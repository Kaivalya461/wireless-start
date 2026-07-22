package in.kvapps.wirelessstart.ble;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import java.util.UUID;

public class BleManager {

    public interface BleListener {
        void onLog(String message);
        void onConnectionStateChanged(boolean isConnected, String statusText);
    }

    private static final String ESP32_MAC = "AA:BB:CC:DD:EE:FF";
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

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

            bluetoothGatt = device.connectGatt(context, false, gattCallback);

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
                listener.onConnectionStateChanged(true, "Dio Hardware Ready");
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
                } else {
                    listener.onLog("Error: Service UUID matching failed.");
                }
            }
        }
    };
}