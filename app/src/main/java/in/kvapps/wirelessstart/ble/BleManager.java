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
import java.util.UUID;

import in.kvapps.wirelessstart.BuildConfig;

public class BleManager {

    public interface BleListener {
        void onLog(String message);
        void onConnectionStateChanged(boolean isConnected, String statusText);
        void onServicesReady();
        void onVoltageReceived(float voltage); // NEW: Dispatches updated voltage string
    }
    private String targetHwName = "Vehicle 001";
    private String ESP32_MAC = BuildConfig.ESP32_MAC;
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    // Standard BLE Client Characteristic Configuration Descriptor (CCCD) UUID
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BleListener listener;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private boolean isReceiverRegistered = false;

    public BleManager(Context context, BleListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        registerBluetoothStateReceiver();
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
                    listener.onLog("System Alert: Phone Bluetooth was turned off.");
                    disconnect();
                    listener.onConnectionStateChanged(false, "Phone Bluetooth Off");
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
            listener.onLog("System Alert: Please turn on phone Bluetooth.");
            listener.onConnectionStateChanged(false, "Phone Bluetooth Off");
            return;
        }

        disconnect(); // Disconnect existing stale connections

        listener.onLog("Searching for " + targetHwName + " [" + ESP32_MAC + "]...");
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(ESP32_MAC);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    listener.onLog("Error: Missing runtime Bluetooth connection permission.");
                    listener.onConnectionStateChanged(false, "Permission Missing");
                    return;
                }
            }

            // Dynamically pass the autoConnect flag here
            bluetoothGatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE);

        } catch (IllegalArgumentException e) {
            listener.onLog("Configuration Error: Invalid MAC address provided.");
        } catch (SecurityException e) {
            listener.onLog("Security Error: Operating system blocked connection profile.");
            listener.onConnectionStateChanged(false, "Security Exception");
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
                    listener.onLog("Command Transmitted -> " + command);
                    if (onSuccess != null) {
                        onSuccess.run(); // Trigger the success callback
                    }
                }
            } catch (SecurityException e) {
                listener.onLog("Security Exception: Missing OS permission mapping.");
                if (onFailure != null) onFailure.run();
            }
        } else {
            listener.onLog("Action Blocked: Hardware connection is offline.");
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
            listener.onLog("Security Error: Blocked from enabling notifications.");
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
                listener.onLog("Security Error passing raw bytes.");
            }
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (SecurityException e) {
                listener.onLog("Security Error while disconnecting GATT.");
            }
            bluetoothGatt = null;
        }
        commandCharacteristic = null;
    }

    // Call this if your app destroys the manager instance to prevent memory leaks
    public void release() {
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

    public void setTargetHwName(String targetHwName) {
        if (targetHwName != null && !targetHwName.trim().isEmpty()) {
            this.targetHwName = targetHwName;
        }
    }

    public void setMacAdd(String macAdd) {
        if (macAdd != null && !macAdd.trim().isEmpty()) {
            this.ESP32_MAC = macAdd;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onConnectionStateChanged(true, targetHwName + " Connected");

                // REQUEST HIGH PRIORITY
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                        } else {
                            listener.onLog("Security Warning: Missing BLUETOOTH_CONNECT permission for priority request.");
                        }
                    } else {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    }
                } catch (SecurityException e) {
                    listener.onLog("Security Exception: Blocked from setting connection priority.");
                }

                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    listener.onLog("Security Error: Failed to discover services.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String statusText = targetHwName + " Offline";
                listener.onLog("System Alert: " + statusText);
                listener.onConnectionStateChanged(false, statusText);
                commandCharacteristic = null;
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
                            .postDelayed(listener::onServicesReady, 50);
                } else {
                    listener.onLog("Error: Service UUID matching failed.");
                }
            }
        }

        // NEW: Triggers every time the ESP32 calls pCharacteristic->notify()
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length >= 2) {
                    // Extract byte structures and shift bits to rebuild the 16-bit payload
                    int highByte = data[0] & 0xFF;
                    int lowByte = data[1] & 0xFF;
                    int milliVolts = (highByte << 8) | lowByte;

                    // Convert raw millivolt integer back into a decimal reading
                    float finalVoltage = milliVolts / 1000.0f;

                    // Pass metrics back up to the main UI loop safely
                    listener.onVoltageReceived(finalVoltage);
                }
            }
        }

        // Android 13+ Callback compatibility variant for newer compilation structures
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid()) && value != null && value.length >= 2) {
                int milliVolts = ((value[0] & 0xFF) << 8) | (value[1] & 0xFF);
                float finalVoltage = milliVolts / 1000.0f;
                listener.onVoltageReceived(finalVoltage);
            }
        }
    };

    // Force-sync telemetry state to ESP32 (0x03 = Enable, 0x02 = Disable)
    public void syncTelemetryState(boolean isEnabled) {
        byte commandByte = (byte) (isEnabled ? 0x03 : 0x02);
        sendRawByteCommand(commandByte);
        listener.onLog("Syncing telemetry state -> " + (isEnabled ? "ENABLED" : "DISABLED"));
    }

    // Automatically sync current Unix timestamp to ESP32 for scheduled night sleep
    public void sendAutoTimeSync() {
        long currentEpochSeconds = System.currentTimeMillis() / 1000;
        String syncCommand = "TIME:" + currentEpochSeconds;
        listener.onLog("Auto-syncing system time to ESP32...");
        sendBleCommand(syncCommand, null, null);
    }
}