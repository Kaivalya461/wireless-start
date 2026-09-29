package in.kvapps.wirelessstart.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import in.kvapps.wirelessstart.BuildConfig;

public class PreferenceManager {
    private static final String PREFS_NAME = "WirelessStartPrefs";
    private static final String KEY_START_PULSE_DURATION = "start_pulse_duration_ms";
    private static final String KEY_STOP_PULSE_DURATION = "stop_pulse_duration_ms";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";
    private static final String KEY_TARGET_HW_NAME = "target_hw_name";
    private static final String DEFAULT_HW_NAME = "Vehicle 001";
    private static final String KEY_TARGET_MAC = "target_mac_address";
    private static final String KEY_TARGET_AUTO_CONNECT = "target_auto_connect";
    private static final String KEY_FOREGROUND_SERVICE = "pref_foreground_service";
    private static final String KEY_ENGINE_SCHEDULE_RUN_TIME = "pref_engine_scheduled_run_time";
    private static final String KEY_ENGINE_SCHEDULE_ENABLED = "pref_engine_schedule_enabled";
    private static final long DEFAULT_START_MS = 1500;
    private static final long DEFAULT_STOP_MS = 2500;

    private final SharedPreferences prefs;
    private final Context context;

    public PreferenceManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveStartPulseDuration(long durationMs) {
        prefs.edit().putLong(KEY_START_PULSE_DURATION, durationMs).apply();
        syncToWearables();
    }

    public long getStartPulseDuration() {
        return prefs.getLong(KEY_START_PULSE_DURATION, DEFAULT_START_MS);
    }

    public void saveStopPulseDuration(long durationMs) {
        prefs.edit().putLong(KEY_STOP_PULSE_DURATION, durationMs).apply();
        syncToWearables();
    }

    public long getStopPulseDuration() {
        return prefs.getLong(KEY_STOP_PULSE_DURATION, DEFAULT_STOP_MS);
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
        boolean isStart = "START".equalsIgnoreCase(action);
        long duration = isStart ? getStartPulseDuration() : getStopPulseDuration();
        return action + ":" + duration;
    }

    public long getSelectedPulseDuration(String action) {
        boolean isStart = "START".equalsIgnoreCase(action);
        return isStart ? getStartPulseDuration() : getStopPulseDuration();
    }

    public long getSelectedStartPulseDuration() {
        return getSelectedPulseDuration("START");
    }

    public long getSelectedStopPulseDuration() {
        return getSelectedPulseDuration("STOP");
    }

    /**
     * Syncs configuration map to all connected Wear OS devices via DataClient.
     */
    public void syncToWearables() {
        PutDataMapRequest dataMap = PutDataMapRequest.create("/config_durations");
        dataMap.getDataMap().putString("START_CMD", getFormattedCommand("START"));
        dataMap.getDataMap().putString("STOP_CMD", getFormattedCommand("STOP"));

        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.getDataClient(context).putDataItem(request);
    }

    public boolean isAutoConnectEnabled() {
        return prefs.getBoolean(KEY_TARGET_AUTO_CONNECT, false); // Default to false
    }

    public void setAutoConnectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TARGET_AUTO_CONNECT, enabled).apply();
    }

    public boolean isFailSafeEnabled() {
        return prefs.getBoolean("fail_safe_enabled", false); // Default to false
    }

    public void setFailSafeEnabled(boolean enabled) {
        prefs.edit().putBoolean("fail_safe_enabled", enabled).apply();
    }

    public boolean isForegroundServiceEnabled() {
        // Default to true if you want it active by default, or false otherwise
        return prefs.getBoolean(KEY_FOREGROUND_SERVICE, false);
    }

    public void setForegroundServiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FOREGROUND_SERVICE, enabled).apply();
    }

    public long getEngineScheduledRunTime() {
        return prefs.getLong(KEY_ENGINE_SCHEDULE_RUN_TIME, 0);
    }

    public void saveEngineScheduledRunTime(long epochTime) {
        prefs.edit().putLong(KEY_ENGINE_SCHEDULE_RUN_TIME, epochTime).apply();
    }

    public boolean isEngineScheduleEnabled() {
        return prefs.getBoolean(KEY_ENGINE_SCHEDULE_ENABLED, false); // Default to false
    }

    public void setEngineScheduleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENGINE_SCHEDULE_ENABLED, enabled).apply();
    }
}