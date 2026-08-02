package in.kvapps.wirelessstart.util;

import android.content.Context;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import in.kvapps.wirelessstart.shared.Constants;

public class FeedbackUtil {
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

    public static void sendConnectionSuccessHapticToWatch(Context context) {
        Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(nodes -> {
            for (Node node : nodes) {
                Wearable.getMessageClient(context).sendMessage(
                        node.getId(),
                        VIBRATE_MESSAGE_PATH,
                        new byte[0]
                );
            }
        });
    }
}
