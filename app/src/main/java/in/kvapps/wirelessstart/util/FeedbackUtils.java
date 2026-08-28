package in.kvapps.wirelessstart.util;

import static androidx.core.app.NotificationCompat.PRIORITY_DEFAULT;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.core.app.NotificationCompat.*;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.MainActivity;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.shared.Constants;

public class FeedbackUtils {
    private static final String VIBRATE_MESSAGE_PATH = Constants.VIBRATE_PATH;

    public static void triggerDoubleVibrate(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            // Double Vibrate pattern: Wait 0ms, Vibrate 80ms, Pause 80ms, Vibrate 80ms
            long[] timings = {0, 80, 80, 80};
            int[] amplitudes = {0, 255, 0, 255}; // Full intensity
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1));
        }
    }

    /**
     * Sends a haptic command to the watch.
     * @param context The application context
     * @param hapticType Constants.HAPTIC_SUCCESS or Constants.HAPTIC_DISCONNECT
     */
    public static void sendHapticToWatch(Context context, String hapticType) {
        byte[] payload = hapticType.getBytes();
        Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(nodes -> {
            for (Node node : nodes) {
                Wearable.getMessageClient(context).sendMessage(
                        node.getId(),
                        VIBRATE_MESSAGE_PATH,
                        payload
                );
            }
        });
    }

    /**
     * Triggers a distinct disconnect vibration pattern optimized for standard haptic motors.
     * Pattern: Strong snap, brief pause, lighter click.
     */
    public static void triggerDisconnectVibrate(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            // Snap-Thud pattern:
            // Wait 0ms | Vibrate 70ms (Amp: 255) | Pause 50ms | Vibrate 40ms (Amp: 120)
            long[] timings = {0, 70, 50, 40};
            int[] amplitudes = {0, 255, 0, 120};
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1));
        }
    }

    /**
     * Sends the command result acknowledgment payload (Success or Failure) to the watch.
     * @param context The application context
     * @param resultPayload Constants.START_SUCCESS or Constants.START_FAILURE
     */
    public static void sendCmdResultAckToWatch(Context context, String resultPayload) {
        byte[] payload = resultPayload != null ? resultPayload.getBytes() : new byte[0];
        Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(nodes -> {
            for (Node node : nodes) {
                Wearable.getMessageClient(context).sendMessage(
                        node.getId(),
                        Constants.START_COMMAND_RESULT_PATH,
                        payload);
            }
        });
    }

    public static void showConnectionNotification(Context context, boolean isConnected, long uptimeMillis,
                                                  boolean isAppInForeground) {
        // Skip Notification in case of,
        // 1. App is in foreground
        // 2. Disconnect event and connection time was 0 sec (i.e. Failed Connection Attempts)
        if (isAppInForeground || (!isConnected && uptimeMillis == 0)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        String channelId = "connection_status_channel";
        String groupKey = "wireless_status_group";
        int summaryNotificationId = Constants.SUMMARY_NOTIFICATION_ID;

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Connection Status Alerts",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notifies when wireless starter connects or disconnects");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String notificationTitle = isConnected ? "Connected" : "Disconnected";

        // Format content text based on connection state
        String contentText;
        if (isConnected) {
            contentText = "At: " + timeStamp;
        } else {
            String formattedUptime = formatUptime(uptimeMillis);
            contentText = "At: " + timeStamp + " | Uptime: " + formattedUptime;
        }

        Builder childBuilder = new Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationTitle)
                .setContentText(contentText)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setGroup(groupKey)
                .setPriority(PRIORITY_DEFAULT);

        int uniqueEventId = (int) System.currentTimeMillis();
        notificationManager.notify(uniqueEventId, childBuilder.build());

        Builder summaryBuilder = new Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Wireless Start Activity")
                .setContentText("Recent connection updates")
                .setContentIntent(pendingIntent)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(PRIORITY_DEFAULT);

        notificationManager.notify(summaryNotificationId, summaryBuilder.build());
    }

    // Helper method to format milliseconds into HH:mm:ss or mm:ss
    private static String formatUptime(long millis) {
        if (millis <= 0) return "0s";
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%ds", secs);
        }
    }
}