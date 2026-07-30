package in.kvapps.wirelessstart.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import in.kvapps.wirelessstart.BuildConfig;
import in.kvapps.wirelessstart.enums.DurationOption;

public class PreferenceManager {
    private static final String PREFS_NAME = "WirelessStartPrefs";
    private static final String KEY_START_SPINNER_POS = "start_spinner_pos";
    private static final String KEY_START_CUSTOM_MS = "start_custom_ms";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";
    private static final String KEY_TARGET_HW_NAME = "target_hw_name";
    private static final String DEFAULT_HW_NAME = "Vehicle 001";
    private static final String KEY_TARGET_MAC = "target_mac_address";
    private static final long DEFAULT_START_MS = 1500;

    private final SharedPreferences prefs;
    private final Context context;

    public PreferenceManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveStartSpinnerPosition(int pos) {
        prefs.edit().putInt(KEY_START_SPINNER_POS, pos).apply();
        syncToWearables();
    }

    public int getStartSpinnerPosition() {
        return prefs.getInt(KEY_START_SPINNER_POS, 0);
    }

    public void saveStartCustomMs(String customMs) {
        prefs.edit().putString(KEY_START_CUSTOM_MS, customMs).apply();
        syncToWearables();
    }

    public String getStartCustomMs() {
        return prefs.getString(KEY_START_CUSTOM_MS, "");
    }

    public void setTelemetryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled).apply();
    }

    public boolean isTelemetryEnabled() {
        return prefs.getBoolean(KEY_TELEMETRY_ENABLED, false);
    }

    // Save the new target hardware name
    public void saveTargetHwName(String name) {
        prefs.edit().putString(KEY_TARGET_HW_NAME, name).apply();
    }

    // Retrieve the stored target hardware name (returns default if none saved)
    public String getTargetHwName() {
        return prefs.getString(KEY_TARGET_HW_NAME, DEFAULT_HW_NAME);
    }

    public void saveTargetMacAddress(String mac) {
        prefs.edit().putString(KEY_TARGET_MAC, mac).apply();
    }

    public String getTargetMacAddress() {
        // Fallback to your BuildConfig or a default string if nothing is saved yet
        return prefs.getString(KEY_TARGET_MAC, BuildConfig.ESP32_MAC);
    }

    /**
     * Resolves active pulse duration string for a given action.
     */
    public String getFormattedCommand(String action) {
        int selectedPosition = getStartSpinnerPosition();

        // Safeguard against out-of-bounds index
        if (selectedPosition < 0 || selectedPosition >= DurationOption.values().length) {
            return action;
        }

        DurationOption selectedOption = DurationOption.values()[selectedPosition];

        switch (selectedOption) {
            case MS_800:
            case MS_1700:
            case MS_2200:
                // Dynamically uses whatever millisecond value is mapped in the enum
                return action + ":" + selectedOption.getValueMs();

            case CUSTOM:
                String customVal = getStartCustomMs();
                String trimmed = customVal != null ? customVal.trim() : "";
                return !trimmed.isEmpty() ? action + ":" + trimmed : action;

            default:
                return action;
        }
    }

    public long getSelectedStartPulseDuration() {
        int selectedPosition = getStartSpinnerPosition();

        // Safeguard against out-of-bounds index
        if (selectedPosition < 0 || selectedPosition >= DurationOption.values().length) {
            return -1;
        }

        DurationOption selectedOption = DurationOption.values()[selectedPosition];

        switch (selectedOption) {
            case MS_800:
            case MS_1700:
            case MS_2200:
                // Dynamically uses whatever millisecond value is mapped in the enum
                return selectedOption.getValueMs();

            case CUSTOM:
                String customInput = getStartCustomMs();
                try {
                    return Long.parseLong(customInput);
                } catch (NumberFormatException e) {
                    return DEFAULT_START_MS;
                }

            default:
                return DEFAULT_START_MS;
        }
    }

    /**
     * Syncs configuration map to all connected Wear OS devices via DataClient.
     */
    public void syncToWearables() {
        PutDataMapRequest dataMap = PutDataMapRequest.create("/config_durations");
        dataMap.getDataMap().putString("START_CMD", getFormattedCommand("START"));

        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.getDataClient(context).putDataItem(request);
    }
}