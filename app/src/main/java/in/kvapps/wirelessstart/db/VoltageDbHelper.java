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
    private static final int DATABASE_VERSION = 2; // Incremented version for schema update

    // Voltage Table Constants
    private static final String TABLE_VOLTAGE = "voltage_history";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_VALUE = "voltage_value";

    // Logs Table Constants
    private static final String TABLE_LOGS = "app_logs";
    private static final String COLUMN_LOG_TIMESTAMP = "timestamp";
    private static final String COLUMN_LOG_MESSAGE = "message";

    public VoltageDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create voltage table
        String createVoltageTable = "CREATE TABLE " + TABLE_VOLTAGE + " (" +
                COLUMN_TIMESTAMP + " INTEGER PRIMARY KEY, " +
                COLUMN_VALUE + " REAL)";
        db.execSQL(createVoltageTable);

        // Create logs table
        String createLogsTable = "CREATE TABLE " + TABLE_LOGS + " (" +
                COLUMN_LOG_TIMESTAMP + " INTEGER, " +
                COLUMN_LOG_MESSAGE + " TEXT)";
        db.execSQL(createLogsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple upgrade strategy: drop and recreate tables (or handle migration logic if preserving data is critical)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VOLTAGE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    // --- Voltage Operations ---

    public void insertReading(long timestamp, float voltage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_VALUE, voltage);

        db.insertWithOnConflict(TABLE_VOLTAGE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

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

    public List<VoltageEntry> getAveragesSince(long cutoffTimestamp, long intervalMs) {
        List<VoltageEntry> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

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

    // --- Log Operations ---

    // Insert a new log message into the database
    public void insertLog(long timestamp, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LOG_TIMESTAMP, timestamp);
        values.put(COLUMN_LOG_MESSAGE, message);

        db.insert(TABLE_LOGS, null, values);
    }

    // Optional: Retrieve logs since a specific cutoff timestamp
    public List<String> getLogsSince(long cutoffTimestamp) {
        List<String> logsList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + COLUMN_LOG_MESSAGE + " FROM " + TABLE_LOGS +
                " WHERE " + COLUMN_LOG_TIMESTAMP + " >= ? ORDER BY " + COLUMN_LOG_TIMESTAMP + " ASC";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(cutoffTimestamp)});

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        String message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOG_MESSAGE));
                        logsList.add(message);
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        return logsList;
    }

    // Retrieve all log messages generated starting from today's midnight
    public List<String> getTodayLogs() {
        List<String> logsList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Calculate today's midnight timestamp (00:00:00)
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        long startOfDayTimestamp = calendar.getTimeInMillis();

        String query = "SELECT " + COLUMN_LOG_TIMESTAMP + ", " + COLUMN_LOG_MESSAGE +
                " FROM " + TABLE_LOGS +
                " WHERE " + COLUMN_LOG_TIMESTAMP + " >= ? " +
                " ORDER BY " + COLUMN_LOG_TIMESTAMP + " ASC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startOfDayTimestamp)});

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LOG_TIMESTAMP));
                    String message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOG_MESSAGE));

                    // Format timestamp back to human-readable string and append
                    String timeStampFormatted = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(timestamp));
                    logsList.add("[" + timeStampFormatted + "] " + message);
                }
            } finally {
                cursor.close();
            }
        }
        return logsList;
    }

    public List<String> getLogsForDate(String targetDate) {
        List<String> logsList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        long startOfDayTimestamp = 0;
        long endOfDayTimestamp = 0;

        try {
            // Parse the targetDate string ("yyyy-MM-dd") to get start and end timestamps
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date parsedDate = sdf.parse(targetDate);

            if (parsedDate != null) {
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.setTime(parsedDate);

                // Set to 00:00:00.000
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
                calendar.set(java.util.Calendar.MINUTE, 0);
                calendar.set(java.util.Calendar.SECOND, 0);
                calendar.set(java.util.Calendar.MILLISECOND, 0);
                startOfDayTimestamp = calendar.getTimeInMillis();

                // Set to 23:59:59.999
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
                calendar.set(java.util.Calendar.MINUTE, 59);
                calendar.set(java.util.Calendar.SECOND, 59);
                calendar.set(java.util.Calendar.MILLISECOND, 999);
                endOfDayTimestamp = calendar.getTimeInMillis();
            }
        } catch (java.text.ParseException e) {
            e.printStackTrace();
            return logsList; // Return empty list if parsing fails
        }

        // Query logs falling within that day's time range
        String query = "SELECT " + COLUMN_LOG_TIMESTAMP + ", " + COLUMN_LOG_MESSAGE +
                " FROM " + TABLE_LOGS +
                " WHERE " + COLUMN_LOG_TIMESTAMP + " >= ? AND " + COLUMN_LOG_TIMESTAMP + " <= ? " +
                " ORDER BY " + COLUMN_LOG_TIMESTAMP + " ASC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startOfDayTimestamp), String.valueOf(endOfDayTimestamp)});

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LOG_TIMESTAMP));
                    String message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOG_MESSAGE));

                    // Format timestamp back to human-readable string and append
                    String timeStampFormatted = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(timestamp));
                    logsList.add("[" + timeStampFormatted + "] " + message);
                }
            } finally {
                cursor.close();
            }
        }
        return logsList;
    }
}
