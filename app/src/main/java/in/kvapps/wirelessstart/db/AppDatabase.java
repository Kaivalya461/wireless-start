package in.kvapps.wirelessstart.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import in.kvapps.wirelessstart.dao.VehicleDao;
import in.kvapps.wirelessstart.model.Vehicle;

@Database(entities = {Vehicle.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract VehicleDao vehicleDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "wireless_start_db"
                            )
                            .allowMainThreadQueries() // Allows synchronous calls for simple settings management
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}