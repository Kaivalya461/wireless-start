package in.kvapps.wirelessstart;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;

// Listener to consume messages sent by Wear App
public class WearMessageListenerService extends WearableListenerService implements BleManager.BleListener {
    private static final String TAG = Constants.WEAR_DATA_LAYER_TAG;
    private BleManager bleManager;
    private String pendingCommandToSend; // Store the intended command
    private long commandStartTime = 0;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "Received watch route path: " + path);

        String action = null;
        if (Constants.START_PATH.equals(path)) {
            action = "START";
        } else if (Constants.STOP_PATH.equals(path)) {
            action = "STOP";
        }

        if (action != null) {
            PreferenceManager prefManager = new PreferenceManager(this);
            String formattedCommand = prefManager.getFormattedCommand(action);

            Intent broadCastIntent = new Intent("DIO_HARDWARE_TRIGGER");
            broadCastIntent.putExtra("COMMAND", formattedCommand);

            sendOrderedBroadcast(broadCastIntent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int resultCode = getResultCode();
                    boolean wasHandledByActivity = (resultCode == Activity.RESULT_OK);

                    if (wasHandledByActivity) {
                        AppLogger.logToDatabaseAndLogcat(context, TAG, "Command handled directly by open MainActivity UI.");
                    } else {
                        AppLogger.logToDatabaseAndLogcat(context, TAG, "MainActivity is closed. Executing background BLE write: " + formattedCommand);
                        executeBackgroundBleCommand(formattedCommand);
                    }
                }
            }, null, Activity.RESULT_CANCELED, null, null);
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
//                        AppLogger.logToDatabaseAndLogcat(this, TAG, "Received Schedule Epoch from Wear: " + targetEpoch);

                        // Broadcast to foreground MainActivity if open, else execute background write
                        Intent broadcastIntent = new Intent("DIO_SCHEDULE_TRIGGER");
                        broadcastIntent.putExtra("EPOCH", targetEpoch);

                        sendOrderedBroadcast(broadcastIntent, null, new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                boolean wasHandled = (getResultCode() == Activity.RESULT_OK);
                                if (!wasHandled) {
                                    // MainActivity is closed; add an error entry in System Activity Monitor
                                    onLog("Error - Failed to configured Engine-Run Schedule, MainActivity was closed. Try Again!");
                                }
                            }
                        }, null, Activity.RESULT_CANCELED, null, null);
                    }
                }
            }
        }
    }

    private void executeBackgroundBleCommand(String command) {
        this.pendingCommandToSend = command;
        this.commandStartTime = System.currentTimeMillis();

        // Clean up any stale manager instance first
        if (bleManager != null) {
            bleManager.release();
            bleManager = null;
        }

        // Pass application context instead of service context if possible
        // to decouple it from short-lived service states
        bleManager = new BleManager(getApplicationContext(), this);

        bleManager.connect(false);
    }

    @Override
    public void onLog(String message) {
        // Shared logging handles both Logcat and background DB persistence
        AppLogger.logToDatabaseAndLogcat(this, TAG, "[BLE BACKGROUND] " + message);
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected, String statusText) {
        Log.d(TAG, "[BLE STATE] " + statusText);
        if (!isConnected) {
            // Clean up if connection drops or fails
            cleanup();
        }
    }

    @Override
    public void onServicesReady() {
        if (pendingCommandToSend != null) {
            final String executedCommand = pendingCommandToSend; // capture for lambda scope

            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (bleManager != null && pendingCommandToSend != null) {
                    onLog("Transmitting pending action command: " + executedCommand);

                    bleManager.sendBleCommand(
                            executedCommand,
                            () -> handleCommandResult(executedCommand, Constants.COMMAND_SUCCESS),
                            () -> handleCommandResult(executedCommand, Constants.COMMAND_FAILURE)
                    );
                    pendingCommandToSend = null;
                }
            }, 50);
        }
    }

    private void handleCommandResult(String executedCommand, String resultPayload) {
        long timeTaken = System.currentTimeMillis() - commandStartTime;

        onLog("[WATCH RX] Command: " + executedCommand + " | " + resultPayload + " | TimeTaken: " + timeTaken + "ms");

        FeedbackUtils.sendCmdResultAckToWatch(this, resultPayload);

        // Reset timer
        commandStartTime = 0;

        cleanup();
    }

    private void cleanup() {
        if (bleManager != null) {
            bleManager.release();
            bleManager = null;
        }
        pendingCommandToSend = null;
    }

    @Override
    public void onDataReceived(byte[] rawData) {
        // Leave this empty as wearable listener doesn't need
        // to actively process or update the incoming message from BLE GATT
    }
}