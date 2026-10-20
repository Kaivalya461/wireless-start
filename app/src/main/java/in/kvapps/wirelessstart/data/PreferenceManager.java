package in.kvapps.wirelessstart.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.UUID;

import in.kvapps.wirelessstart.BuildConfig;
import in.kvapps.wirelessstart.dao.VehicleDao;
import in.kvapps.wirelessstart.db.AppDatabase;
import in.kvapps.wirelessstart.model.Vehicle;

public class PreferenceManager {
    private static final String PREFS_NAME = "WirelessStartPrefs";
    private static final String KEY_START_PULSE_DURATION = "start_pulse_duration_ms";
    private static final String KEY_STOP_PULSE_DURATION = "stop_pulse_duration_ms";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";
    private static final String KEY_TARGET_AUTO_CONNECT = "target_auto_connect";
    private static final String KEY_FOREGROUND_SERVICE = "pref_foreground_service";
    private static final String KEY_ENGINE_SCHEDULE_RUN_TIME = "pref_engine_scheduled_run_time";
    private static final String KEY_ENGINE_SCHEDULE_ENABLED = "pref_engine_schedule_enabled";
    private static final String KEY_AVOID_POOR_CONN = "key_avoid_poor_connection";
    private static final String KEY_SELECTED_VEHICLE_ID = "key_selected_vehicle_id";

    private static final long DEFAULT_START_MS = 1500;
    private static final long DEFAULT_STOP_MS = 2500;

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

    public void addVehicle(String name, String macAddress) {
        String id = UUID.randomUUID().toString();
        Vehicle vehicle = new Vehicle(id, name, macAddress);
        vehicleDao.insertVehicle(vehicle);

        // If no vehicle was selected, select this one
        if (getSelectedVehicleId() == null) {
            setSelectedVehicleId(id);
        }
    }

    public void updateVehicle(String id, String name, String macAddress) {
        Vehicle vehicle = new Vehicle(id, name, macAddress);
        vehicleDao.updateVehicle(vehicle);
    }

    public void deleteVehicle(String id) {
        vehicleDao.deleteVehicle(id);

        // If the deleted vehicle was the active one, switch to another if available
        if (id.equals(getSelectedVehicleId())) {
            List<Vehicle> remaining = getVehicles();
            if (!remaining.isEmpty()) {
                setSelectedVehicleId(remaining.get(0).getId());
            } else {
                setSelectedVehicleId(null);
            }
        }
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

    // --- Backward-Compatible Getters/Setters targeting Active Vehicle ---
    public String getTargetHwName() {
        Vehicle active = getSelectedVehicle();
        return active != null ? active.getName() : "Vehicle 001";
    }

    public void saveTargetHwName(String name) {
        Vehicle active = getSelectedVehicle();
        if (active != null) {
            updateVehicle(active.getId(), name, active.getMacAddress());
        }
    }

    public String getTargetMacAddress() {
        Vehicle active = getSelectedVehicle();
        return active != null ? active.getMacAddress() : BuildConfig.ESP32_MAC;
    }

    public void saveTargetMacAddress(String mac) {
        Vehicle active = getSelectedVehicle();
        if (active != null) {
            updateVehicle(active.getId(), active.getName(), mac);
        }
    }

    // --- Existing Pulse, Telemetry, and Service Methods ---
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

        Vehicle active = getSelectedVehicle();
        if (active != null) {
            dataMap.getDataMap().putString("ACTIVE_VEHICLE_NAME", active.getName());
            dataMap.getDataMap().putString("ACTIVE_VEHICLE_MAC", active.getMacAddress());
        }

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

    public boolean isAvoidPoorConnectionEnabled() {
        return prefs.getBoolean(KEY_AVOID_POOR_CONN, false);
    }

    public void setAvoidPoorConnectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AVOID_POOR_CONN, enabled).apply();
    }
}