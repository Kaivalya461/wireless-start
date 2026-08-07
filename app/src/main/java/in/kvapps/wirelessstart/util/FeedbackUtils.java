package in.kvapps.wirelessstart.util;

import android.content.Context;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

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
}