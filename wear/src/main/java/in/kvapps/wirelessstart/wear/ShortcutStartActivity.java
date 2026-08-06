package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import in.kvapps.wirelessstart.wear.pubsub.StartEventBus;
import in.kvapps.wirelessstart.wear.util.ActionUtil;
import in.kvapps.wirelessstart.wear.util.EdgeAnimationView;

public class ShortcutStartActivity extends Activity {

    private EdgeAnimationView edgeView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFinished = false;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shortcut_start);

        // Command - Fire the start request path to phone
        ActionUtil.transmitActionToPhone(this, ActionUtil.START_PATH, "Cranking Engine...");

        // Animation - Start creeping slowly toward the finish line while waiting
        edgeView = findViewById(R.id.edgeAnimationView);
        if (edgeView != null) {
            edgeView.startCreepingProgress();
        }

        // Animation - Define safety fallback timeout (10 seconds)
        timeoutRunnable = () -> {
            if (!isFinished) {
                isFinished = true;
                if (edgeView != null) {
                    edgeView.timeoutAndFadeOut(); // Shift to red and fade out
                }
                // Close activity fully after red visual feedback runs
                // This delay needs to be more than the animation duration, else edge animation will clip out.
                handler.postDelayed(this::finish, 2500);
            }
        };

        // Animation - Post the timeout check
        handler.postDelayed(timeoutRunnable, 11000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        StartEventBus.setListener(this::onEngineStartSuccess);
    }

    @Override
    protected void onPause() {
        super.onPause();
        StartEventBus.setListener(null);
    }

    private void onEngineStartSuccess() {
        if (isFinished) return;
        isFinished = true;

        // Animation - Cancel the timeout since success arrived in time
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }

        // Animation - Complete the circle, turn green, and trigger visual fade out
        if (edgeView != null) {
            edgeView.completeAndFadeOut();
        }

        // Close activity fully after the finish visual feedback runs
        handler.postDelayed(this::finish, 1500);
    }
}