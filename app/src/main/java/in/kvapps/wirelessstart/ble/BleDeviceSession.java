package in.kvapps.wirelessstart.ble;

import android.content.Context;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import in.kvapps.wirelessstart.enums.BleCommand;
import in.kvapps.wirelessstart.shared.Constants;

public class BleDeviceSession {
    private static final String TAG = Constants.BLE_DEVICE_SESSION_TAG;

    private static final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private final Map<Integer, PendingAck> pendingAcks = new ConcurrentHashMap<>();
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    private final Context context;
    private final BluetoothGatt gatt;
    private final BluetoothGattCharacteristic commandCharacteristic;
    private final String macAddress;
    private final boolean supportsAck;
    private final Handler rssiHandler = new Handler(Looper.getMainLooper());
    private Runnable rssiRunnable;

    private static class PendingAck {
        final Runnable onSuccess;
        final Runnable onFailure;
        final Runnable timeoutRunnable;

        PendingAck(Runnable onSuccess, Runnable onFailure, Runnable timeoutRunnable) {
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
            this.timeoutRunnable = timeoutRunnable;
        }
    }

    // Updated constructor accepting supportsAck flag
    public BleDeviceSession(Context context, BluetoothGatt gatt, BluetoothGattCharacteristic commandCharacteristic, boolean supportsAck) {
        this.context = context;
        this.gatt = gatt;
        this.commandCharacteristic = commandCharacteristic;
        this.macAddress = gatt != null ? gatt.getDevice().getAddress() : "";
        this.supportsAck = supportsAck;
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

    public void sendCommand(byte commandId, byte[] payloadData, Runnable onSuccess, Runnable onFailure) {
        if (!isConnected()) {
            safeRun(onFailure);
            return;
        }

        if (supportsAck) {
            sendAckTrackedCommand(commandId, payloadData, onSuccess, onFailure);
        } else {
            sendLegacyCommand(commandId, payloadData, onSuccess, onFailure);
        }
    }

    private void sendAckTrackedCommand(byte commandId, byte[] payloadData, Runnable onSuccess, Runnable onFailure) {
        int seqId = sequenceCounter.incrementAndGet() & 0xFF;
        byte[] finalPayload = buildAckPayload(commandId, seqId, payloadData);

        registerPendingAck(seqId, onSuccess, onFailure);

        boolean success = writePayloadToGatt(finalPayload);
        if (!success) {
            cancelPendingAck(seqId);
            safeRun(onFailure);
        }
    }

    private void sendLegacyCommand(byte commandId, byte[] payloadData, Runnable onSuccess, Runnable onFailure) {
        byte[] finalPayload = buildLegacyPayload(commandId, payloadData);
        boolean success = writePayloadToGatt(finalPayload);

        if (success) {
            safeRun(onSuccess);
        } else {
            safeRun(onFailure);
        }
    }

    private byte[] buildAckPayload(byte commandId, int seqId, byte[] payloadData) {
        int payloadLen = (payloadData != null) ? payloadData.length : 0;
        byte[] finalPayload = new byte[2 + payloadLen];
        finalPayload[0] = commandId;
        finalPayload[1] = (byte) seqId;
        if (payloadLen > 0) {
            System.arraycopy(payloadData, 0, finalPayload, 2, payloadLen);
        }
        return finalPayload;
    }

    private byte[] buildLegacyPayload(byte commandId, byte[] payloadData) {
        if (payloadData != null && payloadData.length > 0) {
            return payloadData;
        }
        return new byte[]{commandId};
    }

    private void registerPendingAck(int seqId, Runnable onSuccess, Runnable onFailure) {
        Runnable timeoutRunnable = () -> {
            pendingAcks.remove(seqId);
            Log.w(TAG, "Command ACK timeout for seqId: " + seqId + " on device " + macAddress);
            safeRun(onFailure);
        };

        pendingAcks.put(seqId, new PendingAck(onSuccess, onFailure, timeoutRunnable));
        timeoutHandler.postDelayed(timeoutRunnable, 2000); // 2-second timeout
    }

    private boolean writePayloadToGatt(byte[] payload) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int status = gatt.writeCharacteristic(
                        commandCharacteristic,
                        payload,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                );
                return (status == BluetoothGatt.GATT_SUCCESS);
            } else {
                commandCharacteristic.setValue(payload);
                return gatt.writeCharacteristic(commandCharacteristic);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception writing characteristic for " + macAddress, e);
            return false;
        }
    }

    private void safeRun(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private void cancelPendingAck(int seqId) {
        PendingAck ack = pendingAcks.remove(seqId);
        if (ack != null) {
            timeoutHandler.removeCallbacks(ack.timeoutRunnable);
        }
    }

    public void handleIncomingAck(byte[] data) {
        if (!supportsAck || data == null || data.length < 3) return;

        int cmdId = data[0] & 0xFF;
        int seqId = data[1] & 0xFF;
        int status = data[2] & 0xFF;

        PendingAck pendingAck = pendingAcks.remove(seqId);
        if (pendingAck != null) {
            timeoutHandler.removeCallbacks(pendingAck.timeoutRunnable);

            if (status == BleCommand.STATUS_SUCCESS) {
                if (pendingAck.onSuccess != null) pendingAck.onSuccess.run();
            } else {
                if (pendingAck.onFailure != null) pendingAck.onFailure.run();
            }
        }
    }

    // Force-sync telemetry state to ESP32 (0x03 = Enable, 0x02 = Disable)
    public void syncTelemetryState(boolean isEnabled) {
        byte commandByte = (byte) (isEnabled ? 0x03 : 0x02);
        sendCommand(commandByte, null,
                () -> Log.d(TAG, "Telemetry sync acknowledged"),
                () -> Log.e(TAG, "Telemetry sync failed/timed out")
        );
    }

    // Automatically sync current Unix timestamp to ESP32
    public void sendAutoTimeSync() {
        long currentEpochSeconds = System.currentTimeMillis() / 1000;
        String syncCommand = "TIME:" + currentEpochSeconds;
        sendCommand(BleCommand.TIME_SYNC.getCommandId(), syncCommand.getBytes(), null, null);
    }

    // Send Fail-Safe state
    public void syncFailSafeState(boolean isEnabled) {
        String command = isEnabled ? "FAILSAFE:ON" : "FAILSAFE:OFF";
        sendCommand(BleCommand.FAILSAFE_STATE.getCommandId(), command.getBytes(), null, null);
    }

    public void sendScheduledEpoch(long epochSeconds) {
        String command = "SCHED_RUN:" + epochSeconds;
        sendCommand(BleCommand.SCHEDULED_RUN.getCommandId(), command.getBytes(),
                () -> Log.d(TAG, "Scheduled Epoch transmitted & ACKed on " + macAddress),
                () -> Log.e(TAG, "Failed to transmit Scheduled Epoch to " + macAddress)
        );
    }

    public void requestScheduleFromEsp32() {
        // Send command ID 0x13 with null payload to avoid ATT MTU character truncation
        sendCommand(BleCommand.GET_SCHEDULE.getCommandId(), null, () -> {
            Log.d(TAG, "Requested active schedule acknowledged by " + macAddress);
        }, null);
    }

    /**
     * Request the current persistent configuration from the esp32/nRF52 device.
     */
    public void fetchVehicleConfig() {
        sendCommand(BleCommand.GET_CONFIG.getCommandId(), "GET_CONFIG".getBytes(),
                () -> Log.d(TAG, "GET_CONFIG command ACKed."),
                () -> Log.e(TAG, "GET_CONFIG command failed/timed out.")
        );
    }

    public void sendTextCommand(String commandText, Runnable onSuccess, Runnable onFailure) {
        if (commandText == null || commandText.isEmpty()) {
            if (onFailure != null) onFailure.run();
            return;
        }

        // Resolve string to enum, then grab its byte ID
        BleCommand command = BleCommand.resolve(commandText);
        sendCommand(command.getCommandId(), commandText.getBytes(), onSuccess, onFailure);
    }

    public void startRssiPolling() {
        if (rssiRunnable != null) return;

        rssiRunnable = new Runnable() {
            @Override
            public void run() {
                if (gatt != null) {
                    try { gatt.readRemoteRssi(); } catch (SecurityException ignored) {}
                }
                rssiHandler.postDelayed(this, 1000);
            }
        };
        rssiHandler.post(rssiRunnable);
    }

    public void stopRssiPolling() {
        if (rssiHandler != null && rssiRunnable != null) {
            rssiHandler.removeCallbacks(rssiRunnable);
            rssiRunnable = null;
        }
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
}