package in.kvapps.wirelessstart.ble;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
    private ScanCallback scanCallback;
    private boolean isScanning = false;
    private boolean isDutyCycleActive = false; // Tracks if the duty cycle loop should keep running

    private final UUID serviceUuid;
    private final String targetMac;

    // Handlers for managing duty-cycle timing
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private static final long SCAN_DURATION_MS = 10000;  // Scan for 10 seconds
    private static final long PAUSE_DURATION_MS = 20000; // Rest for 20 seconds

    public BleScanManager(Context context, UUID serviceUuid, String targetMac, ScanListener listener) {
        this.context = context;
        this.serviceUuid = serviceUuid;
        this.targetMac = targetMac;
        this.listener = listener;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        } else {
            this.bluetoothLeScanner = null;
        }
    }

    /**
     * Starts the duty-cycling background scan.
     * Scans for 10s, sleeps for 20s, repeating automatically.
     */
    public void startScan() {
        if (bluetoothLeScanner == null) return;

        // Mark that the duty cycle loop is requested to run
        isDutyCycleActive = true;

        // Clear any pending handlers to avoid duplicate overlapping loops
        scanHandler.removeCallbacks(startScanRunnable);
        scanHandler.removeCallbacks(stopScanRunnable);

        // Begin the first scan cycle immediately
        startScanRunnable.run();
    }

    private final Runnable startScanRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isDutyCycleActive || isScanning) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    if (listener != null) listener.onScanLog("Scan Error: Missing BLUETOOTH_SCAN permission.");
                    return;
                }
            }

            if (targetMac == null || targetMac.trim().isEmpty()) {
                if (listener != null) listener.onScanLog("Scan Error: Target MAC is empty.");
                return;
            }

            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(serviceUuid.toString()))
                    .build();
            filters.add(filter);

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (result.getDevice() != null && result.getDevice().getAddress().equalsIgnoreCase(targetMac)) {
                        if (listener != null) listener.onScanLog("Target device spotted in range! Triggering connection...");
                        stopScan(); // Stops scanning and halts duty cycle loop
                        listener.onDeviceFound(targetMac);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    if (listener != null) listener.onScanLog("Scan Failed with error code: " + errorCode);
                    isScanning = false;
                }
            };

            try {
                isScanning = true;
                bluetoothLeScanner.startScan(filters, settings, scanCallback);

                // Schedule stopping this active scan window after 10 seconds
                scanHandler.postDelayed(stopScanRunnable, SCAN_DURATION_MS);

            } catch (SecurityException e) {
                if (listener != null) listener.onScanLog("Security Exception: Failed to start targeted scan.");
                isScanning = false;
            }
        }
    };

    private final Runnable stopScanRunnable = new Runnable() {
        @Override
        public void run() {
            // Stop the underlying Bluetooth scan hardware session
            executeStopScan();

            // If duty cycle is still active (device not found yet), schedule the next scan window after 20 seconds
            if (isDutyCycleActive) {
                scanHandler.postDelayed(startScanRunnable, PAUSE_DURATION_MS);
            }
        }
    };

    private void executeStopScan() {
        if (bluetoothLeScanner != null && scanCallback != null && isScanning) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (SecurityException e) {
                // Safe exception suppression during teardown
            }
        }
        isScanning = false;
        scanCallback = null;
    }

    /**
     * Completely stops the scanning session and kills the duty-cycle loop.
     */
    public void stopScan() {
        isDutyCycleActive = false; // Terminate loop flag
        scanHandler.removeCallbacks(startScanRunnable);
        scanHandler.removeCallbacks(stopScanRunnable);
        executeStopScan();
    }

    public boolean isScanning() {
        return isScanning;
    }
}