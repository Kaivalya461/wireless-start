package in.kvapps.wirelessstart.data;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREFS_NAME = "WirelessStartPrefs";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";
    private static final String KEY_TARGET_AUTO_CONNECT = "target_auto_connect";
    private static final String KEY_FOREGROUND_SERVICE = "pref_foreground_service";
    private static final String KEY_ENGINE_SCHEDULE_RUN_TIME = "pref_engine_scheduled_run_time";
    private static final String KEY_ENGINE_SCHEDULE_ENABLED = "pref_engine_schedule_enabled";
    private static final String KEY_AVOID_POOR_CONN = "key_avoid_poor_connection";

    private final SharedPreferences prefs;
    private final Context context;

    public PreferenceManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Existing Pulse, Telemetry, and Service Methods ---
    public void setTelemetryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled).apply();
    }

    public boolean isTelemetryEnabled() {
        return prefs.getBoolean(KEY_TELEMETRY_ENABLED, false);
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

    public boolean isAvoidPoorConnectionEnabled() {
        return prefs.getBoolean(KEY_AVOID_POOR_CONN, false);
    }

    public void setAvoidPoorConnectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AVOID_POOR_CONN, enabled).apply();
    }

    public void saveLastSeenTimestamp(String macAddress, long timestamp) {
        if (macAddress == null) return;
        prefs.edit().putLong("key_last_seen_" + macAddress.toUpperCase(), timestamp).apply();
    }

    public long getLastSeenTimestamp(String macAddress) {
        if (macAddress == null) return 0;
        return prefs.getLong("key_last_seen_" + macAddress.toUpperCase(), 0);
    }
}