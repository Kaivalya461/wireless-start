package in.kvapps.wirelessstart.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;

import java.util.List;

import in.kvapps.wirelessstart.dao.CommandLogDao;
import in.kvapps.wirelessstart.db.AppDatabase;
import in.kvapps.wirelessstart.model.CommandLogEntity;
import in.kvapps.wirelessstart.shared.Constants;

public class CommandLogsRepository {
    private static volatile CommandLogsRepository instance;
    private final CommandLogDao commandLogDao;

    private CommandLogsRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.commandLogDao = db.commandLogDao();
    }

    public static synchronized CommandLogsRepository getInstance(Context context) {
        if (instance == null) {
            instance = new CommandLogsRepository(context.getApplicationContext());
        }
        return instance;
    }

    public void insertLog(String macAddress, String command, String result, long timestamp, long timeTaken) {
        CommandLogEntity log = new CommandLogEntity(macAddress, command, result, timestamp, timeTaken);
        // Since your AppDatabase uses allowMainThreadQueries(), you can call directly,
        // or wrap in an executor if you want off-main-thread database writes.
        commandLogDao.insert(log);
    }

    public LiveData<List<CommandLogEntity>> getLogsForVehicle(String macAddress) {
        return commandLogDao.getLogsForVehicle(macAddress);
    }

    public void clearLogsForVehicle(String macAddress) {
        commandLogDao.clearLogsForVehicle(macAddress);
    }

    /**
     * Automatically prunes/deletes command logs older than 30 days.
     */
    public void pruneOldLogs() {
        long thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000L;
        long cutoffTimestamp = System.currentTimeMillis() - thirtyDaysInMillis;

        // Since allowMainThreadQueries() is enabled in your AppDatabase,
        // this can be safely called directly.
        commandLogDao.deleteLogsOlderThan(cutoffTimestamp);
    }

    public CommandLogEntity getLatestSuccessfulLog(String macAddress) {
        return commandLogDao.getLatestSuccessfulLog(macAddress, Constants.COMMAND_SUCCESS);
    }
}