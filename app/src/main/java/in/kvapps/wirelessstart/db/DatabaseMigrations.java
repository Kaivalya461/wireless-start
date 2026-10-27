package in.kvapps.wirelessstart.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DatabaseMigrations {

    // Migration from version 1 to version 2 (adds the vehicle_settings table)
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `vehicle_settings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`vehicleId` TEXT NOT NULL, " + // Changed INTEGER to TEXT
                    "`startPulseDuration` INTEGER NOT NULL, " +
                    "`stopPulseDuration` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON DELETE CASCADE)");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_settings_vehicleId` ON `vehicle_settings` (`vehicleId`)");
        }
    };
}