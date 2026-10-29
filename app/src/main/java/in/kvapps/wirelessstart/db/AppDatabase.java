package in.kvapps.wirelessstart.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import in.kvapps.wirelessstart.dao.VehicleDao;
import in.kvapps.wirelessstart.dao.VehicleSettingsDao;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;

@Database(entities = {Vehicle.class, VehicleSettings.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract VehicleDao vehicleDao();
    public abstract VehicleSettingsDao vehicleSettingsDao();

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
                            .addMigrations(
                                    DatabaseMigrations.MIGRATION_1_2,
                                    DatabaseMigrations.MIGRATION_2_3,
                                    DatabaseMigrations.MIGRATION_3_4
                            )
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}