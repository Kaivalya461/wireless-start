package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import in.kvapps.wirelessstart.wear.pubsub.NearbyVehicleEventBus;
import in.kvapps.wirelessstart.wear.util.PulsingGlowView;

public class NearbyVehicleActivity extends Activity {
    private PulsingGlowView pulsingGlowView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable finishRunnable;
    private boolean isFinished = false;

    // Define the single listener instance
    private final NearbyVehicleEventBus.NearbyVehicleCallbackListener nearbyListener =
            () -> handleEventCompletion(true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proximity_start);

        // Setup PulsingGlowView and start continuous pulse animation
        pulsingGlowView = findViewById(R.id.pulsingGlowView);
        if (pulsingGlowView != null) {
            pulsingGlowView.startPulsing();
        }

        // Time-based fallback: Pulse for 10 seconds, then fade out and finish if no event arrives
        finishRunnable = () -> {
            handleEventCompletion(false);
        };

        // Post the 10-second timer
        handler.postDelayed(finishRunnable, 10000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set the active listener when the activity comes to the foreground
        NearbyVehicleEventBus.setListener(nearbyListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // DO NOT Clear the listener when leaving as this animation could be overlapped with another causing missed events in this animation.
//        NearbyVehicleEventBus.setListener(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NearbyVehicleEventBus.setListener(null);
        if (finishRunnable != null) {
            handler.removeCallbacks(finishRunnable);
        }
        handler.removeCallbacksAndMessages(null);
    }

    private void handleEventCompletion(boolean isSuccess) {
        if (isFinished) return;
        isFinished = true;

        // Cancel the 10-second fallback timer since we got a response or finished
        if (finishRunnable != null) {
            handler.removeCallbacks(finishRunnable);
        }

        if (pulsingGlowView != null) {
            // Pass isSuccess and a completion callback to finish the activity when animations end
            pulsingGlowView.startFadeOut(isSuccess, this::finish);
        } else {
            finish();
        }
    }
}