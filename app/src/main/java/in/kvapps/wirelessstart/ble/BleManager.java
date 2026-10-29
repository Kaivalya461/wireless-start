package in.kvapps.wirelessstart.ble;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.util.AppLogger;

public class BleManager {

    public interface BleListener {
        void onDataReceived(String macAddress, byte[] rawData);
    }
    private static final String TAG = "BleManager";

    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    // Standard BLE Client Characteristic Configuration Descriptor (CCCD) UUID
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private BleListener listener;
    private final BluetoothAdapter bluetoothAdapter;
    private final PreferenceManager preferenceManager;
    private final VehicleRepository vehicleRepository;
    private final BleScanManager bleScanManager;
    private boolean isReceiverRegistered = false;

    // Track multiple active device sessions simultaneously keyed by MAC Address
    private final Map<String, BleDeviceSession> activeSessions = new ConcurrentHashMap<>();

    // Track MAC addresses currently undergoing connection to prevent duplicate connectGatt() calls
    private final Set<String> connectingMacs = ConcurrentHashMap.newKeySet();

    public BleManager(Context context, BleListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.preferenceManager = new PreferenceManager(this.context);
        MyApplication app = (MyApplication) context.getApplicationContext();
        vehicleRepository = app.getVehicleRepository();

        this.bleScanManager = new BleScanManager(
                this.context,
                SERVICE_UUID,
                new BleScanManager.ScanListener() {
                    @Override
                    public void onDeviceFound(String macAddress) {
                        // Connect instantly when a target device is found via scan
                        connect(macAddress, false);
                    }

                    @Override
                    public void onScanLog(String message) {
                        logMessage(message);
                    }
                }
        );

        registerBluetoothStateReceiver();
    }

    private void logMessage(String message) {
        AppLogger.logToDatabaseAndLogcat(context, TAG, message);
    }

    public void setListener(BleListener listener) {
        this.listener = listener;
    }

    /**
     * Refreshes target MAC addresses directly from PreferenceManager / Room DB
     * and updates the scan/connection manager.
     */
    public void refreshVehiclesScan() {
        if (bleScanManager != null) {
            if (bleScanManager.isScanning()) {
                bleScanManager.stopScan();
            }

            new Handler(Looper.getMainLooper()).postDelayed(this::startMultiVehicleScan, 100);
        }
    }

    private void evaluateScanState() {
        List<String> allTargets = vehicleRepository.getAllTargetMacAddresses();
        boolean allConnected = true;

        for (String mac : allTargets) {
            if (!isConnected(mac)) {
                allConnected = false;
                break;
            }
        }

        // If every target vehicle is connected, stop scanning!
        if (allConnected && bleScanManager.isScanning()) {
            bleScanManager.stopScan();
            logMessage("All target vehicles connected. Stopping background scan.");
        }
    }

    // Helper to fetch session for a given device
    public BleDeviceSession getSession(String macAddress) {
        return activeSessions.get(macAddress);
    }

    private void registerBluetoothStateReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(bluetoothStateReceiver, filter);
            }
            isReceiverRegistered = true;
        }
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        logMessage("System Update: Phone Bluetooth was turned on.");
                        if (preferenceManager.isForegroundServiceEnabled()) {
                            startMultiVehicleScan();
                        }
                        break;

                    case BluetoothAdapter.STATE_OFF:
                        logMessage("System Alert: Phone Bluetooth was turned off.");
                        disconnectAll();
                        break;
                }
            }
        }
    };

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean isConnected(String macAddress) {
        BleDeviceSession session = activeSessions.get(macAddress);
        return session != null && session.isConnected();
    }

    /**
     * Starts background/foreground scanning for all registered vehicles simultaneously
     * without needing to specify a single MAC address.
     */
    public void startMultiVehicleScan() {
        if (!isBluetoothEnabled()) {
            logMessage("System Alert: Please turn on phone Bluetooth before scanning.");
            return;
        }

        if (bleScanManager != null && !bleScanManager.isScanning()) {
            // Check if all are already connected first so we don't scan needlessly
            List<String> allTargets = vehicleRepository.getAllTargetMacAddresses();
            List<String> disconnectMacs = new ArrayList<>();
            boolean allConnected = true;
            for (String mac : allTargets) {
                if (!isConnected(mac)) {
                    allConnected = false;
                    disconnectMacs.add(mac);
                }
            }

            if (!allConnected) {
                bleScanManager.startScan(disconnectMacs);
            } else {
                logMessage("All target vehicles are already connected. Skipping scan.");
            }
        }
    }

    /**
     * Stops the multi-vehicle scan.
     */
    public void stopMultiVehicleScan() {
        if (bleScanManager != null && bleScanManager.isScanning()) {
            bleScanManager.stopScan();
            vehicleRepository.getAllTargetMacAddresses()
                    .stream()
                    .filter(mac -> !isConnected(mac))
                    .forEach(disconnectedMac ->
                            ConnectionStateRepository.getInstance()
                                    .updateState(disconnectedMac, ConnectionState.DISCONNECTED, "Offline")
                    );
            logMessage("Stopped multi-vehicle background scan.");
        }
    }

    public void connect(String macAddress, boolean autoConnect) {
        if (!isBluetoothEnabled()) {
            logMessage("System Alert: Please turn on phone Bluetooth.");
            return;
        }

        if (macAddress == null || macAddress.trim().isEmpty()) {
            logMessage("Configuration Error: Invalid MAC address.");
            return;
        }

        if (activeSessions.containsKey(macAddress) || connectingMacs.contains(macAddress)) {
            logMessage("Already Connected or Connecting to [" + macAddress + "]; Skipping.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                logMessage("Error: Missing runtime Bluetooth connection permission.");
                return;
            }
        }

        try {
            connectingMacs.add(macAddress); // Mark as in-flight immediately
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            logMessage("Connecting to target vehicle [" + macAddress + "]...");
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (IllegalArgumentException e) {
            connectingMacs.remove(macAddress);
            logMessage("Configuration Error: Invalid MAC address format.");
        } catch (SecurityException e) {
            connectingMacs.remove(macAddress);
            logMessage("Security Error: Blocked from connecting.");
        }
    }

    private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        } catch (SecurityException e) {
            logMessage("Security Error: Blocked from enabling notifications.");
        }
    }

    public void disconnect(String macAddress) {
        connectingMacs.remove(macAddress);
        BleDeviceSession session = activeSessions.remove(macAddress);

        if (session != null && session.getGatt() != null) {
            try {
                session.getGatt().disconnect();
                session.getGatt().close();
            } catch (SecurityException e) {
                logMessage("Security Error disconnecting GATT.");
            }
        }
        ConnectionStateRepository.getInstance().updateState(macAddress, ConnectionState.DISCONNECTED, "Offline");
    }

    public void disconnectAll() {
        for (String macAddress : new ArrayList<>(activeSessions.keySet())) {
            disconnect(macAddress);
        }
    }

    public void release() {
        bleScanManager.stopScan();
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothStateReceiver);
            } catch (IllegalArgumentException ignored) {}
            isReceiverRegistered = false;
        }
        disconnectAll();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String macAddress = gatt.getDevice().getAddress();
            connectingMacs.remove(macAddress); // Clear connecting lock

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                ConnectionStateRepository.getInstance().updateState(macAddress, ConnectionState.DISCOVERING, "Discovering...");

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                        }
                    } else {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    }
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    logMessage("Security Exception on post-connection setup.");
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect(macAddress);

                // If auto-connect is enabled, restart background scan for remaining targets after a short delay
                if (!preferenceManager.isAutoConnectEnabled()) {
                    logMessage("Auto-connect is disabled. Standing by.");
                } else if (!bleScanManager.isScanning()) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        startMultiVehicleScan();
                    }, 200);
                } else if (bleScanManager.getActiveTargetMacs().contains(macAddress)) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        bleScanManager.stopScan();
                        startMultiVehicleScan();
                    }, 200);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            String macAddress = gatt.getDevice().getAddress();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        enableNotifications(gatt, characteristic);

                        // Instantiate and register the dedicated device session per vehicle MAC
                        BleDeviceSession session = new BleDeviceSession(context, gatt, characteristic);
                        activeSessions.put(macAddress, session);

                        // This ensures Multi-Vehicle Scan is stopped once all connected.
                        evaluateScanState();

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                            if (listener != null) listener.onServicesReady(macAddress);
                            ConnectionStateRepository.getInstance().updateState(macAddress, ConnectionState.CONNECTED, "Connected");
                        }, 50);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String macAddress = gatt.getDevice().getAddress();
            if (listener != null && CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                listener.onDataReceived(macAddress, characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            String macAddress = gatt.getDevice().getAddress();
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid()) && value != null) {
                listener.onDataReceived(macAddress, value);
            }
        }
    };
}