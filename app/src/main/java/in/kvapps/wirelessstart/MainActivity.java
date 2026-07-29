package in.kvapps.wirelessstart;

import android.annotation.SuppressLint;
import android.app.Activity;
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

import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.util.PermissionUtils;
import in.kvapps.wirelessstart.util.UiUtils;

public class MainActivity extends Activity implements BleManager.BleListener {
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
        setupSpinnersAndPersistence();
        setupClickListeners();
        registerWatchReceiver();

        updateConnectionUi(false);
        checkPermissionsAndConnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionUtils.hasBluetoothPermissions(this)) {
            onLog("App resumed. Checking BLE connection...");
            bleManager.connect();
        }
    }

    private void initDependencies() {
        dbHelper = new VoltageDbHelper(this);
        preferenceManager = new PreferenceManager(this);
        bleManager = new BleManager(this, this);
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
        popup.getMenu().add(0, 1, 0, "Reconnect");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                onLog("Manual reconnect requested...");
                checkPermissionsAndConnect();
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
        bleManager.disconnect();
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
            txtStatus.setText(statusText);
            statusIndicator.setBackgroundColor(isConnected ? 0xFF4CAF50 : 0xFFE53935);
            onLog("System state: " + statusText);
            updateConnectionUi(isConnected);
        });
    }

    private void updateConnectionUi(boolean isConnected) {
        UiUtils.setButtonState(btnStart, isConnected, isConnected ? 1.0f : 0.5f);
    }

    @Override
    public void onServicesReady() {
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
}