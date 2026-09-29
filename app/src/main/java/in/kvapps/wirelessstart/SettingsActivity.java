package in.kvapps.wirelessstart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.slider.Slider;

import java.util.Locale;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.BleForegroundService;
import in.kvapps.wirelessstart.data.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private BleManager bleManager;

    private SwitchCompat switchAutoConnect;
    private SwitchCompat switchFailSafe;
    private SwitchCompat switchForegroundService;
    private SwitchCompat switchEngineSchedule;
    private SwitchCompat switchVoltage;
    private Slider sliderStart, sliderStop;
    private TextView txtStartDurationValue, txtStopDurationValue;
    private float startSliderMin, stopSliderMin;
    private float startSliderDefault, stopSliderDefault;

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
        switchEngineSchedule = findViewById(R.id.switch_engine_schedule);
        switchVoltage = findViewById(R.id.switch_voltage);
        sliderStart = findViewById(R.id.slider_start);
        sliderStop = findViewById(R.id.slider_stop);
        txtStartDurationValue = findViewById(R.id.txt_start_duration_value);
        txtStopDurationValue = findViewById(R.id.txt_stop_duration_value);
    }

    private void loadStoredPreferences() {
        if (preferenceManager != null) {
            switchAutoConnect.setChecked(preferenceManager.isAutoConnectEnabled());
            switchFailSafe.setChecked(preferenceManager.isFailSafeEnabled());
            switchForegroundService.setChecked(preferenceManager.isForegroundServiceEnabled());
            switchEngineSchedule.setChecked(preferenceManager.isEngineScheduleEnabled());
            switchVoltage.setChecked(preferenceManager.isTelemetryEnabled());

            setupResourcesData();
            // Load saved millisecond values (defaulting to 1.0s if not set)
            float startMs = preferenceManager.getStartPulseDuration(); // returns float/int ms
            if (startMs < startSliderMin) startMs = startSliderDefault;
            sliderStart.setValue(startMs);
            updateDurationLabel(txtStartDurationValue, startMs);

            float stopMs = preferenceManager.getStopPulseDuration();
            if (stopMs < stopSliderMin) stopMs = stopSliderDefault;
            sliderStop.setValue(stopMs);
            updateDurationLabel(txtStopDurationValue, stopMs);
        }
    }

    private void updateDurationLabel(TextView label, float valueMs) {
        float seconds = valueMs / 1000f;
        label.setText(String.format(Locale.getDefault(), "%.1fs", seconds));
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

        // Voltage / Telemetry Toggle listener
        switchVoltage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                preferenceManager.setTelemetryEnabled(isChecked);
            }
        });

        // Start Slider change listener
        sliderStart.addOnChangeListener((slider, value, fromUser) -> {
            updateDurationLabel(txtStartDurationValue, value);
            if (preferenceManager != null && fromUser) {
                preferenceManager.saveStartPulseDuration((int) value);
            }
        });

        // Stop Slider change listener
        sliderStop.addOnChangeListener((slider, value, fromUser) -> {
            updateDurationLabel(txtStopDurationValue, value);
            if (preferenceManager != null && fromUser) {
                preferenceManager.saveStopPulseDuration((int) value);
            }
        });

        // Scheduled Engine-Run
        setupScheduledEngineRunListener();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupScheduledEngineRunListener() {
        switchEngineSchedule.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                // First clean up the Engine-Run Schedule in case of disable
                if (isChecked) {
                    // Do nothing when enabled. Schedule can be set using Date/Time picker from the Wear App.
                } else {
                    bleManager.sendScheduledEpoch(0);

                    // Request for updated schedule from ESP32 after some delay
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (bleManager != null && bleManager.isConnected()) {
                            bleManager.requestScheduleFromEsp32();
                        }
                    }, 500); // 0.5 sec delay
                }

                // Lastly update the Preferences
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    preferenceManager.setEngineScheduleEnabled(isChecked);
                }, 2000); // 2 delay
            }
        });
    }

    private void setupResourcesData() {
        android.util.TypedValue outValue = new android.util.TypedValue();

        getResources().getValue(R.dimen.start_slider_min_value, outValue, true);
        startSliderMin = outValue.getFloat();

        getResources().getValue(R.dimen.stop_slider_min_value, outValue, true);
        stopSliderMin = outValue.getFloat();

        getResources().getValue(R.dimen.start_slider_default_value, outValue, true);
        startSliderDefault = outValue.getFloat();

        getResources().getValue(R.dimen.stop_slider_default_value, outValue, true);
        stopSliderDefault = outValue.getFloat();
    }
}