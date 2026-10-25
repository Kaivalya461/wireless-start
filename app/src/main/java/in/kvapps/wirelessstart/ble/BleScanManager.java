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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BleScanManager implements BleScanReceiver.ScanEventCallback {

    public interface ScanListener {
        // Updated to pass back the specific MAC address found
        void onDeviceFound(String macAddress);
        void onScanLog(String message);
    }

    private final Context context;
    private final ScanListener listener;
    private final BluetoothLeScanner bluetoothLeScanner;
    private PendingIntent scanPendingIntent;
    private boolean isScanning = false;

    private final UUID serviceUuid;
    private final Set<String> targetMacs = new HashSet<>();

    private static long lastScanStartTimestamp = 0L;

    public BleScanManager(Context context, UUID serviceUuid, List<String> initialTargetMacs, ScanListener listener) {
        this.context = context;
        this.serviceUuid = serviceUuid;
        if (initialTargetMacs != null) {
            for (String mac : initialTargetMacs) {
                if (mac != null && !mac.trim().isEmpty()) {
                    this.targetMacs.add(mac.toUpperCase().trim());
                }
            }
        }
        this.listener = listener;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothLeScanner = (bluetoothAdapter != null) ? bluetoothAdapter.getBluetoothLeScanner() : null;
    }

    /**
     * Add or update the list of target vehicles to scan for simultaneously.
     */
    public void updateTargetMacs(List<String> newMacs) {
        targetMacs.clear();
        if (newMacs != null) {
            for (String mac : newMacs) {
                if (mac != null && !mac.trim().isEmpty()) {
                    targetMacs.add(mac.toUpperCase().trim());
                }
            }
        }
        // If already scanning, restart the scan dynamically to apply the new filters
        if (isScanning) {
            stopScan();
            startScan();
        }
    }

    @Override
    public void onSignalStateChanged(String macAddress, BleScanReceiver.SignalState state, int rssi) {
        // Ensure the detected device belongs to our active tracking set
        if (macAddress == null || !targetMacs.contains(macAddress.toUpperCase().trim())) {
            return;
        }

        switch (state) {
            case SUPER_STRONG:
                stopScan();
                if (listener != null) {
                    listener.onScanLog("🔥 SuperStrong signal for " + macAddress + "! (" + rssi + " dBm)");
                    listener.onDeviceFound(macAddress);
                }
                break;

            case MEDIUM_HIGH:
                stopScan();
                if (listener != null) {
                    listener.onScanLog("⚡ Medium-High signal for " + macAddress + " (" + rssi + " dBm)");
                    listener.onDeviceFound(macAddress);
                }
                break;

            case LOW_TRIGGER:
                stopScan();
                if (listener != null) {
                    listener.onScanLog("⚠️ Low signal for " + macAddress + " (" + rssi + " dBm). Connecting anyway...");
                    listener.onDeviceFound(macAddress);
                }
                break;

            case LOW_WAITING:
                if (listener != null) {
                    listener.onScanLog("⏳ Low signal for " + macAddress + " (" + rssi + " dBm). Waiting...");
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

        lastScanStartTimestamp = System.currentTimeMillis();

        // Reset the receiver's state memory when starting a brand new scan
        BleScanReceiver.resetState();
        BleScanReceiver.targetMacAddresses = targetMacs;
        BleScanReceiver.callback = this;

        // Use a distinct request code (e.g., 1001) to prevent PendingIntent recycling issues over long spans
        Intent intent = new Intent(context, BleScanReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        scanPendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags);

        // Build ScanFilters: Android BLE filtering supports matching by MAC address or Service UUID.
        // To scan multiple specific devices, add a filter for each MAC address combined with your Service UUID.
        List<ScanFilter> filters = new ArrayList<>();
        if (!targetMacs.isEmpty()) {
            for (String mac : targetMacs) {
                ScanFilter.Builder builder = new ScanFilter.Builder().setDeviceAddress(mac);
                if (serviceUuid != null) {
                    builder.setServiceUuid(ParcelUuid.fromString(serviceUuid.toString()));
                }
                filters.add(builder.build());
            }
        } else if (serviceUuid != null) {
            // Fallback if no specific MACs are provided yet, match by Service UUID globally
            filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(serviceUuid.toString())).build());
        }

        // Reverted back to Low latency setup for immediate recognition
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0) // Deliver results immediately
                .build();

        try {
            int result = bluetoothLeScanner.startScan(filters, settings, scanPendingIntent);
            if (result == 0) {
                isScanning = true;
                if (listener != null) listener.onScanLog("Scanning active for " + targetMacs.size() + " vehicles...");
            } else {
                if (listener != null) listener.onScanLog("Failed to start multi-scan. Code: " + result);
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

    public static long getLastScanStartTimestamp() {
        return lastScanStartTimestamp;
    }
}