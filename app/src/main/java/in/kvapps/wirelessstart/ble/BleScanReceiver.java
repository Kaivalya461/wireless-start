package in.kvapps.wirelessstart.ble;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

public class BleScanReceiver extends BroadcastReceiver {

    public static String targetMacAddress = null;
    public static ScanEventCallback callback = null;

    public interface ScanEventCallback {
        void onDeviceMatched(String macAddress);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        List<ScanResult> results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        if (results != null && !results.isEmpty()) {
            for (ScanResult result : results) {
                if (result.getDevice() != null) {
                    String foundMac = result.getDevice().getAddress();
//                    Log.i("BleScanReceiver", "foundMac -> " + foundMac);
                    if (foundMac.equalsIgnoreCase(targetMacAddress)) {
                        if (callback != null) {
                            callback.onDeviceMatched(targetMacAddress);
                        }
                        break;
                    }
                }
            }
        }
    }
}