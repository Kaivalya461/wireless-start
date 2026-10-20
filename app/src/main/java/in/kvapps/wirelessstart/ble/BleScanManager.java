package in.kvapps.wirelessstart.ble;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleScanManager implements BleScanReceiver.ScanEventCallback {

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
    private String targetMac;

    public BleScanManager(Context context, UUID serviceUuid, String targetMac, ScanListener listener) {
        this.context = context;
        this.serviceUuid = serviceUuid;
        this.targetMac = targetMac;
        this.listener = listener;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothLeScanner = (bluetoothAdapter != null) ? bluetoothAdapter.getBluetoothLeScanner() : null;
    }

    public void updateTargetMac(String newMac) {
        this.targetMac = newMac;
    }

    @Override
    public void onSignalStateChanged(String macAddress, BleScanReceiver.SignalState state, int rssi) {
        switch (state) {
            case SUPER_STRONG:
                stopScan();
                if (listener != null) {
                    listener.onScanLog("🔥 SuperStrong signal! (" + rssi + " dBm)");
                    listener.onDeviceFound(targetMac);
                }
                break;

            case MEDIUM_HIGH:
                stopScan();
                if (listener != null) {
                    listener.onScanLog("⚡ Medium-High signal (" + rssi + " dBm)");
                    listener.onDeviceFound(targetMac);
                }
                break;

            case LOW_TRIGGER:
                stopScan();
                if (listener != null) {
                    listener.onScanLog("⚠️ Low signal (" + rssi + " dBm), but 'Avoid Poor Connection' setting is disabled. Connecting anyway...");
                    listener.onDeviceFound(targetMac);
                }
                break;

            case LOW_WAITING:
                if (listener != null) {
                    listener.onScanLog("⏳ Low signal (" + rssi + " dBm). Waiting for signal to improve...");
                }
                break;
        }
    }

    public void startScan() {
        if (bluetoothLeScanner == null || isScanning) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                if (listener != null) listener.onScanLog("Scan Error: Missing BLUETOOTH_SCAN permission.");
                return;
            }
        }

        // Reset the receiver's state memory when starting a brand new scan
        BleScanReceiver.resetState();
        BleScanReceiver.targetMacAddress = targetMac;
        BleScanReceiver.callback = this;

        // Use a distinct request code (e.g., 1001) to prevent PendingIntent recycling issues over long spans
        Intent intent = new Intent(context, BleScanReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        scanPendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags);

        // Configure filtering for your specific ESP32 Service UUID
        List<ScanFilter> filters = new ArrayList<>();
        if (targetMac != null && !targetMac.trim().isEmpty() && serviceUuid != null) {
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(serviceUuid.toString()))
                    .build();
            filters.add(filter);
        }

        // IMPORTANT FOR 24/7 BACKGROUND:
        // SCAN_MODE_LOW_LATENCY is too aggressive and often throttled or killed by Android OS after long periods.
        // Switch to SCAN_MODE_BALANCED or SCAN_MODE_LOW_POWER for long-running background durability.
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0) // Deliver results immediately
                .build();

        try {
            int result = bluetoothLeScanner.startScan(filters, settings, scanPendingIntent);
            if (result == 0) {
                isScanning = true;
                if (listener != null) listener.onScanLog("Scanning active (Balanced Mode)...");
            } else {
                if (listener != null) listener.onScanLog("Failed to start scan. Code: " + result);
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

        isScanning = false;
        scanPendingIntent = null;
        BleScanReceiver.callback = null;
        BleScanReceiver.resetState();
    }

    public boolean isScanning() {
        return isScanning;
    }
}