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
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;

// Listener to consume messages sent by Wear App
public class WearMessageListenerService extends WearableListenerService {
    private static final String TAG = Constants.WEAR_DATA_LAYER_TAG;
    private BleManager bleManager;
    private VehicleRepository vehicleRepository;
    private PreferenceManager preferenceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // Grab the app-wide singleton BleManager from MyApplication
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

        CommandActionType action;
        if (Constants.START_PATH.equals(path)) {
            action = CommandActionType.PRIMARY; // START or UNLOCK
        } else if (Constants.STOP_PATH.equals(path)) {
            action = CommandActionType.SECONDARY; // STOP or LOCK
        } else {
            return;
        }

//        AppLogger.logToDatabaseAndLogcat(this, TAG, "Watch command received");

        var selectedVehicle = vehicleRepository.getDefaultVehicle();
        if (selectedVehicle != null && selectedVehicle.getMacAddress() != null) {
            BleCommandManager.getInstance(this).sendCommand(
                    selectedVehicle.getMacAddress(),
                    action,
                    resultPayload -> FeedbackUtils.sendCmdResultAckToWatch(this, resultPayload.getResult()),
                    true // flag indicating it came from watch
            );
        } else {
            AppLogger.logToDatabaseAndLogcat(this, TAG, "[BLE BACKGROUND] No target vehicle found for watch command.");
            FeedbackUtils.sendCmdResultAckToWatch(this, Constants.COMMAND_FAILURE);
        }

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