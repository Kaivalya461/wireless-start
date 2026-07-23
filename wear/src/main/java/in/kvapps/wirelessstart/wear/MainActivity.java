package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import in.kvapps.wirelessstart.wear.util.ActionUtil;

public class MainActivity extends Activity {

    private Button btnStart, btnStop;
    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());
    private static final long STARTER_COOLDOWN_MS = 4000; // 4 seconds safety cooldown

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btn_wear_start);
        btnStop = findViewById(R.id.btn_wear_stop);

        btnStart.setOnClickListener(v -> handleStartAction());
        btnStop.setOnClickListener(v -> handleStopAction());
    }

    private void handleStartAction() {
        // Transmit trigger path to phone
        ActionUtil.transmitActionToPhone(this, ActionUtil.START_PATH, "Cranking Engine...");

        // UI Anti-Spam Lockout
        btnStart.setEnabled(false);
        btnStart.setAlpha(0.5f);

        cooldownHandler.postDelayed(() -> {
            btnStart.setEnabled(true);
            btnStart.setAlpha(1.0f);
        }, STARTER_COOLDOWN_MS);
    }

    private void handleStopAction() {
        ActionUtil.transmitActionToPhone(this, ActionUtil.STOP_PATH, "Killing Engine...");

        btnStop.setEnabled(false);
        btnStop.setAlpha(0.5f);

        cooldownHandler.postDelayed(() -> {
            btnStop.setEnabled(true);
            btnStop.setAlpha(1.0f);
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cooldownHandler.removeCallbacksAndMessages(null);
    }
}