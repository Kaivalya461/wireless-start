package in.kvapps.wirelessstart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;

public class WearMessageListenerService extends WearableListenerService implements BleManager.BleListener {
    private static final String TAG = "DioWearListener";
    private static final String START_PATH = "/dio/engine_start";
    private static final String STOP_PATH = "/dio/engine_stop";

    private BleManager bleManager;
    private String pendingCommandToSend; // Store the intended command

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
                        Log.d(TAG, "Command handled directly by open MainActivity UI.");
                    } else {
                        Log.d(TAG, "MainActivity is closed. Executing background BLE write: " + formattedCommand);
                        executeBackgroundBleCommand(formattedCommand);
                    }
                }
            }, null, 0, null, null);
        }
    }

    private void executeBackgroundBleCommand(String command) {
        this.pendingCommandToSend = command; // Save the action command (START/STOP)
        bleManager = new BleManager(this, this);
        bleManager.connect();
    }

    @Override
    public void onLog(String message) {
        Log.d(TAG, "[BLE LOG] " + message);
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected, String statusText) {
        Log.d(TAG, "[BLE STATE] " + statusText);
    }

    @Override
    public void onServicesReady() {
        // Services discovered AND TIME command sent!
        // Brief 250ms delay to let the TIME packet clear the pipeline
        if (pendingCommandToSend != null) {
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (bleManager != null && pendingCommandToSend != null) {
                    Log.d(TAG, "Transmitting pending action command: " + pendingCommandToSend);
                    bleManager.sendBleCommand(pendingCommandToSend);
                    pendingCommandToSend = null; // Clear queue
                }
            }, 250);
        }
    }
}