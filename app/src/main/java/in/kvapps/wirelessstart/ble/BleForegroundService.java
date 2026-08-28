package in.kvapps.wirelessstart.ble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import in.kvapps.wirelessstart.MainActivity;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.shared.Constants;

public class BleForegroundService extends Service {

    private static final String CHANNEL_ID = "ble_foreground_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start the service in the foreground with an active notification
        Notification notification = createNotification("Maintaining 24/7 Background Service to avoid Connection Drops.");
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification);

        // START_STICKY ensures Android restarts the service if it gets terminated under memory pressure
        return START_STICKY;
    }

    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wireless Start Active")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BLE Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW // Low importance to avoid annoying sound/vibration
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}