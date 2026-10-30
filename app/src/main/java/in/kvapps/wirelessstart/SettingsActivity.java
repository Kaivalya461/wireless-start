package in.kvapps.wirelessstart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.slider.Slider;

import in.kvapps.wirelessstart.ble.BleDeviceSession;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.BleForegroundService;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.repository.VehicleRepository;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private BleManager bleManager;
    private VehicleRepository vehicleRepository;

    private SwitchCompat switchAutoConnect;
    private SwitchCompat switchFailSafe;
    private SwitchCompat switchForegroundService;
    private SwitchCompat switchEngineSchedule;
    private SwitchCompat switchVoltage;

    // Slider controls for Proximity Range
    private Slider sliderConnectionThreshold;
    private TextView txtConnectionThresholdSummary;

    private boolean initialAutoConnect;
    private boolean initialFailSafe;
    private boolean initialForegroundService;
    private boolean initialEngineSchedule;
    private boolean initialVoltage;
    private int initialConnectionThreshold;

    private int currentConnectionThreshold;

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
        vehicleRepository = app.getVehicleRepository();
    }

    private void initUiViews() {
        switchAutoConnect = findViewById(R.id.switch_auto_connect);
        switchFailSafe = findViewById(R.id.switch_fail_safe);
        switchForegroundService = findViewById(R.id.switch_foreground_service);
        switchEngineSchedule = findViewById(R.id.switch_engine_schedule);
        switchVoltage = findViewById(R.id.switch_voltage);

        sliderConnectionThreshold = findViewById(R.id.slider_connection_threshold);
        txtConnectionThresholdSummary = findViewById(R.id.txt_connection_threshold_summary);
    }

    private void loadStoredPreferences() {
        if (preferenceManager != null) {
            initialAutoConnect = preferenceManager.isAutoConnectEnabled();
            initialFailSafe = preferenceManager.isFailSafeEnabled();
            initialForegroundService = preferenceManager.isForegroundServiceEnabled();
            initialEngineSchedule = preferenceManager.isEngineScheduleEnabled();
            initialVoltage = preferenceManager.isTelemetryEnabled();
            initialConnectionThreshold = preferenceManager.getConnectionThreshold();

            currentConnectionThreshold = initialConnectionThreshold;

            switchAutoConnect.setChecked(initialAutoConnect);
            switchFailSafe.setChecked(initialFailSafe);
            switchForegroundService.setChecked(initialForegroundService);
            switchEngineSchedule.setChecked(initialEngineSchedule);
            switchVoltage.setChecked(initialVoltage);

            sliderConnectionThreshold.setValue(currentConnectionThreshold);
            updateThresholdSummary(currentConnectionThreshold);
        }
    }

    private void updateThresholdSummary(int threshold) {
        String[] descriptions = {
                "Long Range (If you want to connect from far away.)",
                "Medium Range (You are around the vehicle.) Default",
                "Strong Only (You are right on the vehicle!)"
        };
        if (threshold >= 0 && threshold < descriptions.length) {
            txtConnectionThresholdSummary.setText(descriptions[threshold]);
        }
    }

    private void setupListeners() {
        switchAutoConnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) preferenceManager.setAutoConnectEnabled(isChecked);
        });

        switchFailSafe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null && vehicleRepository.getDefaultVehicle() != null) {
                preferenceManager.setFailSafeEnabled(isChecked);
                String macAdd = vehicleRepository.getDefaultVehicle().getMacAddress();
                if (bleManager != null) {
                    BleDeviceSession session = bleManager.getSession(macAdd);
                    if (session != null && session.isConnected()) {
                        session.syncFailSafeState(isChecked);
                    }
                }
            }
        });

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

        switchVoltage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) preferenceManager.setTelemetryEnabled(isChecked);
        });

        setupScheduledEngineRunListener();

        // Slider value change listener
        sliderConnectionThreshold.addOnChangeListener((slider, value, fromUser) -> {
            int threshold = (int) value;
            currentConnectionThreshold = threshold;
            updateThresholdSummary(threshold);
            if (fromUser && preferenceManager != null) {
                preferenceManager.setConnectionThreshold(threshold);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void finish() {
        boolean hasChanges = (switchAutoConnect.isChecked() != initialAutoConnect) ||
                (switchFailSafe.isChecked() != initialFailSafe) ||
                (switchForegroundService.isChecked() != initialForegroundService) ||
                (switchEngineSchedule.isChecked() != initialEngineSchedule) ||
                (switchVoltage.isChecked() != initialVoltage) ||
                (currentConnectionThreshold != initialConnectionThreshold);

        if (hasChanges) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }

        super.finish();
    }

    private void setupScheduledEngineRunListener() {
        switchEngineSchedule.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null && vehicleRepository.getDefaultVehicle() != null) {
                String macAdd = vehicleRepository.getDefaultVehicle().getMacAddress();
                BleDeviceSession session = bleManager != null ? bleManager.getSession(macAdd) : null;

                if (!isChecked) {
                    if (session != null && session.isConnected()) {
                        session.sendScheduledEpoch(0);
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (session.isConnected()) {
                                session.requestScheduleFromEsp32();
                            }
                        }, 500);
                    }
                }

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    preferenceManager.setEngineScheduleEnabled(isChecked);
                }, 2000);
            }
        });
    }
}