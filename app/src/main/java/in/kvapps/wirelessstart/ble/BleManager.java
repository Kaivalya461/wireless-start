package in.kvapps.wirelessstart.ble;

import android.Manifest;
import android.content.Context;
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

    private static final String ESP32_MAC = BuildConfig.ESP32_MAC;
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    // Standard BLE Client Characteristic Configuration Descriptor (CCCD) UUID
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BleListener listener;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic commandCharacteristic;

    public BleManager(Context context, BleListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void connect() {
        if (!isBluetoothEnabled()) {
            listener.onLog("System Alert: Please turn on phone Bluetooth.");
            listener.onConnectionStateChanged(false, "Phone Bluetooth Off");
            return;
        }

        disconnect(); // Disconnect existing stale connections

        listener.onLog("Searching for Dio hardware [" + ESP32_MAC + "]...");
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(ESP32_MAC);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    listener.onLog("Error: Missing runtime Bluetooth connection permission.");
                    listener.onConnectionStateChanged(false, "Permission Missing");
                    return;
                }
            }

            bluetoothGatt = device.connectGatt(context, true, gattCallback);

        } catch (IllegalArgumentException e) {
            listener.onLog("Configuration Error: Invalid MAC address provided.");
        } catch (SecurityException e) {
            listener.onLog("Security Error: Operating system blocked connection profile.");
            listener.onConnectionStateChanged(false, "Security Exception");
        }
    }

    public void sendBleCommand(String command) {
        if (commandCharacteristic != null && bluetoothGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeCharacteristic(
                            commandCharacteristic,
                            command.getBytes(),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                } else {
                    commandCharacteristic.setValue(command.getBytes());
                    bluetoothGatt.writeCharacteristic(commandCharacteristic);
                }
                listener.onLog("Command Transmitted -> " + command);
            } catch (SecurityException e) {
                listener.onLog("Security Exception: Missing OS permission mapping.");
            }
        } else {
            listener.onLog("Action Blocked: Hardware connection is offline.");
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
                listener.onLog("Battery stream channel listening.");
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
                listener.onLog("Byte Packet Transmitted -> 0x" + String.format("%02X", controlByte));
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
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onConnectionStateChanged(true, "Dio Hardware Connected");
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    listener.onLog("Security Error: Failed to discover services.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener.onConnectionStateChanged(false, "Dio Hardware Offline");
                commandCharacteristic = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    listener.onLog("Data pipeline channel mapped.");

                    // Enable background telemetry updates
                    // 1. First sync time to ESP32
                    enableNotifications(gatt, commandCharacteristic);

                    // Sync calendar time markers
                    sendAutoTimeSync();

                    // 2. Notify listener that the pipeline is fully ready
                    listener.onServicesReady();
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

    // Automatically sync current Unix timestamp to ESP32 for scheduled night sleep
    private void sendAutoTimeSync() {
        long currentEpochSeconds = System.currentTimeMillis() / 1000;
        String syncCommand = "TIME:" + currentEpochSeconds;
        listener.onLog("Auto-syncing system time to ESP32...");
        sendBleCommand(syncCommand);
    }
}