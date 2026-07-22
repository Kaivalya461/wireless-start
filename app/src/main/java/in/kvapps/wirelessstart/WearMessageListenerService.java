package in.kvapps.wirelessstart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

            // Send an Ordered Broadcast so we can check if MainActivity is currently alive to handle it
            sendOrderedBroadcast(broadCastIntent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // getResultCode() == -1 means a active BroadcastReceiver consumed the intent!
                    boolean wasHandledByActivity = (getResultCode() == -1);

                    if (wasHandledByActivity) {
                        Log.d(TAG, "Command handled directly by open MainActivity UI.");
                    } else {
                        // MainActivity is closed/dead -> Fallback to background BLE execution
                        Log.d(TAG, "MainActivity is closed. Executing background BLE write: " + formattedCommand);
                        executeBackgroundBleCommand(formattedCommand);
                    }
                }
            }, null, 0, null, null);
        }
    }

    private void executeBackgroundBleCommand(String command) {
        bleManager = new BleManager(this, this);
        bleManager.connect();

        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (bleManager != null) {
                bleManager.sendBleCommand(command);
            }
        }, 1200);
    }

    @Override public void onLog(String message) { Log.d(TAG, "[BLE LOG] " + message); }
    @Override public void onConnectionStateChanged(boolean isConnected, String statusText) { Log.d(TAG, "[BLE STATE] " + statusText); }
}