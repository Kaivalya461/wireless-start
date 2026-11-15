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

    // Delete logs older than a specific timestamp (e.g., 30 days ago)
    @Query("DELETE FROM command_logs WHERE timestamp < :cutoffTimestamp")
    void deleteLogsOlderThan(long cutoffTimestamp);

    @Query("SELECT * FROM command_logs WHERE macAddress = :macAddress AND result = :successResult ORDER BY timestamp DESC LIMIT 1")
    CommandLogEntity getLatestSuccessfulLog(String macAddress, String successResult);
}