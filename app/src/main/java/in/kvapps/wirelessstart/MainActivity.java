package in.kvapps.wirelessstart;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.ble.BleLifecycleObserver;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.util.FeedbackUtil;
import in.kvapps.wirelessstart.util.PermissionUtils;
import in.kvapps.wirelessstart.util.UiUtils;

public class MainActivity extends AppCompatActivity implements BleManager.BleListener {
    // UI Controls
    private View statusIndicator, panelVoltage;
    private TextView txtStatus, txtLog, txtVoltageValue;
    private ScrollView scrollLog;
    private Button btnStart;
    private ImageButton btnMenu;
    private Spinner spinnerStart;
    private EditText inputCustomStart;
    private SwitchCompat switchVoltage;

    // Architecture & Helpers
    private VoltageDbHelper dbHelper;
    private BleManager bleManager;
    private PreferenceManager preferenceManager;
    private BroadcastReceiver watchCommandReceiver;
    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());

    private boolean isTelemetryEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDependencies();
        initUiViews();
        loadTelemetryPreference();
        setupSpinnersAndPersistence();
        setupClickListeners();
        registerWatchReceiver();
        // Register the lifecycle observer for automatic BLE reconnection handling
        getLifecycle().addObserver(new BleLifecycleObserver(this, bleManager, this::onLog));

        updateConnectionUi(false);
        checkPermissionsAndConnect();
    }

    private void initDependencies() {
        dbHelper = new VoltageDbHelper(this);
        preferenceManager = new PreferenceManager(this);
        bleManager = new BleManager(this, this);

        // Load User saved Device Configuration
        bleManager.setTargetHwName(preferenceManager.getTargetHwName());
        bleManager.setMacAdd(preferenceManager.getTargetMacAddress());
    }

    private void initUiViews() {
        statusIndicator = findViewById(R.id.status_indicator);
        txtStatus = findViewById(R.id.txt_status);
        txtLog = findViewById(R.id.txt_log);
        scrollLog = findViewById(R.id.scroll_log);
        btnStart = findViewById(R.id.btn_start);
        btnMenu = findViewById(R.id.btn_menu);
        spinnerStart = findViewById(R.id.spinner_start);
        inputCustomStart = findViewById(R.id.input_custom_start);
        txtVoltageValue = findViewById(R.id.txt_voltage_value);
        switchVoltage = findViewById(R.id.switch_voltage);
        panelVoltage = findViewById(R.id.panel_voltage);
    }

    private void setupSpinnersAndPersistence() {
        UiUtils.setupDurationSpinner(this, spinnerStart, inputCustomStart, preferenceManager);
    }

    private void setupClickListeners() {
        btnStart.setOnClickListener(v -> handleStartAction());
        btnMenu.setOnClickListener(this::showPopupMenu);
        panelVoltage.setOnClickListener(v -> startActivity(new Intent(this, VoltageHistoryActivity.class)));
        switchVoltage.setOnCheckedChangeListener((buttonView, isChecked) -> handleTelemetryToggle(isChecked));
    }

    private void handleStartAction() {
        FeedbackUtil.triggerDoubleVibrate(this);
        String command = preferenceManager.getFormattedCommand("START");
        bleManager.sendBleCommand(command);

        long pulseMs = preferenceManager.getSelectedStartPulseDuration();
        long totalCooldownMs = pulseMs + 3000; // Pulse time + 3s starter motor resting cooldown

        UiUtils.setButtonState(btnStart, false, 0.5f);
        onLog("[SAFETY] Starter cooling down for " + (totalCooldownMs / 1000) + "s...");

        cooldownHandler.postDelayed(() -> {
            UiUtils.setButtonState(btnStart, true, 1.0f);
            onLog("[SAFETY] Ignition ready.");
        }, totalCooldownMs);
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);

        // Add menu items (ID 1 for Reconnect, ID 2 for Rename)
        popup.getMenu().add(0, 1, 0, "Reconnect");
        popup.getMenu().add(0, 2, 1, "Edit Config");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                onLog("Manual reconnect requested...");
                checkPermissionsAndConnect();
                return true;
            } else if (id == 2) {
                showEditConfigDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void handleTelemetryToggle(boolean isChecked) {
        isTelemetryEnabled = isChecked;
        if (isChecked) {
            bleManager.sendRawByteCommand((byte) 0x03);
            onLog("Telemetry request: Resuming live stream.");
        } else {
            bleManager.sendRawByteCommand((byte) 0x02);
            if (txtVoltageValue != null) txtVoltageValue.setText("--.--V");
            onLog("Telemetry request: Stopped live stream to save hardware power.");
        }
        preferenceManager.setTelemetryEnabled(isChecked);
    }

    private void checkPermissionsAndConnect() {
        if (!PermissionUtils.hasBluetoothPermissions(this)) {
            onLog("Requesting hardware system permissions...");
            PermissionUtils.requestBluetoothPermissions(this);
            return;
        }
        bleManager.connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils.handlePermissionsResult(requestCode, grantResults)) {
            onLog("Permissions approved by user.");
            bleManager.connect();
        } else {
            onLog("CRITICAL ERROR: Bluetooth permissions denied.");
            onConnectionStateChanged(false, "Permissions Denied");
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerWatchReceiver() {
        watchCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String formattedCommand = intent.getStringExtra("COMMAND");
                if (formattedCommand != null) {
                    onLog("[WATCH RX] UI handling trigger: " + formattedCommand);
                    bleManager.sendBleCommand(formattedCommand);
                    setResultCode(-1); // Mark handled
                }
            }
        };

        IntentFilter filter = new IntentFilter("DIO_HARDWARE_TRIGGER");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(watchCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(watchCommandReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchCommandReceiver != null) unregisterReceiver(watchCommandReceiver);
        if (dbHelper != null) dbHelper.close();
        if (bleManager != null) {
            bleManager.disconnect();
            bleManager.release();
        }
    }

    // --- BLE Manager Callbacks ---
    @Override
    public void onLog(String message) {
        runOnUiThread(() -> {
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            txtLog.append("[" + timeStamp + "] " + message + "\n");
            if (scrollLog != null) {
                scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected, String statusText) {
        runOnUiThread(() -> {
            // Format with Sci-Fi prefix
            String sciFiStatus = "SYSTEMS: " + statusText.toUpperCase();
            txtStatus.setText(sciFiStatus);

            if (isConnected) {
                statusIndicator.setBackgroundResource(R.drawable.indicator_online);
                updateConnectionUi(true);
            } else {
                statusIndicator.setBackgroundResource(R.drawable.indicator_offline);
                updateConnectionUi(false);
            }
        });
    }

    private void updateConnectionUi(boolean isConnected) {
        UiUtils.setButtonState(btnStart, isConnected, isConnected ? 1.0f : 0.5f);
    }

    @Override
    public void onServicesReady() {
        // 1. Auto-sync current system time to ESP32
        bleManager.sendAutoTimeSync();

        // 2. Force-sync the user's preferred telemetry state to the ESP32
        boolean savedTelemetryState = preferenceManager.isTelemetryEnabled();
        bleManager.syncTelemetryState(savedTelemetryState);

        FeedbackUtil.triggerDoubleVibrate(this);
        FeedbackUtil.sendConnectionSuccessHapticToWatch(this);
        onLog("GATT pipeline established. Ready for control operations.");
    }

    @Override
    public void onVoltageReceived(float voltage) {
        runOnUiThread(() -> {
            if (isTelemetryEnabled) {
                long now = System.currentTimeMillis();
                if (dbHelper != null) dbHelper.insertReading(now, voltage);
                if (txtVoltageValue != null) {
                    txtVoltageValue.setText(String.format(Locale.getDefault(), "%.2fV", voltage));
                }
            }
        });
    }

    // Load saved telemetry state into local variable
    private void loadTelemetryPreference() {
        isTelemetryEnabled = preferenceManager.isTelemetryEnabled();

        if (switchVoltage != null) {
            // Set switch checked state without triggering listeners (if any)
            switchVoltage.setChecked(isTelemetryEnabled);
        }
    }

    private void showEditConfigDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Device Configuration");

        // Create a container layout to hold multiple inputs
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(50, 40, 50, 20);

        // 1. Device Name Input
        final EditText inputName = new EditText(this);
        inputName.setHint("Device Name (e.g. Vehicle 001)");
        String currentName = preferenceManager.getTargetHwName();
        inputName.setText(currentName);
        container.addView(inputName);

        // 2. MAC Address Input
        final EditText inputMac = new EditText(this);
        inputMac.setHint("MAC Address (e.g. AA:BB:CC:DD:EE:FF)");
        // Fetch current saved MAC from your preferenceManager (make sure to create this method)
        String currentMac = preferenceManager.getTargetMacAddress();
        if (currentMac != null && !currentMac.isEmpty()) {
            inputMac.setText(currentMac);
        }
        container.addView(inputMac);

        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = inputName.getText().toString().trim();
            String newMac = inputMac.getText().toString().trim();

            if (!newName.isEmpty()) {
                preferenceManager.saveTargetHwName(newName);
                bleManager.setTargetHwName(newName);
            }

            // Save and update MAC address if valid format is entered
            if (!newMac.isEmpty()) {
                preferenceManager.saveTargetMacAddress(newMac);
                bleManager.setMacAdd(newMac);
            }

            onConnectionStateChanged(false, "Reconnecting");
            onLog("Configuration updated. Reconnecting...");
            bleManager.connect();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}