package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import in.kvapps.wirelessstart.wear.util.ActionUtil;
import in.kvapps.wirelessstart.wear.util.EdgeAnimationView;

public class ShortcutStartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout containing the blue neon edge animation view
        setContentView(R.layout.activity_shortcut_start);

        // Trigger physical haptic feedback on the watch wrist
        triggerEngineStartHaptics();

        // Fire the start request path to phone
        ActionUtil.transmitActionToPhone(this, ActionUtil.START_PATH, "Cranking Engine...");

        EdgeAnimationView edgeView = findViewById(R.id.edgeAnimationView);

        // Trigger fade out 300ms before finishing
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (edgeView != null) {
                edgeView.startFadeOut();
            }
        }, 900);

        // Close activity fully after fade finishes
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1200);
    }

    private void triggerEngineStartHaptics() {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Creates a satisfying "cranking" pattern: a short thud, a slight pause, then a longer rumble
                long[] timings = {0, 70, 50, 150};
                int[] amplitudes = {0, 255, 0, 180}; // Max strength for impact, moderate for rumble
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                // Fallback for older legacy watches
                vibrator.vibrate(new long[]{0, 70, 50, 150}, -1);
            }
        }
    }
}