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
import android.util.Log;

import java.util.UUID;

import in.kvapps.wirelessstart.data.PreferenceManager;

public class BleManager {

    public interface BleListener {
        void onLog(String message);
        void onConnectionStateChanged(boolean isConnected, String statusText);
        void onServicesReady();
        void onDataReceived(byte[] rawData);
    }
    public interface RssiCallback {
        void onRssiRead(int rssi);
    }
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    // Standard BLE Client Characteristic Configuration Descriptor (CCCD) UUID
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private BleListener listener; // Modified to allow updating listener dynamically across layouts/activities
    private RssiCallback currentRssiCallback;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private boolean isReceiverRegistered = false;
    private final PreferenceManager preferenceManager;
    private final BleScanManager bleScanManager;

    public BleManager(Context context, BleListener listener) {
        this.context = context.getApplicationContext(); // Use application context to prevent memory leaks across Activity navigations
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.preferenceManager = new PreferenceManager(this.context);

        // Initialize the dedicated Custom Scan Manager
        this.bleScanManager = new BleScanManager(
                this.context,
                SERVICE_UUID,
                preferenceManager.getTargetMacAddress(),
                new BleScanManager.ScanListener() {
                    @Override
                    public void onDeviceFound(String macAddress) {
                        // When device is detected via scan, connect instantly (autoConnect = false)
                        connect(false);
                    }

                    @Override
                    public void onScanLog(String message) {
                        if (BleManager.this.listener != null) {
                            BleManager.this.listener.onLog(message);
                        }
                    }
                }
        );

        registerBluetoothStateReceiver();
    }

    // Allows updating the UI listener when navigating between activities
    public void setListener(BleListener listener) {
        this.listener = listener;
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

                // Only trigger once when Bluetooth is completely off
                if (state == BluetoothAdapter.STATE_OFF) {
                    if (listener != null) {
                        listener.onLog("System Alert: Phone Bluetooth was turned off.");
                        listener.onConnectionStateChanged(false, "Phone Bluetooth Off");
                    }
                    disconnect();
                }
            }
        }
    };

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean isConnected() {
        return bluetoothGatt != null;
    }

    // Pass true for background auto-reconnect, false for fast watch shortcuts
    public void connect(boolean autoConnect) {
        if (!isBluetoothEnabled()) {
            if (listener != null) {
                listener.onLog("System Alert: Please turn on phone Bluetooth.");
                listener.onConnectionStateChanged(false, "Phone Bluetooth Off");
            }
            return;
        }

        disconnect();

        // Retrieve the latest saved values directly from the class-level PreferenceManager
        String currentMac = preferenceManager.getTargetMacAddress();
        String currentHwName = preferenceManager.getTargetHwName();

        if (currentMac == null || currentMac.trim().isEmpty()) {
            if (listener != null) {
                listener.onLog("Configuration Error: No MAC address configured.");
                listener.onConnectionStateChanged(false, "Invalid MAC");
            }
            return;
        }

        String maskedMac = currentMac.length() >= 5 ? "..." + currentMac.substring(currentMac.length() - 5) : "......";

        if (listener != null) {
            listener.onLog("Searching for " + currentHwName + " [" + maskedMac + "]...");
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(currentMac);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (listener != null) {
                        listener.onLog("Error: Missing runtime Bluetooth connection permission.");
                        listener.onConnectionStateChanged(false, "Permission Missing");
                    }
                    return;
                }
            }

            // Always pass 'false' to connectGatt for instant connection.
            // The "auto-connect" behavior is intelligently handled by Custom BleScanManager instead of Android's lazy stack.
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);

        } catch (IllegalArgumentException e) {
            if (listener != null) {
                listener.onLog("Configuration Error: Invalid MAC address provided.");
                listener.onConnectionStateChanged(false, "Invalid MAC");
            }
        } catch (SecurityException e) {
            if (listener != null) {
                listener.onLog("Security Error: Operating system blocked connection profile.");
                listener.onConnectionStateChanged(false, "Security Exception");
            }
        }
    }

    public void sendBleCommand(String command, Runnable onSuccess, Runnable onFailure) {
        if (commandCharacteristic != null && bluetoothGatt != null) {
            try {
                boolean success = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    int status = bluetoothGatt.writeCharacteristic(
                            commandCharacteristic,
                            command.getBytes(),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                    // Note: writeCharacteristic returns a status code in newer APIs,
                    // but if it initiates successfully without throwing:
                    success = (status == BluetoothGatt.GATT_SUCCESS); // Or check API specific return
                } else {
                    commandCharacteristic.setValue(command.getBytes());
                    success = bluetoothGatt.writeCharacteristic(commandCharacteristic);
                }

                if (success) {
//                    if (listener != null) listener.onLog("Command Transmitted -> " + command);
                    if (onSuccess != null) {
                        onSuccess.run(); // Trigger the success callback
                    }
                }
            } catch (SecurityException e) {
                if (listener != null) listener.onLog("Security Exception: Missing OS permission mapping.");
                if (onFailure != null) onFailure.run();
            }
        } else {
            if (listener != null) listener.onLog("Action Blocked: Hardware connection is offline.");
            if (onFailure != null) onFailure.run();
        }
    }

    // NEW: Subscribes Android engine to listen to incoming battery data pushes
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
            if (listener != null) listener.onLog("Security Error: Blocked from enabling notifications.");
        }
    }

    public void sendRawByteCommand(byte controlByte) {
        if (commandCharacteristic != null && bluetoothGatt != null) {
            try {
                byte[] payload = new byte[]{ controlByte };
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeCharacteristic(
                            commandCharacteristic,
                            payload,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                } else {
                    commandCharacteristic.setValue(payload);
                    bluetoothGatt.writeCharacteristic(commandCharacteristic);
                }
//                listener.onLog("Byte Packet Transmitted -> 0x" + String.format("%02X", controlByte));
            } catch (SecurityException e) {
                if (listener != null) listener.onLog("Security Error passing raw bytes.");
            }
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (SecurityException e) {
                if (listener != null) listener.onLog("Security Error while disconnecting GATT.");
            }
            bluetoothGatt = null;
        }
        commandCharacteristic = null;
    }

    // Call this if your app destroys the manager instance to prevent memory leaks
    public void release() {
        bleScanManager.stopScan();
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothStateReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver was already unregistered
            }
            isReceiverRegistered = false;
        }
        disconnect();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bleScanManager.stopScan();
                if (listener != null) {
                    listener.onConnectionStateChanged(true, preferenceManager.getTargetHwName() + " Connected");
                }

                // REQUEST HIGH PRIORITY
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                        } else if (listener != null) {
                            listener.onLog("Security Warning: Missing BLUETOOTH_CONNECT permission for priority request.");
                        }
                    } else {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    }
                } catch (SecurityException e) {
                    if (listener != null) listener.onLog("Security Exception: Blocked from setting connection priority.");
                }

                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    if (listener != null) listener.onLog("Security Error: Failed to discover services.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String statusText = preferenceManager.getTargetHwName() + " Offline";
                if (listener != null) {
                    listener.onLog("System Alert: " + statusText);
                    listener.onConnectionStateChanged(false, statusText);
                }
                release();

                // ONLY start background scanning if the user's auto-connect preference is TRUE
                if (preferenceManager.isAutoConnectEnabled()) {
                    bleScanManager.startScan();
                } else if (listener != null) {
                    listener.onLog("Auto-connect is disabled. Standing by.");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);

                    // 1. Enable to BLE Push Communications between Phone and ESP32
                    enableNotifications(gatt, commandCharacteristic);

                    // 2. Notify listener that the pipeline is fully ready
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(() -> {
                                if (listener != null) listener.onServicesReady();
                            }, 50);
                } else if (listener != null) {
                    listener.onLog("Error: Service UUID matching failed.");
                }
            }
        }

        // NEW: Triggers every time the ESP32 calls pCharacteristic->notify()
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                listener.onDataReceived(data);
            }
        }

        // Android 13+ Callback compatibility variant for newer compilation structures
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid()) && value != null && value.length >= 2) {
                listener.onDataReceived(value);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (currentRssiCallback != null) {
                    currentRssiCallback.onRssiRead(rssi);
                }
            } else {
                if (currentRssiCallback != null) {
                    currentRssiCallback.onRssiRead(0);
                }
            }
        }
    };

    // Force-sync telemetry state to ESP32 (0x03 = Enable, 0x02 = Disable)
    public void syncTelemetryState(boolean isEnabled) {
        byte commandByte = (byte) (isEnabled ? 0x03 : 0x02);
        sendRawByteCommand(commandByte);
//        listener.onLog("Syncing telemetry state -> " + (isEnabled ? "ENABLED" : "DISABLED"));
    }

    // Automatically sync current Unix timestamp to ESP32 for scheduled night sleep
    public void sendAutoTimeSync() {
        long currentEpochSeconds = System.currentTimeMillis() / 1000;
        String syncCommand = "TIME:" + currentEpochSeconds;
//        listener.onLog("Auto-syncing system time to ESP32...");
        sendBleCommand(syncCommand, null, null);
    }

    // Send Fail-Safe state as a plain-text command string to prevent GATT queue collisions
    public void syncFailSafeState(boolean isEnabled) {
        String command = isEnabled ? "FAILSAFE:ON" : "FAILSAFE:OFF";
        sendBleCommand(command, null, null);
    }

    // Executes initialization commands sequentially with built-in spacing to prevent GATT collisions
    public void syncInitializationSequence(Runnable... tasks) {
        if (tasks == null || tasks.length == 0) return;

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int i = 0; i < tasks.length; i++) {
            final Runnable task = tasks[i];
            long delayMillis = i * 50L; // Stagger each command by 50ms
            handler.postDelayed(() -> {
                if (isConnected()) {
                    task.run();
                }
            }, delayMillis);
        }
    }

    public void sendScheduledEpoch(long epochSeconds) {
        String command = "SCHED_RUN:" + epochSeconds;
        sendBleCommand(command,
                () -> Log.d("BleManager", "Scheduled Epoch transmitted successfully -> " + epochSeconds),
                () -> Log.e("BleManager", "Failed to transmit Scheduled Epoch command.")
        );
    }

    public void requestScheduleFromEsp32() {
        sendBleCommand("GET_ENGINE_SCHEDULE", () -> {
            Log.d("BleManager", "Requested active schedule from ESP32");
        }, null);
    }

    public void readRssi(RssiCallback callback) {
        this.currentRssiCallback = callback;
        if (bluetoothGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt.readRemoteRssi();
                    }
                } else {
                    bluetoothGatt.readRemoteRssi();
                }
            } catch (SecurityException e) {
                if (listener != null) listener.onLog("Security Error: Blocked reading RSSI.");
                if (currentRssiCallback != null) {
                    currentRssiCallback.onRssiRead(0);
                }
            }
        } else {
            if (currentRssiCallback != null) {
                currentRssiCallback.onRssiRead(0);
            }
        }
    }
}