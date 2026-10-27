package in.kvapps.wirelessstart;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import in.kvapps.wirelessstart.ble.BleDeviceSession;
import in.kvapps.wirelessstart.ble.BleForegroundService;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.VehicleUtils;

// Listener to consume messages sent by Wear App
public class WearMessageListenerService extends WearableListenerService implements ConnectionStateRepository.OnConnectionStateChangedListener {
    private static final String TAG = Constants.WEAR_DATA_LAYER_TAG;
    private BleManager bleManager;
    private VehicleRepository vehicleRepository;
    private PreferenceManager preferenceManager;
    private String pendingCommandToSend; // Store the intended command
    private long commandStartTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        // Grab the app-wide singleton BleManager from MyApplication
        MyApplication app = (MyApplication) getApplication();
        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();
        vehicleRepository = app.getVehicleRepository();

        // Register to the ConnectionStateRepository SSOT
        ConnectionStateRepository.getInstance().addListener(this);
    }

    @Override
    public void onDestroy() {
        // Unregister from the repository to prevent memory leaks
        ConnectionStateRepository.getInstance().removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "Received watch route path: " + path);

        String action;
        if (Constants.START_PATH.equals(path)) {
            action = "START";
        } else if (Constants.STOP_PATH.equals(path)) {
            action = "STOP";
        } else {
            action = null;
        }

        if (action != null) {
            Intent broadCastIntent = new Intent("DIO_HARDWARE_TRIGGER");
            broadCastIntent.putExtra("COMMAND", action);

            sendOrderedBroadcast(broadCastIntent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int resultCode = getResultCode();
                    boolean wasHandledByActivity = (resultCode == Activity.RESULT_OK);

                    if (wasHandledByActivity) {
                        AppLogger.logToDatabaseAndLogcat(context, TAG, "Command handled directly by open MainActivity UI.");
                    } else {
                        AppLogger.logToDatabaseAndLogcat(context, TAG, "MainActivity is closed. Executing background BLE write.");
                        executeBackgroundBleCommand(action);
                    }
                }
            }, null, Activity.RESULT_CANCELED, null, null);

            PreferenceManager prefManager = new PreferenceManager(this);
            if (prefManager.isForegroundServiceEnabled()) {
                Intent serviceIntent = new Intent(this, BleForegroundService.class);
                startForegroundService(serviceIntent);
            }
        }
    }

    // --- Handle Data Layer Synchronization for Engine-Run Schedules ---
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                if (Constants.ENGINE_SCHEDULE_PATH.equals(item.getUri().getPath())) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    long targetEpoch = dataMap.getLong(Constants.KEY_SCHEDULE_EPOCH, -1);

                    if (targetEpoch >= 0) {
                        AppLogger.logToDatabaseAndLogcat(this, TAG, "Received Schedule Epoch from Wear: " + targetEpoch);
                        executeScheduledEngineRunEvent(targetEpoch);
                    }
                }
            }
        }
    }

    private void executeBackgroundBleCommand(String command) {
        this.pendingCommandToSend = command;
        this.commandStartTime = System.currentTimeMillis();

        if (bleManager != null) {
            var selectedVehicle = preferenceManager.getSelectedVehicle();
            if (selectedVehicle == null || selectedVehicle.getMacAddress() == null) {
                AppLogger.logToDatabaseAndLogcat(this, TAG, "[BLE BACKGROUND] No target vehicle found for background command.");
                handleCommandResult(command, Constants.COMMAND_FAILURE);
                return;
            }

            if (bleManager.isConnected(selectedVehicle.getMacAddress())) {
                // Already connected (e.g., foreground service kept it alive)
                sendPendingCommandNow();
            } else {
                // Not connected, initiate connection using the shared manager
                bleManager.connect(selectedVehicle.getMacAddress(), false);
            }
        }
    }

    // --- ConnectionStateRepository Observer Callback (SSOT) ---
    @Override
    public void onStateChanged(String macAddress, ConnectionStateRepository.DeviceConnectionState newState) {
        var selectedVehicle = preferenceManager.getSelectedVehicle();
        if (selectedVehicle != null && macAddress.equalsIgnoreCase(selectedVehicle.getMacAddress())) {
            Log.d(TAG, "[BLE STATE] " + newState.getStatusText());

            if (newState.isConnected()) {
                // If we were waiting to send a command in the background, trigger it now upon successful connection and service readiness
                if (pendingCommandToSend != null) {
                    sendPendingCommandNow();
                }
            } else {
                if (pendingCommandToSend != null) {
                    AppLogger.logToDatabaseAndLogcat(this, TAG, "[BLE BACKGROUND] Connection lost while trying to execute background command.");
                    handleCommandResult(pendingCommandToSend, Constants.COMMAND_FAILURE);
                }
            }
        }
    }

    private void handleCommandResult(String executedCommand, String resultPayload) {
        long timeTaken = System.currentTimeMillis() - commandStartTime;

        AppLogger.logToDatabaseAndLogcat(this, TAG, "[WATCH RX] Command: " + executedCommand + " | " + resultPayload + " | TimeTaken: " + timeTaken + "ms");

        FeedbackUtils.sendCmdResultAckToWatch(this, resultPayload);

        // Reset timer
        commandStartTime = 0;
        pendingCommandToSend = null;
    }

    private void sendPendingCommandNow() {
        var selectedVehicle = preferenceManager.getSelectedVehicle();
        if (pendingCommandToSend != null && bleManager != null && selectedVehicle != null && bleManager.isConnected(selectedVehicle.getMacAddress())) {
            final String executedCommand = pendingCommandToSend;
            var vehicleSettings = vehicleRepository.getSettingsForVehicle(selectedVehicle.getId());
            String formattedCommand = VehicleUtils.getFormattedCommand(executedCommand, vehicleSettings);
            AppLogger.logToDatabaseAndLogcat(this, TAG, "[BLE BACKGROUND] Transmitting background action command: " + formattedCommand);

            BleDeviceSession session = bleManager.getSession(selectedVehicle.getMacAddress());
            if (session != null && session.isConnected()) {
                session.sendBleCommand(
                        formattedCommand,
                        () -> handleCommandResult(formattedCommand, Constants.COMMAND_SUCCESS),
                        () -> handleCommandResult(formattedCommand, Constants.COMMAND_FAILURE)
                );
            } else {
                handleCommandResult(formattedCommand, Constants.COMMAND_FAILURE);
            }
            pendingCommandToSend = null;
        }
    }

    private void executeScheduledEngineRunEvent(long targetEpoch) {
        var selectedVehicle = preferenceManager.getSelectedVehicle();
        if (bleManager != null && selectedVehicle != null) {
            BleDeviceSession session = bleManager.getSession(selectedVehicle.getMacAddress());
            if (session != null && session.isConnected()) {
                session.sendScheduledEpoch(targetEpoch);

                // send a request for the schedule to confirm that target has received the data
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (session.isConnected()) session.requestScheduleFromEsp32();
                }, 500);
            }
        }
    }
}