package in.kvapps.wirelessstart.util;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.db.VoltageDbHelper;

public class AppLogger {
    private static final String SOFT_RED = "#FF6B6B";
    private static final String SOFT_GOLD = "#F4D03F";
    private static final String LIGHT_BLUE = "#00E5FF";

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

    public static SpannableString formatLogLine(String logLine) {
        SpannableString spannableLine = new SpannableString(logLine);

        // Apply Soft Red for safety/errors/denied
        if (logLine.contains("System Alert") || logLine.contains("FAILURE") || logLine.contains("Error")) {
            spannableLine.setSpan(
                    new ForegroundColorSpan(Color.parseColor(SOFT_RED)),
                    0,
                    logLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        // Apply Soft Golden for Command Results
        else if (logLine.contains("TimeTaken")) {
            spannableLine.setSpan(
                    new ForegroundColorSpan(Color.parseColor(SOFT_GOLD)),
                    0,
                    logLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        // Apply Soft Golden for Command Results
        else if (logLine.contains("Ready")) {
            spannableLine.setSpan(
                    new ForegroundColorSpan(Color.parseColor(LIGHT_BLUE)),
                    0,
                    logLine.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return spannableLine;
    }
}