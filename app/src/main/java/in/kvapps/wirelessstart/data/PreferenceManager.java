package in.kvapps.wirelessstart.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import in.kvapps.wirelessstart.BuildConfig;
import in.kvapps.wirelessstart.dao.VehicleDao;
import in.kvapps.wirelessstart.db.AppDatabase;
import in.kvapps.wirelessstart.model.Vehicle;

public class PreferenceManager {
    private static final String PREFS_NAME = "WirelessStartPrefs";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";
    private static final String KEY_TARGET_AUTO_CONNECT = "target_auto_connect";
    private static final String KEY_FOREGROUND_SERVICE = "pref_foreground_service";
    private static final String KEY_ENGINE_SCHEDULE_RUN_TIME = "pref_engine_scheduled_run_time";
    private static final String KEY_ENGINE_SCHEDULE_ENABLED = "pref_engine_schedule_enabled";
    private static final String KEY_AVOID_POOR_CONN = "key_avoid_poor_connection";
    private static final String KEY_SELECTED_VEHICLE_ID = "key_selected_vehicle_id";

    private final SharedPreferences prefs;
    private final Context context;
    private final VehicleDao vehicleDao;

    public PreferenceManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.vehicleDao = AppDatabase.getInstance(context).vehicleDao();

        // Migration / First-run check: Ensure at least one vehicle exists
        initializeDefaultVehicleIfNeeded();
    }

    private void initializeDefaultVehicleIfNeeded() {
        if (vehicleDao.getVehicleCount() == 0) {
            String defaultId = UUID.randomUUID().toString();
            String defaultName = "Vehicle 001";
            String defaultMac = BuildConfig.ESP32_MAC;

            Vehicle defaultVehicle = new Vehicle(defaultId, defaultName, defaultMac);
            vehicleDao.insertVehicle(defaultVehicle);
            setSelectedVehicleId(defaultId);
        }
    }

    // --- Vehicle Management (Delegated to Room) ---

    public List<Vehicle> getVehicles() {
        return vehicleDao.getAllVehicles();
    }

    public void setSelectedVehicleId(String vehicleId) {
        prefs.edit().putString(KEY_SELECTED_VEHICLE_ID, vehicleId).apply();
    }

    public String getSelectedVehicleId() {
        return prefs.getString(KEY_SELECTED_VEHICLE_ID, null);
    }

    public Vehicle getSelectedVehicle() {
        String selectedId = getSelectedVehicleId();
        if (selectedId != null) {
            Vehicle vehicle = vehicleDao.getVehicleById(selectedId);
            if (vehicle != null) return vehicle;
        }
        // Fallback to first available vehicle
        List<Vehicle> vehicles = getVehicles();
        return vehicles.isEmpty() ? null : vehicles.get(0);
    }

    public Vehicle getVehicleByMac(String macAddress) {
        if (macAddress == null) return null;
        return vehicleDao.getVehicleByMac(macAddress);
    }

    // --- Multi-Vehicle Support Getters ---

    /**
     * Returns a list of all target MAC addresses from the saved vehicles database
     * so that BleScanManager can scan for all vehicles simultaneously.
     */
    public List<String> getTargetMacAddresses() {
        List<Vehicle> vehicles = getVehicles();
        List<String> macs = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.getMacAddress() != null && !v.getMacAddress().trim().isEmpty()) {
                macs.add(v.getMacAddress().trim());
            }
        }
        // Fallback to build config if list is empty
        if (macs.isEmpty() && BuildConfig.ESP32_MAC != null) {
            macs.add(BuildConfig.ESP32_MAC);
        }
        return macs;
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