package in.kvapps.wirelessstart.ble;

import android.content.Context;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.dto.CmdExecResultData;
import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.CommandActionType;
import in.kvapps.wirelessstart.enums.VehicleEventType;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.VehicleUtils;

public class BleCommandManager implements VehicleEventRepository.VehicleEventListener {
    private static final String TAG = "BleCommandManager";
    private static BleCommandManager instance;

    private final Context context;
    private final BleManager bleManager;
    private final VehicleRepository vehicleRepository;

    private String pendingCommand;
    private String pendingMacAddress;
    private long commandStartTime = 0;
    private CommandCallback activeCallback;

    public interface CommandCallback {
        void onCommandFinished(CmdExecResultData cmdExecResultData);
    }

    private BleCommandManager(Context context) {
        this.context = context.getApplicationContext();
        MyApplication app = (MyApplication) this.context;
        this.bleManager = app.getBleManager();
        this.vehicleRepository = app.getVehicleRepository();

        // Listen to vehicle events (e.g., connection success)
        VehicleEventRepository.getInstance().addListener(this);
    }

    public static synchronized BleCommandManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleCommandManager(context);
        }
        return instance;
    }

    /**
     * Unified entry point: Caller passes the vehicle MAC and logical action type.
     */
    public void sendCommand(String macAddress, CommandActionType actionType, boolean tertiaryState, CommandCallback callback, boolean isWatch) {
        if (macAddress == null || macAddress.isEmpty()) {
            if (callback != null) callback.onCommandFinished(new CmdExecResultData(null, Constants.COMMAND_SYSTEM_FAILURE, 0));
            return;
        }

        // 1. Resolve vehicle and settings
        var vehicle = vehicleRepository.getVehicleByMac(macAddress);
        if (vehicle == null) {
            AppLogger.logToDatabaseAndLogcat(context, TAG, "[COMMAND] Vehicle not found for MAC: " + macAddress);
            if (callback != null) callback.onCommandFinished(new CmdExecResultData(null, Constants.COMMAND_SYSTEM_FAILURE, 0));
            return;
        }

        VehicleSettings vehicleSettings = vehicleRepository.getSettingsForVehicle(vehicle.getId());

        // 2. Automatically determine the correct physical command string based on VehicleControlType
        String resolvedCommand = resolveCommandString(vehicleSettings, actionType, tertiaryState);
        if (resolvedCommand == null) {
            AppLogger.logToDatabaseAndLogcat(context, TAG, "[COMMAND] Failed to resolve command string for action type.");
            if (callback != null) callback.onCommandFinished(new CmdExecResultData(null, Constants.COMMAND_SYSTEM_FAILURE, 0));
            return;
        }

        this.pendingMacAddress = macAddress;
        this.pendingCommand = resolvedCommand;
        this.commandStartTime = System.currentTimeMillis();
        this.activeCallback = callback;

        if (bleManager.isConnected(macAddress)) {
            flushAndExecutePendingCommand();
        } else {
            AppLogger.logToDatabaseAndLogcat(context, TAG, "[COMMAND] Device not connected. Initiating connection for: " + resolvedCommand);
            bleManager.connect(macAddress, false);
        }
    }

    // Overload for primary/secondary actions that don't need a boolean toggle state
    public void sendCommand(String macAddress, CommandActionType actionType, CommandCallback callback, boolean isWatch) {
        sendCommand(macAddress, actionType, false, callback, isWatch);
    }

    private String resolveCommandString(VehicleSettings vehicleSettings, CommandActionType actionType, boolean tertiaryState) {
        return switch (actionType) {
            case PRIMARY ->
                // Resolves to START or UNLOCK
                    VehicleUtils.getVehicleActionText(vehicleSettings, true);
            case SECONDARY ->
                // Resolves to STOP or LOCK
                    VehicleUtils.getVehicleActionText(vehicleSettings, false);
            case TERTIARY ->
                // Resolves to PWR:ON / PWR:OFF
                    VehicleUtils.getVehicleTertiaryActionText(tertiaryState);
        };
    }

    @Override
    public void onVehicleEvent(String macAddress, VehicleEventType vehicleEventType, VehicleEventData vehicleEventData) {
        // When connection succeeds, check if we have a pending command waiting for this specific MAC
        if (vehicleEventType == VehicleEventType.READY_FOR_CONTROL) {
            if (pendingCommand != null && pendingMacAddress != null && pendingMacAddress.equalsIgnoreCase(macAddress)) {
                flushAndExecutePendingCommand();
            }
        }
    }

    private void flushAndExecutePendingCommand() {
        if (pendingCommand == null || pendingMacAddress == null) return;

        final String commandToSend = pendingCommand;
        final String mac = pendingMacAddress;

        BleDeviceSession session = bleManager.getSession(mac);
        if (session != null && session.isConnected()) {
            var vehicleSettings = vehicleRepository.getSettingsForVehicle(vehicleRepository.getVehicleByMac(mac).getId());
            String formattedCommand = VehicleUtils.getFormattedCommand(commandToSend, vehicleSettings);

//            AppLogger.logToDatabaseAndLogcat(context, TAG, "[COMMAND] Transmitting command: " + formattedCommand);

            // Use sendTextCommand to safely map string commands into the session
            session.sendTextCommand(formattedCommand,
                    () -> handleResult(formattedCommand, Constants.COMMAND_SUCCESS),
                    () -> handleResult(formattedCommand, Constants.COMMAND_FAILURE)
            );
        } else {
            AppLogger.logToDatabaseAndLogcat(context, TAG, "[COMMAND] Failed to execute: Session is null or disconnected.");
            handleResult(commandToSend, Constants.COMMAND_SYSTEM_FAILURE);
        }
    }

    private void handleResult(String executedCommand, String result) {
        long timeTaken = System.currentTimeMillis() - commandStartTime;
//        AppLogger.logToDatabaseAndLogcat(context, TAG, "[COMMAND RESULT] " + executedCommand + " | " + result + " | Time: " + timeTaken + "ms");

        if (activeCallback != null) {
            CmdExecResultData resultPayload = new CmdExecResultData(executedCommand, result, timeTaken);
            activeCallback.onCommandFinished(resultPayload);
        }

        // Reset state
        pendingCommand = null;
        pendingMacAddress = null;
        commandStartTime = 0;
        activeCallback = null;
    }
}