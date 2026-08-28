package in.kvapps.wirelessstart;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.BleForegroundService;
import in.kvapps.wirelessstart.data.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private BleManager bleManager;

    private SwitchCompat switchAutoConnect;
    private SwitchCompat switchFailSafe;
    private SwitchCompat switchForegroundService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initDependencies();
        initUiViews();
        loadStoredPreferences();
        setupListeners();
    }

    private void initDependencies() {
        MyApplication app = (MyApplication) getApplication();
        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();
    }

    private void initUiViews() {
        switchAutoConnect = findViewById(R.id.switch_auto_connect);
        switchFailSafe = findViewById(R.id.switch_fail_safe);
        switchForegroundService = findViewById(R.id.switch_foreground_service);
    }

    private void loadStoredPreferences() {
        if (preferenceManager != null) {
            switchAutoConnect.setChecked(preferenceManager.isAutoConnectEnabled());
            switchFailSafe.setChecked(preferenceManager.isFailSafeEnabled());
            switchForegroundService.setChecked(preferenceManager.isForegroundServiceEnabled());
        }
    }

    private void setupListeners() {
        // Auto-Connect toggle listener
        switchAutoConnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                preferenceManager.setAutoConnectEnabled(isChecked);
            }
        });

        // Fail-Safe Toggle listener
        switchFailSafe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                preferenceManager.setFailSafeEnabled(isChecked);

                // Sync immediately to ESP32 if device is connected
                if (bleManager != null && bleManager.isConnected()) {
                    bleManager.syncFailSafeState(isChecked);
                }
            }
        });

        // Foreground Service Toggle listener
        switchForegroundService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                preferenceManager.setForegroundServiceEnabled(isChecked);

                if (isChecked) {
                    startForegroundService(new Intent(SettingsActivity.this, BleForegroundService.class));
                } else {
                    stopService(new Intent(SettingsActivity.this, BleForegroundService.class));
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}