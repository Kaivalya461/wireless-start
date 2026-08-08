package in.kvapps.wirelessstart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
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
    private static final String START_PATH = Constants.START_PATH;
    private static final String STOP_PATH = Constants.STOP_PATH;

    private BleManager bleManager;
    private String pendingCommandToSend; // Store the intended command
    private long commandStartTime = 0;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "Received watch route path: " + path);

        String action = null;
        if (START_PATH.equals(path)) {
            action = "START";
        } else if (STOP_PATH.equals(path)) {
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
                    boolean wasHandledByActivity = (getResultCode() == -1);

                    if (wasHandledByActivity) {
                        AppLogger.logToDatabaseAndLogcat(context, TAG, "Command handled directly by open MainActivity UI.");
                    } else {
                        AppLogger.logToDatabaseAndLogcat(context, TAG, "MainActivity is closed. Executing background BLE write: " + formattedCommand);
                        executeBackgroundBleCommand(formattedCommand);
                    }
                }
            }, null, 0, null, null);
        }
    }

    private void executeBackgroundBleCommand(String command) {
        this.pendingCommandToSend = command; // Save the action command (START/STOP)

        // Record start time when background execution begins
        this.commandStartTime = System.currentTimeMillis();

        // Clean up any stale manager instance first
        if (bleManager != null) {
            bleManager.release();
        }

        bleManager = new BleManager(this, this);

        PreferenceManager preferenceManager = new PreferenceManager(this);
        bleManager.setTargetHwName(preferenceManager.getTargetHwName());
        bleManager.setMacAdd(preferenceManager.getTargetMacAddress());

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
                            () -> handleCommandResult(executedCommand, Constants.START_SUCCESS),
                            () -> handleCommandResult(executedCommand, Constants.START_FAILURE)
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

    @Override
    public void onVoltageReceived(float voltage) {
        // Leave this empty if your wearable listener doesn't need
        // to actively process or update the voltage UI on its own.
    }

    private void cleanup() {
        if (bleManager != null) {
            bleManager.release();
            bleManager = null;
        }
        pendingCommandToSend = null;
    }
}