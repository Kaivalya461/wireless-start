package in.kvapps.wirelessstart;

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

import in.kvapps.wirelessstart.ble.BleCommandManager;
import in.kvapps.wirelessstart.ble.BleDeviceSession;
import in.kvapps.wirelessstart.ble.BleForegroundService;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.CommandActionType;
import in.kvapps.wirelessstart.model.CommandLogEntity;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.repository.CommandLogsRepository;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.VehicleUtils;

// Listener to consume messages sent by Wear App
public class WearMessageListenerService extends WearableListenerService {
    private static final String TAG = Constants.WEAR_DATA_LAYER_TAG;
    private BleManager bleManager;
    private VehicleRepository vehicleRepository;
    private PreferenceManager preferenceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        MyApplication app = (MyApplication) getApplication();
        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();
        vehicleRepository = app.getVehicleRepository();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "Received watch route path: " + path);

        var selectedVehicle = vehicleRepository.getDefaultVehicle();
        if (selectedVehicle == null || selectedVehicle.getMacAddress() == null) {
            AppLogger.logToDatabaseAndLogcat(this, TAG, "[BLE BACKGROUND] No target vehicle found for watch command.");
            FeedbackUtils.sendCmdResultAckToWatch(this, Constants.COMMAND_SYSTEM_FAILURE);
            return;
        }

        String mac = selectedVehicle.getMacAddress();
        VehicleSettings settings = vehicleRepository.getSettingsForVehicle(selectedVehicle.getId());
        CommandActionType action;

        // Determine action based on which path was triggered from the watch
        if (Constants.START_PATH.equals(path)) {
            // Explicit START button pressed
            action = CommandActionType.PRIMARY;
            AppLogger.logToDatabaseAndLogcat(this, TAG, "[WATCH] Explicit START path received.");

        } else if (Constants.STOP_PATH.equals(path)) {
            // Explicit STOP button pressed
            action = CommandActionType.SECONDARY;
            AppLogger.logToDatabaseAndLogcat(this, TAG, "[WATCH] Explicit STOP path received.");

        } else if (Constants.FLIP_FLOP_PATH.equals(path)) {
            // New Flip-Flop Shortcut triggered: Inspect last successful command log
            String primaryActionText = VehicleUtils.getVehicleActionText(settings, true);
            CommandLogEntity lastLog = CommandLogsRepository.getInstance(this).getLatestSuccessfulLog(mac);

            if (lastLog != null && lastLog.getCommand() != null &&
                    lastLog.getCommand().toUpperCase().startsWith(primaryActionText.toUpperCase())) {
                // Last successful was Primary (START / UNLOCK) -> Now trigger Secondary (STOP / LOCK)
                action = CommandActionType.SECONDARY;
            } else {
                // Last successful was Secondary or no logs exist -> Trigger Primary (START / UNLOCK)
                action = CommandActionType.PRIMARY;
            }
//            AppLogger.logToDatabaseAndLogcat(this, TAG, "[WATCH] Flip-flop path received. Selected action: " + action);

        } else {
            // Unrecognized path
            return;
        }

        // Execute command
        BleCommandManager.getInstance(this).sendCommand(
                mac,
                action,
                resultPayload -> FeedbackUtils.sendCmdResultAckToWatch(this, resultPayload.getResult()),
                true // came from watch
        );

        if (preferenceManager.isForegroundServiceEnabled()) {
            Intent serviceIntent = new Intent(this, BleForegroundService.class);
            startForegroundService(serviceIntent);
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

    private void executeScheduledEngineRunEvent(long targetEpoch) {
        var selectedVehicle = vehicleRepository.getDefaultVehicle();
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