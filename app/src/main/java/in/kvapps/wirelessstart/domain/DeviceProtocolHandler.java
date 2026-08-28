package in.kvapps.wirelessstart.domain;

import android.content.Context;
import android.util.Log;

import java.time.format.DateTimeFormatter;

import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.util.WearSyncUtils;

public class DeviceProtocolHandler {
    private static final String TAG = "ProtocolHandler";
    private final Context context;
    private final PreferenceManager preferenceManager;
    private ProtocolListener listener;

    public interface ProtocolListener {
        void onVoltageReady(float voltage);
        void onProtocolLog(String message);
    }

    public DeviceProtocolHandler(Context context, ProtocolListener listener) {
        this.context = context.getApplicationContext();
        this.preferenceManager = new PreferenceManager(this.context);
        this.listener = listener;
    }

    public void setListener(ProtocolListener listener) {
        this.listener = listener;
    }

    // Process incoming raw BLE byte arrays
    public void parseIncomingData(byte[] data) {
        if (data == null || data.length == 0) return;

        String payloadString = new String(data).trim();

        // 1. Handle Text / Command Protocol Responses (e.g. Schedule sync from ESP32)
        if (payloadString.startsWith("SCHED_IS:")) {
            try {
                long serverEpoch = Long.parseLong(payloadString.substring(9).trim());
                if (listener == null) {
                    Log.e(TAG, "ERROR -> DeviceProtocolHandler.listener is null");
                    return;
                }

                // Save locally and push down to watch via Data Layer
                preferenceManager.saveEngineScheduledRunTime(serverEpoch);
                WearSyncUtils.syncScheduleToWear(context, serverEpoch);

                // Handle cancellation / empty schedule explicitly
                if (serverEpoch == 0) {
                    listener.onProtocolLog("[ESP32 RX] Engine-Run Schedule Cancelled");
                    return;
                }

                // Convert epoch (seconds) to a readable formatted date/time string
                String formattedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(java.time.Instant.ofEpochMilli(serverEpoch * 1000));

                listener.onProtocolLog("[ESP32 RX] Engine-Run Schedule Completed -> " + formattedDate);

            } catch (NumberFormatException e) {
                if (listener != null) listener.onProtocolLog("Failed to parse schedule epoch from ESP32.");
            }
            return;
        }

        // 2. Handle Binary Telemetry Payload (Voltage: High Byte + Low Byte)
        if (data.length >= 2) {
            int highByte = data[0] & 0xFF;
            int lowByte = data[1] & 0xFF;
            int milliVolts = (highByte << 8) | lowByte;
            float finalVoltage = milliVolts / 1000.0f;

            if (listener != null) {
                listener.onVoltageReady(finalVoltage);
            }
        }
    }
}