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
    public static final String KEY_CONNECTION_THRESHOLD = "key_connection_threshold";
    private static final String KEY_AUTO_DEFAULT_VEHICLE = "pref_auto_default_vehicle";
    private static final String KEY_WATCH_SYNC = "pref_watch_sync";

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

    public void saveLastSeenTimestamp(String macAddress, long timestamp) {
        if (macAddress == null) return;
        prefs.edit().putLong("key_last_seen_" + macAddress.toUpperCase(), timestamp).apply();
    }

    public long getLastSeenTimestamp(String macAddress) {
        if (macAddress == null) return 0;
        return prefs.getLong("key_last_seen_" + macAddress.toUpperCase(), 0);
    }

    public int getConnectionThreshold() {
        // Default to 1, Auto-Connect Signal Threshold to Medium Range.
        return prefs.getInt(KEY_CONNECTION_THRESHOLD, 1);
    }

    public void setConnectionThreshold(int threshold) {
        prefs.edit().putInt(KEY_CONNECTION_THRESHOLD, threshold).apply();
    }

    public boolean isAutoDefaultVehicleEnabled() {
        return prefs.getBoolean(KEY_AUTO_DEFAULT_VEHICLE, false);
    }

    public void setAutoDefaultVehicleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_DEFAULT_VEHICLE, enabled).apply();
    }

    public boolean isWatchSyncEnabled() {
        return prefs.getBoolean("KEY_WATCH_SYNC", false);
    }

    public void setWatchSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean("KEY_WATCH_SYNC", enabled).apply();
    }
}