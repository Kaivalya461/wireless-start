package in.kvapps.wirelessstart.ble;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import in.kvapps.wirelessstart.data.PreferenceManager;

public class BleScanReceiver extends BroadcastReceiver {

    public static String targetMacAddress = null;
    public static ScanEventCallback callback = null;

    private static SignalState lastSignalState = null;

    public enum SignalState {
        SUPER_STRONG,      // >= -50 dBm (Triggers Connect)
        MEDIUM_HIGH,       // -51 dBm to -75 dBm (Triggers Connect)
        LOW_WAITING,       // < -75 dBm & Avoid Poor Connection = TRUE (Waiting)
        LOW_TRIGGER        // < -75 dBm & Avoid Poor Connection = FALSE (Triggers Connect)
    }

    public interface ScanEventCallback {
        void onSignalStateChanged(String macAddress, SignalState state, int rssi);
    }

    public static void resetState() {
        lastSignalState = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        List<ScanResult> results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        if (results != null && !results.isEmpty()) {
            for (ScanResult result : results) {
                if (result.getDevice() != null) {
                    String foundMac = result.getDevice().getAddress();
                    int rssi = result.getRssi();

                    if (foundMac.equalsIgnoreCase(targetMacAddress)) {
                        SignalState currentState;

                        if (rssi >= -50) {
                            currentState = SignalState.SUPER_STRONG;
                        } else if (rssi >= -75) {
                            currentState = SignalState.MEDIUM_HIGH;
                        } else {
                            // RSSI is < -75 (Low)
                            // Initialize PreferenceManager using the broadcast context
                            PreferenceManager preferenceManager = new PreferenceManager(context);
                            boolean avoidPoorConnection = preferenceManager.isAvoidPoorConnectionEnabled();
                            if (avoidPoorConnection) {
                                currentState = SignalState.LOW_WAITING;
                            } else {
                                currentState = SignalState.LOW_TRIGGER;
                            }
                        }

                        // Trigger callback only if state changes
                        if (currentState != lastSignalState) {
                            lastSignalState = currentState;
//                            Log.i("BleScanReceiver", "State changed to: " + currentState + " (RSSI: " + rssi + ")");

                            if (callback != null) {
                                callback.onSignalStateChanged(targetMacAddress, currentState, rssi);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }
}