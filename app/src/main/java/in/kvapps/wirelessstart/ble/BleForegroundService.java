package in.kvapps.wirelessstart.ble;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import in.kvapps.wirelessstart.MainActivity;
import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.shared.Constants;

public class BleForegroundService extends Service {

    private static final String TAG = "BleForegroundService";
    private static final String CHANNEL_ID = "ble_foreground_channel";
    private static final String ACTION_FORCE_STOP = "in.kvapps.wirelessstart.ACTION_FORCE_STOP";
    private static final String ACTION_WATCHDOG_TICK = "in.kvapps.wirelessstart.ACTION_WATCHDOG_TICK";

    // Watchdog interval: Check connection status every 15 minutes
    private static final long WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_FORCE_STOP.equals(intent.getAction())) {
                forceStopApp();
                return START_NOT_STICKY;
            } else if (ACTION_WATCHDOG_TICK.equals(intent.getAction())) {
                // Triggered periodically by AlarmManager (survives Doze mode)
                performWatchdogCheck();
                scheduleNextWatchdogAlarm();
                return START_STICKY;
            }
        }

        // 1. MANDATORY: Call startForeground immediately to satisfy Android's OS policy
        Notification notification = createNotification("Background BLE Service Active.", true);
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification);

        // 2. Trigger initial connection/scanning
        triggerInitialConnection();

        // 3. Schedule the first Doze-safe background watchdog alarm
        scheduleNextWatchdogAlarm();

        return START_STICKY;
    }

    private void scheduleNextWatchdogAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, BleForegroundService.class);
        intent.setAction(ACTION_WATCHDOG_TICK);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getService(this, 2002, intent, pendingFlags);

        if (alarmManager != null) {
            long triggerAtMillis = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Check if exact alarms are allowed by the system/user
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    } else {
                        // Permission missing: Fallback to inexact alarm and notify user via notification text
//                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                        updateForegroundNotification("Warning: Exact Alarm permission missing. Scan not running!!!", false);
                        Log.w(TAG, "Exact Alarm permission (SCHEDULE_EXACT_ALARM) is not granted.");
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while scheduling exact alarm: " + e.getMessage());
                updateForegroundNotification("Warning: Exact Alarm permission missing. Scan not running!!!", false);
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } catch (Exception ignored) {}
            }
        }
    }

    private void cancelWatchdogAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, BleForegroundService.class);
        intent.setAction(ACTION_WATCHDOG_TICK);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getService(this, 2002, intent, pendingFlags);

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * Updates the foreground notification dynamically.
     * @param message Status text to show
     * @param showChronometer If true, shows a live elapsed time counter (timer)
     */
    public void updateForegroundNotification(String message, boolean showChronometer) {
        Notification notification = createNotification(message, showChronometer);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification);
        }
    }

    private void triggerInitialConnection() {
        BleManager manager = getBleManager();
        if (manager != null) {
            PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
            Vehicle selectedVehicle = preferenceManager.getSelectedVehicle();

            if (selectedVehicle != null && selectedVehicle.getMacAddress() != null) {
                String targetMac = selectedVehicle.getMacAddress();
                if (!targetMac.trim().isEmpty() && !manager.isConnected(targetMac)) {
                    Log.i(TAG, "ForegroundService: Initial connect trigger for " + targetMac);
                    updateForegroundNotification("Initial Scan for " + selectedVehicle.getName() + "...", true);
                    manager.connect(targetMac, false);
                }
            }
        }
    }

    private void performWatchdogCheck() {
        Log.i(TAG, "Watchdog Check: Evaluating background BLE health...");
        BleManager manager = getBleManager();
        if (manager == null) return;

        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        Vehicle selectedVehicle = preferenceManager.getSelectedVehicle();

        if (selectedVehicle == null || selectedVehicle.getMacAddress() == null) return;
        String targetMac = selectedVehicle.getMacAddress();

        if (targetMac.trim().isEmpty()) return;

        if (!manager.isBluetoothEnabled()) {
            updateForegroundNotification("Phone Bluetooth is Off", false);
            return;
        }

        if (manager.isConnected(targetMac)) {
            // Connected: Stop the chronometer scan timer and display Connected status
            updateForegroundNotification("Connected to " + selectedVehicle.getName(), false);
            return;
        }

        // Disconnected: Trigger reconnect and show active scanning chronometer timer
        updateForegroundNotification("Scanning for " + selectedVehicle.getName() + "...", true);
        manager.connect(targetMac, preferenceManager.isAutoConnectEnabled());
    }

    private void forceStopApp() {
        cancelWatchdogAlarm();

        BleManager manager = getBleManager();
        if (manager != null) {
            manager.setListener(null);
            manager.release();
        }

        stopForeground(true);
        stopSelf();

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private BleManager getBleManager() {
        MyApplication app = (MyApplication) getApplication();
        return app != null ? app.getBleManager() : null;
    }

    private Notification createNotification(String message, boolean showChronometer) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent forceStopIntent = new Intent(this, BleForegroundService.class);
        forceStopIntent.setAction(ACTION_FORCE_STOP);
        PendingIntent forceStopPendingIntent = PendingIntent.getService(
                this, 1, forceStopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wireless Start Service")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Force Stop", forceStopPendingIntent)
                .setOngoing(true);

        if (showChronometer) {
            // Enables the built-in system stopwatch timer counting up from service/scan start time
            builder.setUsesChronometer(true)
                    .setWhen(System.currentTimeMillis());
        } else {
            // Disables the chronometer so it acts like a normal notification text view
            builder.setUsesChronometer(false);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BLE Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelWatchdogAlarm();

        BleManager manager = getBleManager();
        if (manager != null) {
            manager.setListener(null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}