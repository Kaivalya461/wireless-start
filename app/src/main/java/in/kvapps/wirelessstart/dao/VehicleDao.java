package in.kvapps.wirelessstart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import in.kvapps.wirelessstart.model.Vehicle;

@Dao
public interface VehicleDao {
    @Query("SELECT * FROM vehicles")
    List<Vehicle> getAllVehicles();

    @Query("SELECT * FROM vehicles WHERE id = :id")
    Vehicle getVehicleById(String id);

    @Query("SELECT * FROM vehicles WHERE macAddress = :macAddress")
    Vehicle getVehicleByMac(String macAddress);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVehicle(Vehicle vehicle);

    @Update
    void updateVehicle(Vehicle vehicle);

    @Query("DELETE FROM vehicles WHERE id = :id")
    void deleteVehicle(String id);

    @Query("SELECT COUNT(*) FROM vehicles")
    int getVehicleCount();
}