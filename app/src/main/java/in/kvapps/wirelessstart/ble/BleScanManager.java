package in.kvapps.wirelessstart.ble;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleScanManager {

    public interface ScanListener {
        void onDeviceFound(String macAddress);
        void onScanLog(String message);
    }

    private final Context context;
    private final ScanListener listener;
    private final BluetoothLeScanner bluetoothLeScanner;
    private PendingIntent scanPendingIntent;
    private boolean isScanning = false;

    private final UUID serviceUuid;
    private final String targetMac;

    private static final String ACTION_SCAN_RESULT = "in.kvapps.wirelessstart.ACTION_BLE_SCAN_RESULT";
    private boolean isReceiverRegistered = false;

    public BleScanManager(Context context, UUID serviceUuid, String targetMac, ScanListener listener) {
        this.context = context;
        this.serviceUuid = serviceUuid;
        this.targetMac = targetMac;
        this.listener = listener;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothLeScanner = (bluetoothAdapter != null) ? bluetoothAdapter.getBluetoothLeScanner() : null;
    }

    // BroadcastReceiver triggered by the OS kernel when a matching BLE advertisement is caught
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_SCAN_RESULT.equals(intent.getAction())) return;

            List<ScanResult> results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
            if (results != null) {
                for (ScanResult result : results) {
                    if (result.getDevice() != null) {
                        String foundMac = result.getDevice().getAddress();
                        if (foundMac.equalsIgnoreCase(targetMac)) {
                            if (listener != null) {
                                listener.onScanLog("Target device spotted in range! Triggering connection...");
                                stopScan();
                                listener.onDeviceFound(targetMac);
                                break;
                            }
                        }
                    }
                }
            }
        }
    };

    public void startScan() {
        if (bluetoothLeScanner == null || isScanning) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                if (listener != null) listener.onScanLog("Scan Error: Missing BLUETOOTH_SCAN permission.");
                return;
            }
        }

        // 1. Register the local broadcast receiver safely
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_SCAN_RESULT);
            androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    scanReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            );
            isReceiverRegistered = true;
        }

        // 2. Build explicit PendingIntent without a redundant custom action string
        Intent intent = new Intent(context, BleScanReceiver.class);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        scanPendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags);

        // 3. Configure filtering for your specific ESP32 Service UUID
        List<ScanFilter> filters = new ArrayList<>();
        if (targetMac != null && !targetMac.trim().isEmpty()) {
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(serviceUuid.toString()))
                    .build();
            filters.add(filter);
        }

        // 4. Low latency setup for immediate recognition
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            int result = bluetoothLeScanner.startScan(filters, settings, scanPendingIntent);
            if (result == 0) {
                isScanning = true;
                if (listener != null) listener.onScanLog("Scanning active...");
            } else {
                if (listener != null) listener.onScanLog("Failed to start Target Device scan. Code: " + result);
            }
        } catch (SecurityException e) {
            if (listener != null) listener.onScanLog("Security Exception starting PendingIntent scan.");
        }
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && scanPendingIntent != null) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(scanPendingIntent);
                }
            } catch (Exception ignored) {
            }
        }

        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            isReceiverRegistered = false;
        }

        isScanning = false;
        scanPendingIntent = null;
//        if (listener != null) listener.onScanLog("Background scan terminated.");
    }

    public boolean isScanning() {
        return isScanning;
    }
}