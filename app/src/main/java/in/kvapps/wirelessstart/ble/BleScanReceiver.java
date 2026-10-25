package in.kvapps.wirelessstart.ble;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import in.kvapps.wirelessstart.data.PreferenceManager;

public class BleScanReceiver extends BroadcastReceiver {

    public static Set<String> targetMacAddresses = new HashSet<>();
    public static ScanEventCallback callback = null;
    private static final Map<String, SignalState> lastSignalStates = new HashMap<>();

    public enum SignalState {
        SUPER_STRONG,      // >= -50 dBm (Triggers Connect)
        MEDIUM_HIGH,       // -51 dBm to -80 dBm (Triggers Connect)
        LOW_WAITING,       // < -80 dBm & Avoid Poor Connection = TRUE (Waiting)
        LOW_TRIGGER        // < -80 dBm & Avoid Poor Connection = FALSE (Triggers Connect)
    }

    public interface ScanEventCallback {
        void onSignalStateChanged(String macAddress, SignalState state, int rssi);
    }

    public static void resetState() {
        lastSignalStates.clear();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        List<ScanResult> results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        if (results != null && !results.isEmpty()) {
            PreferenceManager prefMgr = new PreferenceManager(context);
            boolean avoidPoorConnection = prefMgr.isAvoidPoorConnectionEnabled();

            for (ScanResult result : results) {
                if (result.getDevice() != null) {
                    String foundMac = result.getDevice().getAddress();

                    // Check if the found device matches any of our target vehicles (case-insensitive)
                    boolean isTarget = false;
                    if (targetMacAddresses != null) {
                        for (String targetMac : targetMacAddresses) {
                            if (targetMac != null && targetMac.equalsIgnoreCase(foundMac)) {
                                isTarget = true;
                                break;
                            }
                        }
                    }

                    if (isTarget) {
                        int rssi = result.getRssi();
                        prefMgr.saveLastSeenTimestamp(foundMac, System.currentTimeMillis());

                        SignalState currentState;
                        if (rssi >= -50) {
                            currentState = SignalState.SUPER_STRONG;
                        } else if (rssi >= -80) {
                            currentState = SignalState.MEDIUM_HIGH;
                        } else {
                            if (avoidPoorConnection) {
                                currentState = SignalState.LOW_WAITING;
                            } else {
                                currentState = SignalState.LOW_TRIGGER;
                            }
                        }

                        // Check state change specifically for this vehicle MAC
                        SignalState lastState = lastSignalStates.get(foundMac);
                        if (currentState != lastState) {
                            lastSignalStates.put(foundMac, currentState);

                            if (callback != null) {
                                callback.onSignalStateChanged(foundMac, currentState, rssi);
                            }
                        }
                        // Do not break here because multiple target vehicles
                        // could be present in the same batch of scan results.
                    }
                }
            }
        }
    }
}