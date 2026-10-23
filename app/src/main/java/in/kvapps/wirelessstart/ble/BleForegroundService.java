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

import java.util.List;
import java.util.Locale;

import in.kvapps.wirelessstart.MainActivity;
import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.ScanHealth;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.UiUtils;
import in.kvapps.wirelessstart.util.WearSyncUtils;

public class BleForegroundService extends Service implements ConnectionStateRepository.OnConnectionStateChangedListener {

    private static final String TAG = "BleForegroundService";
    private static final String CHANNEL_ID = "ble_foreground_channel";
    private static final String ACTION_FORCE_STOP = "in.kvapps.wirelessstart.ACTION_FORCE_STOP";
    private static final String ACTION_WATCHDOG_TICK = "in.kvapps.wirelessstart.ACTION_WATCHDOG_TICK";
    private static final String ACTION_RECOVER_SCAN = "in.kvapps.wirelessstart.ACTION_RECOVER_SCAN";

    // Watchdog interval: Check connection status every 15 minutes
    private static final long WATCHDOG_INTERVAL_MS = 15L * 60L * 1000L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // Register to listen to the Single Source of Truth connection state repository
        ConnectionStateRepository.getInstance().addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_FORCE_STOP.equals(action)) {
                forceStopApp();
                return START_NOT_STICKY;
            } else if (ACTION_RECOVER_SCAN.equals(action)) {
                triggerManualScanRecovery();
                return START_STICKY;
            } else if (ACTION_WATCHDOG_TICK.equals(action)) {
                performWatchdogCheck();
                scheduleNextWatchdogAlarm();
                return START_STICKY;
            }
        }

        // Determine correct initial notification state from SSOT repository
        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        Vehicle selectedVehicle = preferenceManager.getSelectedVehicle();

        // 1. MANDATORY: Call startForeground immediately with the accurate state
        ScanHealth initialHealth = ScanHealth.HEALTHY;
        BleManager manager = getBleManager();
        if (selectedVehicle != null && selectedVehicle.getMacAddress() != null && manager != null) {
            initialHealth = evaluateScanHealth(manager, selectedVehicle.getMacAddress());
        }
        Notification notification = createNotification(initialHealth);
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification);

        // 2. Trigger initial connection/scanning if not already connected
        triggerInitialConnection();

        // 3. Schedule the first Doze-safe background watchdog alarm
        scheduleNextWatchdogAlarm();

        return START_STICKY;
    }

    @Override
    public void onStateChanged(String macAddress, ConnectionStateRepository.DeviceConnectionState newState) {
        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        Vehicle selectedVehicle = preferenceManager.getSelectedVehicle();

        if (selectedVehicle != null && macAddress.equalsIgnoreCase(selectedVehicle.getMacAddress())) {
            switch (newState.getState()) {
                case CONNECTED:
                    updateForegroundNotification(ScanHealth.HEALTHY);
                    WearSyncUtils.syncBleStatusToWatch(this, newState.isConnected(), newState.getStatusText());
                    FeedbackUtils.sendHapticToWatch(this, Constants.HAPTIC_CONNECT);
                    break;
                case DISCOVERING:
                    updateForegroundNotification(ScanHealth.HEALTHY);
                    break;
                case DISCONNECTED:
                default:
                    updateForegroundNotification(ScanHealth.HEALTHY);
                    WearSyncUtils.syncBleStatusToWatch(this, newState.isConnected(), newState.getStatusText());
                    FeedbackUtils.sendHapticToWatch(this, Constants.HAPTIC_DISCONNECT);
                    break;
            }
        }
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
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    } else {
                        updateForegroundNotification(ScanHealth.FAILURE);
                        Log.w(TAG, "Exact Alarm permission (SCHEDULE_EXACT_ALARM) is not granted.");
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while scheduling exact alarm: " + e.getMessage());
                updateForegroundNotification(ScanHealth.FAILURE);
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
     * @param health Scan Health status to determine
     */
    public void updateForegroundNotification(ScanHealth health) {
        Notification notification = createNotification(health);
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
                    AppLogger.logToDatabaseAndLogcat(this, TAG, "ForegroundService: Initial connect trigger for " + targetMac);
                    manager.connect(targetMac, false);
                }
            }
        }
    }

    // Method executed when user clicks "Recover Scan" on the notification
    private void triggerManualScanRecovery() {
        Log.i(TAG, "Manual Scan Recovery triggered by user from notification.");
        BleManager manager = getBleManager();
        if (manager == null) return;

        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        Vehicle selectedVehicle = preferenceManager.getSelectedVehicle();
        if (selectedVehicle == null || selectedVehicle.getMacAddress() == null) return;

        String targetMac = selectedVehicle.getMacAddress();

        // Reset BLE stack and restart scanning
        manager.release();
        manager.connect(targetMac, preferenceManager.isAutoConnectEnabled());

        updateForegroundNotification(ScanHealth.HEALTHY);
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

        // Evaluate Scan Health
        ScanHealth health = evaluateScanHealth(manager, targetMac);

        if (health == ScanHealth.FAILURE) {
            updateForegroundNotification(health);
            return;
        }

        if (health == ScanHealth.WARNING) {
            // Shows warning text AND dynamically pops up the "Recover Scan" button
            updateForegroundNotification(health);
            Log.w(TAG, "Scan health is WARNING. Showing manual recovery button.");
            return;
        }

        if (manager.isConnected(targetMac)) {
            updateForegroundNotification(health);
            return;
        }

        // Disconnected & Healthy: Active scanning (No button shown)
        updateForegroundNotification(health);
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

    private Notification createNotification(ScanHealth health) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Force Stop Action Intent
        Intent forceStopIntent = new Intent(this, BleForegroundService.class);
        forceStopIntent.setAction(ACTION_FORCE_STOP);
        PendingIntent forceStopPendingIntent = PendingIntent.getService(
                this, 1, forceStopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        BleManager bleManager = getBleManager();
        List<Vehicle> vehicles = preferenceManager.getVehicles();

        int totalVehicles = vehicles != null ? vehicles.size() : 0;
        int connectedCount = 0;

        if (vehicles != null && bleManager != null) {
            for (Vehicle v : vehicles) {
                if (bleManager.isConnected(v.getMacAddress())) {
                    connectedCount++;
                }
            }
        }

        // Format Scan Status line
        String healthStr = "Healthy";
        if (health == ScanHealth.WARNING) healthStr = String.format(
                "Warning (Stalled: %s)",
                UiUtils.formatRelativeTime(BleScanReceiver.getLastScanPacketTimestamp()));
        else if (health == ScanHealth.FAILURE) healthStr = "Failed (BT Off)";

        String summaryLine = String.format(Locale.getDefault(), "Scan Status: %s", healthStr);
        String devicesLine = String.format(Locale.getDefault(), "Vehicles: %d/%d Connected", connectedCount, totalVehicles);

        // Setup InboxStyle for multi-line display
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle()
                .setBigContentTitle(devicesLine)
                .addLine(summaryLine);

        if (vehicles != null) {
            for (Vehicle v : vehicles) {
                boolean isConnected = (bleManager != null && bleManager.isConnected(v.getMacAddress()));
                String stateDesc = isConnected ? "Connected" : "Offline";
                long lastSeen = preferenceManager.getLastSeenTimestamp(v.getMacAddress());
                String timeDesc = UiUtils.formatRelativeTimeLastSeen(lastSeen);

                String line = String.format(Locale.getDefault(), "• %s: %s (%s)", v.getName(), stateDesc, timeDesc);
                inboxStyle.addLine(line);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wireless Start Service")
                .setContentText(summaryLine) // Collapsed view text
                .setStyle(inboxStyle)        // Expanded multi-line view
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        // Add Recover Scan button if in WARNING state
        if (health == ScanHealth.WARNING) {
            Intent recoverScanIntent = new Intent(this, BleForegroundService.class);
            recoverScanIntent.setAction(ACTION_RECOVER_SCAN);
            PendingIntent recoverScanPendingIntent = PendingIntent.getService(
                    this, 2, recoverScanIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            builder.addAction(android.R.drawable.ic_menu_rotate, "Recover Scan", recoverScanPendingIntent);
        }

        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Force Stop", forceStopPendingIntent);

        // Show chronometer if any vehicle is scanning/disconnected
        if (connectedCount < totalVehicles) {
            builder.setUsesChronometer(true).setWhen(System.currentTimeMillis());
        } else {
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

    private ScanHealth evaluateScanHealth(BleManager manager, String targetMac) {
        if (!manager.isBluetoothEnabled()) {
            return ScanHealth.FAILURE;
        }

        // If already connected, it's inherently healthy
        if (manager.isConnected(targetMac)) {
            return ScanHealth.HEALTHY;
        }

        // Check if the scan receiver has received active packets from Android OS recently (e.g., last 10 minutes)
        long lastPacketTime = BleScanReceiver.getLastScanPacketTimestamp();
        long timeSinceLastPacket = System.currentTimeMillis() - lastPacketTime;
        long tenMinutesMs = 10L * 60L * 1000L;

        // If it's been more than 10 minutes since the last hardware scan callback,
        // Android has likely throttled or frozen the background scan.
        if (lastPacketTime == 0 || timeSinceLastPacket > tenMinutesMs) {
            return ScanHealth.WARNING;
        }

        return ScanHealth.HEALTHY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelWatchdogAlarm();
        ConnectionStateRepository.getInstance().removeListener(this);

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