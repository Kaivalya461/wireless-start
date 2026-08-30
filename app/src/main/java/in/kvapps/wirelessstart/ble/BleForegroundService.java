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

public class BleForegroundService extends Service implements BleManager.BleListener {

    private static final String CHANNEL_ID = "ble_foreground_channel";
    private BleManager bleManager;
    private static BleForegroundService instance;

    public static BleForegroundService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();

        // Initialize BleManager centrally inside the Foreground Service
        // Pass 'this' as the initial listener; activities can override/hook into this later
        bleManager = new BleManager(getApplicationContext(), this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createNotification("Background BLE Service Active.");
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification);

        // Ensure we try connecting if not already connected
        if (bleManager != null && !bleManager.isConnected()) {
            bleManager.connect(true);
        }

        return START_STICKY; // Restarts service if Android kills it under memory pressure
    }

    public BleManager getBleManager() {
        return bleManager;
    }

    @Override
    public void onLog(String message) {
        // Log locally or broadcast if MainActivity is open
        android.util.Log.d("BleForegroundService", message);
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected, String statusText) {
        // Update notification text dynamically based on connection status so you know if it dropped in your pocket
//        String msg = isConnected ? "Connected. All Systems healthy." : "Disconnected. Searching...";
        updateNotification(statusText);
    }

    @Override
    public void onServicesReady() {
        android.util.Log.d("BleForegroundService", "GATT Services ready in background.");
    }

    @Override
    public void onDataReceived(byte[] rawData) {
        // Forward data if needed (or handle watch command triggers)
    }

    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wireless Start Service")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, createNotification(message));
        }
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
        if (bleManager != null) {
            bleManager.release();
        }
        instance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}