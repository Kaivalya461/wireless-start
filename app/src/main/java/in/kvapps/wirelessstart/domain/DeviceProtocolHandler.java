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
        void onVoltageReady(String macAddress, float voltage);
        void onProtocolLog(String message);
        // Added callback when vehicle configuration is received from nRF52
        void onVehicleConfigReceived(String macAddress, String deviceName, boolean startEnabled, boolean stopEnabled, boolean battEnabled);
    }

    public DeviceProtocolHandler(Context context, ProtocolListener listener) {
        this.context = context.getApplicationContext();
        this.preferenceManager = new PreferenceManager(this.context);
        this.listener = listener;
    }

    public void setListener(ProtocolListener listener) {
        this.listener = listener;
    }

    // Process incoming raw BLE byte arrays mapped to the specific vehicle MAC address
    public void parseIncomingData(String macAddress, byte[] data) {
        if (data == null || data.length == 0) return;

        String payloadString = new String(data).trim();

        // 1. Handle Vehicle Configuration Response (CONFIG:Name,StartEn,StopEn,BattEn)
        if (payloadString.startsWith("CONFIG:")) {
            try {
                String configData = payloadString.substring(7);
                String[] parts = configData.split(",");

                if (parts.length >= 4) {
                    String deviceName = parts[0];
                    boolean startEnabled = parts[1].equals("1");
                    boolean stopEnabled = parts[2].equals("1");
                    boolean battEnabled = parts[3].equals("1");

                    if (listener != null) {
                        listener.onVehicleConfigReceived(macAddress, deviceName, startEnabled, stopEnabled, battEnabled);
                        listener.onProtocolLog("[nRF52 RX][" + macAddress + "] Config Synced -> Name: " + deviceName);
                    }
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onProtocolLog("Failed to parse vehicle configuration payload from " + macAddress);
                }
            }
            return;
        }

        // 2. Handle Text / Command Protocol Responses (e.g. Schedule sync from ESP32/nRF52)
        if (payloadString.startsWith("SCHED_IS:")) {
            // Only process schedule updates if the toggle is enabled in preferences
            if (!preferenceManager.isEngineScheduleEnabled()) {
                return;
            }

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
                    listener.onProtocolLog("[nRF52 RX][" + macAddress + "] Engine-Run Schedule Cancelled");
                    return;
                }

                // Convert epoch (seconds) to a readable formatted date/time string
                String formattedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(java.time.Instant.ofEpochMilli(serverEpoch * 1000));

                listener.onProtocolLog("[nRF52 RX][" + macAddress + "] Engine-Run Schedule Completed -> " + formattedDate);

            } catch (NumberFormatException e) {
                if (listener != null) listener.onProtocolLog("Failed to parse schedule epoch from nRF52 [" + macAddress + "].");
            }
            return;
        }

        // 3. Handle Binary Telemetry Payload (Voltage: High Byte + Low Byte)
        if (data.length >= 2) {
            int highByte = data[0] & 0xFF;
            int lowByte = data[1] & 0xFF;
            int milliVolts = (highByte << 8) | lowByte;
            float finalVoltage = milliVolts / 1000.0f;

            if (listener != null) {
                listener.onVoltageReady(macAddress, finalVoltage);
            }
        }
    }
}