package in.kvapps.wirelessstart.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

import in.kvapps.wirelessstart.model.VoltageEntry;

public class VoltageDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "telemetry.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_VOLTAGE = "voltage_history";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_VALUE = "voltage_value";

    public VoltageDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_VOLTAGE + " (" +
                COLUMN_TIMESTAMP + " INTEGER PRIMARY KEY, " +
                COLUMN_VALUE + " REAL)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VOLTAGE);
        onCreate(db);
    }

    // 1. Write telemetry data safely to the database disk storage profile
    public void insertReading(long timestamp, float voltage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_VALUE, voltage);

        // Use CONFLICT_REPLACE to handle identical rapid clock cycles safely
        db.insertWithOnConflict(TABLE_VOLTAGE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // 2. Query data filtered by age (e.g. past 24 hours, past 7 days)
    public List<VoltageEntry> getReadingsSince(long cutoffTimestamp) {
        List<VoltageEntry> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_VOLTAGE + " WHERE " + COLUMN_TIMESTAMP + " >= ? ORDER BY " + COLUMN_TIMESTAMP + " ASC";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(cutoffTimestamp)});

        if (cursor.moveToFirst()) {
            do {
                long time = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                float volt = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_VALUE));
                list.add(new VoltageEntry(time, volt));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // Calculates the average voltage grouped by any custom time interval (in milliseconds)
    public List<VoltageEntry> getAveragesSince(long cutoffTimestamp, long intervalMs) {
        List<VoltageEntry> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // SQL groups all timestamps falling within the same bucket interval together and averages their voltages
        String query = "SELECT " +
                "((timestamp / " + intervalMs + ") * " + intervalMs + ") AS time_bucket, " +
                "AVG(" + COLUMN_VALUE + ") AS avg_voltage " +
                "FROM " + TABLE_VOLTAGE + " " +
                "WHERE " + COLUMN_TIMESTAMP + " >= ? " +
                "GROUP BY time_bucket " +
                "ORDER BY time_bucket ASC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(cutoffTimestamp)});

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        long bucketTime = cursor.getLong(cursor.getColumnIndexOrThrow("time_bucket"));
                        float avgVolt = cursor.getFloat(cursor.getColumnIndexOrThrow("avg_voltage"));
                        list.add(new VoltageEntry(bucketTime, avgVolt));
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

}
