package in.kvapps.wirelessstart.ble;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.SignalState;

public class BleScanReceiver extends BroadcastReceiver {

    public static Set<String> targetMacAddresses = new HashSet<>();
    public static ScanEventCallback callback = null;

    public interface ScanEventCallback {
        void onSignalStateChanged(String macAddress, SignalState state, int rssi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        List<ScanResult> results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        if (results != null && !results.isEmpty()) {
            PreferenceManager prefMgr = new PreferenceManager(context);
            int userThreshold = prefMgr.getConnectionThreshold();

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

                        // Evaluate current signal state dynamically based on user's threshold setting
                        SignalState currentState = SignalState.evaluate(rssi, userThreshold);

                        // Always emit the latest RSSI and state so the UI proximity bar updates live
                        if (callback != null) {
                            callback.onSignalStateChanged(foundMac, currentState, rssi);
                        }
                        // Do not break here because multiple target vehicles
                        // could be present in the same batch of scan results.
                    }
                }
            }
        }
    }
}