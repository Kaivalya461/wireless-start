package in.kvapps.wirelessstart.util;

import android.content.Context;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.db.VoltageDbHelper;

public class AppLogger {

    public static void logToDatabaseAndLogcat(Context context, String tag, String message) {
        long currentTime = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(currentTime));

        // 1. Standard Logcat output
        Log.d(tag, "[" + timeStamp + "] " + message);

        // 2. Persistent Database storage (Works whether UI is open or closed)
        try (VoltageDbHelper dbHelper = new VoltageDbHelper(context)) {
            dbHelper.insertLog(currentTime, message);
        } catch (Exception e) {
            Log.e(tag, "Failed to write log to database: " + e.getMessage());
        }
    }
}