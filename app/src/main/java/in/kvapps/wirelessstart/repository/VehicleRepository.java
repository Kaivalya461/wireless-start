package in.kvapps.wirelessstart.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.kvapps.wirelessstart.db.AppDatabase;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.model.VehicleWithSettings; // Import your combined model
import in.kvapps.wirelessstart.util.VehicleUtils;

public class VehicleRepository {
    private final AppDatabase db;

    public VehicleRepository(Context context) {
        db = AppDatabase.getInstance(context);
    }

    public List<Vehicle> getAllVehicles() {
        return db.vehicleDao().getAllVehicles();
    }

    /**
     * Fetches all vehicles and enriches them with their live BLE connection states.
     */
    public List<Vehicle> getAllVehiclesWithLiveState(String tag) {
        List<Vehicle> vehicles = db.vehicleDao().getAllVehicles();
        return VehicleUtils.enrichVehiclesWithLiveState(tag, vehicles);
    }

    /**
     * Fetches vehicles with settings as LiveData, and enriches them with live BLE connection states.
     */
    public LiveData<List<VehicleWithSettings>> getAllVehiclesWithSettingsAndLiveState(String tag) {
        LiveData<List<VehicleWithSettings>> rawLiveData = db.vehicleDao().getAllVehiclesWithSettings();

        return Transformations.map(rawLiveData, vehicleWithSettingsList -> {
            // Enriches the transient fields (isConnected, statusText) on the fly
            return VehicleUtils.enrichVehiclesWithSettingsLiveState(tag, vehicleWithSettingsList);
        });
    }

    /**
     * Fetches a specific vehicle by its ID.
     */
    public Vehicle getVehicleById(String vehicleId) {
        return db.vehicleDao().getVehicleById(vehicleId);
    }

    /**
     * Fetches all vehicle settings mapped by their vehicleId for quick lookup.
     */
    public Map<String, VehicleSettings> getAllVehicleSettingsMap() {
        List<VehicleSettings> settingsList = db.vehicleSettingsDao().getAllSettings();

        Map<String, VehicleSettings> settingsMap = new HashMap<>();
        if (settingsList != null) {
            for (VehicleSettings setting : settingsList) {
                settingsMap.put(setting.getVehicleId(), setting);
            }
        }
        return settingsMap;
    }

    /**
     * Fetches settings for a specific vehicle.
     */
    public VehicleSettings getSettingsForVehicle(String vehicleId) {
        return db.vehicleSettingsDao().getSettingsForVehicle(vehicleId);
    }

    // Handles adding vehicle + default settings together cleanly
    public String addVehicleWithSettings(String name, String mac, int defaultStartMs, int defaultStopMs) {
        String vehicleId = String.valueOf(System.currentTimeMillis());

        Vehicle vehicle = new Vehicle(vehicleId, name, mac);
        db.vehicleDao().insertVehicle(vehicle);

        VehicleSettings settings = new VehicleSettings(vehicleId, defaultStartMs, defaultStopMs);
        db.vehicleSettingsDao().insertSettings(settings);

        return vehicleId;
    }

    // Handles updating vehicle settings
    public void saveVehicleSettings(String vehicleId, int startMs, int stopMs) {
        VehicleSettings settings = db.vehicleSettingsDao().getSettingsForVehicle(vehicleId);
        if (settings == null) {
            settings = new VehicleSettings(vehicleId, startMs, stopMs);
            db.vehicleSettingsDao().insertSettings(settings);
        } else {
            settings.setStartPulseDuration(startMs);
            settings.setStopPulseDuration(stopMs);
            db.vehicleSettingsDao().updateSettings(settings);
        }
    }

    public void saveVehicleConfigSettings(String vehicleId, boolean startEn, boolean stopEn) {
        VehicleSettings settings = db.vehicleSettingsDao().getSettingsForVehicle(vehicleId);
        if (settings != null) {
            settings.setStartEngineEnabled(startEn);
            settings.setStopEngineEnabled(stopEn);
            db.vehicleSettingsDao().updateSettings(settings);
        }
    }

    public void deleteVehicle(Vehicle vehicle) {
        // Room cascade delete handles the settings automatically
        db.vehicleDao().deleteVehicle(vehicle.getId());
    }
}