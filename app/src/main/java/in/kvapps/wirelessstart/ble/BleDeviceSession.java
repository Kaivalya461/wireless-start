package in.kvapps.wirelessstart.ble;

import android.content.Context;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import in.kvapps.wirelessstart.shared.Constants;

public class BleDeviceSession {
    private static final String TAG = Constants.BLE_DEVICE_SESSION_TAG;

    private final Context context;
    private final BluetoothGatt gatt;
    private final BluetoothGattCharacteristic commandCharacteristic;
    private final String macAddress;

    public BleDeviceSession(Context context, BluetoothGatt gatt, BluetoothGattCharacteristic commandCharacteristic) {
        this.context = context;
        this.gatt = gatt;
        this.commandCharacteristic = commandCharacteristic;
        this.macAddress = gatt != null ? gatt.getDevice().getAddress() : "";
    }

    public String getMacAddress() {
        return macAddress;
    }

    public BluetoothGatt getGatt() {
        return gatt;
    }

    public boolean isConnected() {
        return gatt != null && commandCharacteristic != null;
    }

    public void sendBleCommand(String command, Runnable onSuccess, Runnable onFailure) {
        if (commandCharacteristic != null && gatt != null) {
            try {
                boolean success;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    int status = gatt.writeCharacteristic(
                            commandCharacteristic,
                            command.getBytes(),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                    success = (status == BluetoothGatt.GATT_SUCCESS);
                } else {
                    commandCharacteristic.setValue(command.getBytes());
                    success = gatt.writeCharacteristic(commandCharacteristic);
                }

                if (success && onSuccess != null) {
                    onSuccess.run();
                } else if (!success && onFailure != null) {
                    onFailure.run();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security Exception writing characteristic for " + macAddress);
                if (onFailure != null) onFailure.run();
            }
        } else {
            if (onFailure != null) onFailure.run();
        }
    }

    public void sendRawByteCommand(byte controlByte) {
        if (commandCharacteristic != null && gatt != null) {
            try {
                byte[] payload = new byte[]{controlByte};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(commandCharacteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                } else {
                    commandCharacteristic.setValue(payload);
                    gatt.writeCharacteristic(commandCharacteristic);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security Error sending raw bytes to " + macAddress);
            }
        }
    }

    // Force-sync telemetry state to ESP32 (0x03 = Enable, 0x02 = Disable)
    public void syncTelemetryState(boolean isEnabled) {
        byte commandByte = (byte) (isEnabled ? 0x03 : 0x02);
        sendRawByteCommand(commandByte);
    }

    // Automatically sync current Unix timestamp to ESP32
    public void sendAutoTimeSync() {
        long currentEpochSeconds = System.currentTimeMillis() / 1000;
        String syncCommand = "TIME:" + currentEpochSeconds;
        sendBleCommand(syncCommand, null, null);
    }

    // Send Fail-Safe state
    public void syncFailSafeState(boolean isEnabled) {
        String command = isEnabled ? "FAILSAFE:ON" : "FAILSAFE:OFF";
        sendBleCommand(command, null, null);
    }

    // Executes initialization commands sequentially with built-in spacing
    public void syncInitializationSequence(Runnable... tasks) {
        if (tasks == null || tasks.length == 0) return;

        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < tasks.length; i++) {
            final Runnable task = tasks[i];
            long delayMillis = i * 100L;
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
                () -> Log.d(TAG, "Scheduled Epoch transmitted to " + macAddress),
                () -> Log.e(TAG, "Failed to transmit Scheduled Epoch to " + macAddress)
        );
    }

    public void requestScheduleFromEsp32() {
        sendBleCommand("GET_ENGINE_SCHEDULE", () -> {
            Log.d(TAG, "Requested active schedule from " + macAddress);
        }, null);
    }
}