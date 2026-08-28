package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.wear.util.ActionUtil;

public class MainActivity extends Activity {

    private Button btnStart, btnStop, btnOpenScheduler;
    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());
    private static final long STARTER_COOLDOWN_MS = 4000; // 4 seconds safety cooldown
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btn_wear_start);
        btnStop = findViewById(R.id.btn_wear_stop);
        btnOpenScheduler = findViewById(R.id.btn_open_scheduler);

        btnStart.setOnClickListener(v -> handleStartAction());
        btnStop.setOnClickListener(v -> handleStopAction());
        btnOpenScheduler.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, WearScheduleActivity.class));
        });

        // Check and request notification permission for Android 13+
        checkNotificationPermission();
    }

    private void handleStartAction() {
        ActionUtil.transmitActionToPhone(this, Constants.START_PATH, "Cranking Engine...");

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

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Request the permission
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications are disabled. You won't see status updates.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cooldownHandler.removeCallbacksAndMessages(null);
    }
}