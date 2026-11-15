package in.kvapps.wirelessstart.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import in.kvapps.wirelessstart.model.CommandLogEntity;

@Dao
public interface CommandLogDao {
    @Insert
    void insert(CommandLogEntity log);

    @Query("SELECT * FROM command_logs WHERE macAddress = :macAddress ORDER BY timestamp DESC")
    LiveData<List<CommandLogEntity>> getLogsForVehicle(String macAddress);

    @Query("DELETE FROM command_logs WHERE macAddress = :macAddress")
    void clearLogsForVehicle(String macAddress);
}