package in.kvapps.wirelessstart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import in.kvapps.wirelessstart.model.VehicleSettings;

@Dao
public interface VehicleSettingsDao {

    // Get settings for a specific vehicle by its vehicleId (Changed long to String)
    @Query("SELECT * FROM vehicle_settings WHERE vehicleId = :vehicleId")
    VehicleSettings getSettingsForVehicle(String vehicleId);

    // Insert new settings (replaces if it already exists for that ID)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSettings(VehicleSettings settings);

    // Update existing settings
    @Update
    void updateSettings(VehicleSettings settings);

    // Delete settings by vehicleId (Changed long to String)
    @Query("DELETE FROM vehicle_settings WHERE vehicleId = :vehicleId")
    void deleteSettingsForVehicle(String vehicleId);

    @Query("SELECT * FROM vehicle_settings")
    List<VehicleSettings> getAllSettings();
}