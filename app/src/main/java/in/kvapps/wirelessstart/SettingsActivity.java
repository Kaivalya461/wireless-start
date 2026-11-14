package in.kvapps.wirelessstart;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

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
    private SwitchCompat switchAutoDefaultVehicle;
    private SwitchCompat switchSyncWatch; // New: Sync Vehicle events to Watch

    private boolean initialAutoConnect;
    private boolean initialFailSafe;
    private boolean initialForegroundService;
    private boolean initialEngineSchedule;
    private boolean initialVoltage;
    private boolean initialAutoDefaultVehicle;
    private boolean initialSyncWatch; // New: Track initial state for result checking

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
        switchAutoDefaultVehicle = findViewById(R.id.switch_auto_default_vehicle);
        switchSyncWatch = findViewById(R.id.switch_sync_watch); // New: Initialize view
    }

    private void loadStoredPreferences() {
        if (preferenceManager != null) {
            initialAutoConnect = preferenceManager.isAutoConnectEnabled();
            initialFailSafe = preferenceManager.isFailSafeEnabled();
            initialForegroundService = preferenceManager.isForegroundServiceEnabled();
            initialEngineSchedule = preferenceManager.isEngineScheduleEnabled();
            initialVoltage = preferenceManager.isTelemetryEnabled();
            initialAutoDefaultVehicle = preferenceManager.isAutoDefaultVehicleEnabled();
            initialSyncWatch = preferenceManager.isWatchSyncEnabled(); // New: Load preference

            switchAutoConnect.setChecked(initialAutoConnect);
            switchFailSafe.setChecked(initialFailSafe);
            switchForegroundService.setChecked(initialForegroundService);
            switchEngineSchedule.setChecked(initialEngineSchedule);
            switchVoltage.setChecked(initialVoltage);
            switchAutoDefaultVehicle.setChecked(initialAutoDefaultVehicle);
            switchSyncWatch.setChecked(initialSyncWatch); // New: Apply state to switch
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

        // Auto-Select Default Vehicle Toggle Listener
        switchAutoDefaultVehicle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                preferenceManager.setAutoDefaultVehicleEnabled(isChecked);
            }
        });

        // New: Sync Vehicle Events to Watch Toggle Listener
        switchSyncWatch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (preferenceManager != null) {
                preferenceManager.setWatchSyncEnabled(isChecked);
            }
        });

        setupScheduledEngineRunListener();
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
                (switchAutoDefaultVehicle.isChecked() != initialAutoDefaultVehicle) ||
                (switchSyncWatch.isChecked() != initialSyncWatch); // New: Track change

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