package in.kvapps.wirelessstart.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleWithSettings;

@Dao
public interface VehicleDao {
    @Query("SELECT * FROM vehicles")
    List<Vehicle> getAllVehicles();

    @Query("SELECT * FROM vehicles WHERE id = :id")
    Vehicle getVehicleById(String id);

    @Query("SELECT * FROM vehicles WHERE macAddress = :macAddress")
    Vehicle getVehicleByMac(String macAddress);

    @Query("SELECT * FROM vehicles WHERE is_default = 1 LIMIT 1")
    Vehicle getDefaultVehicle();

    @Query("UPDATE vehicles SET is_default = 0")
    void clearAllDefaults();

    @Query("UPDATE vehicles SET is_default = 1 WHERE id = :vehicleId")
    void setVehicleAsDefault(String vehicleId);

    @Transaction
    default void switchDefaultVehicle(String vehicleId) {
        clearAllDefaults();
        setVehicleAsDefault(vehicleId);
    }

    @Query("SELECT macAddress FROM vehicles WHERE macAddress IS NOT NULL AND macAddress != ''")
    List<String> getAllMacAddresses();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVehicle(Vehicle vehicle);

    @Update
    void updateVehicle(Vehicle vehicle);

    @Query("DELETE FROM vehicles WHERE id = :id")
    void deleteVehicle(String id);

    @Query("SELECT COUNT(*) FROM vehicles")
    int getVehicleCount();

    @Transaction
    @Query("SELECT * FROM vehicles")
    LiveData<List<VehicleWithSettings>> getAllVehiclesWithSettings();
}