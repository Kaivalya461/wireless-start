package in.kvapps.wirelessstart.ble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import in.kvapps.wirelessstart.MainActivity;
import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.shared.Constants;

public class BleForegroundService extends Service {

    private static final String CHANNEL_ID = "ble_foreground_channel";
    private static final String ACTION_FORCE_STOP = "in.kvapps.wirelessstart.ACTION_FORCE_STOP";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Check if the Force Stop action was triggered from the notification button
        if (intent != null && ACTION_FORCE_STOP.equals(intent.getAction())) {
            forceStopApp();
            return START_NOT_STICKY;
        }

        Notification notification = createNotification("Background BLE Service Active.");
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification);

        BleManager manager = getBleManager();
        if (manager != null && !manager.isConnected()) {
            manager.connect(false); // This parameter doesn't control the autoConnect behavior.
        }

        return START_STICKY;
    }

    private void forceStopApp() {
        // 1. Release/Disconnect BLE connections safely
        BleManager manager = getBleManager();
        if (manager != null) {
            manager.setListener(null);
            manager.release();
        }

        // 2. Stop foreground notification and service
        stopForeground(true);
        stopSelf();

        // 3. Completely terminate the app process
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private BleManager getBleManager() {
        MyApplication app = (MyApplication) getApplication();
        return app != null ? app.getBleManager() : null;
    }


    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Intent to handle Force Stop button click
        Intent forceStopIntent = new Intent(this, BleForegroundService.class);
        forceStopIntent.setAction(ACTION_FORCE_STOP);
        PendingIntent forceStopPendingIntent = PendingIntent.getService(
                this, 1, forceStopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wireless Start Service")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Force Stop", forceStopPendingIntent)
                .setOngoing(true)
                .build();
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