package in.kvapps.wirelessstart;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

public class MainActivity extends Activity implements BleManager.BleListener {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private final String[] durationOptions = {"800ms", "1700ms", "2200ms", "Custom"};

    // UI Controls
    private View statusIndicator;
    private TextView txtStatus, txtLog, txtVoltageValue;
    private ScrollView scrollLog;
    private Button btnStart;
    private ImageButton btnMenu;
    private Spinner spinnerStart;
    private EditText inputCustomStart;
    private SwitchCompat switchVoltage;

    // Helpers
    private BleManager bleManager;
    private PreferenceManager preferenceManager;
    private BroadcastReceiver watchCommandReceiver;
    private boolean isTelemetryEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);
        bleManager = new BleManager(this, this);

        initUiViews();
        setupSpinnersAndPersistence();
        setupClickListeners();
        registerWatchReceiver();

        checkPermissionsAndConnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Triggers reconnect attempt when app comes back to the foreground from background
        if (hasPermissions()) {
            onLog("App resumed. Checking BLE connection...");
            bleManager.connect();
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
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
    }

    private void setupSpinnersAndPersistence() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durationOptions);
        spinnerStart.setAdapter(adapter);

        // Restore values
        spinnerStart.setSelection(preferenceManager.getStartSpinnerPosition());
        inputCustomStart.setText(preferenceManager.getStartCustomMs());

        // Listeners
        spinnerStart.setOnItemSelectedListener(new SimpleSpinnerListener((parent, view, position, id) -> {
            inputCustomStart.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            preferenceManager.saveStartSpinnerPosition(position);
        }));

        inputCustomStart.addTextChangedListener((SimpleTextWatcher) text -> preferenceManager.saveStartCustomMs(text));
    }

    private final android.os.Handler cooldownHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private void setupClickListeners() {
        btnStart.setOnClickListener(v -> {
            String command = formatCommand("START", spinnerStart, inputCustomStart);

            // 1. Transmit command
            bleManager.sendBleCommand(command);

            // 2. Calculate dynamic cooldown duration
            long pulseMs = parsePulseDuration(spinnerStart, inputCustomStart, 1700); // 1700ms default start
            long totalCooldownMs = pulseMs + 3000; // Pulse time + 3s starter motor resting cooldown

            // 3. Disable UI button and give visual user feedback
            btnStart.setEnabled(false);
            btnStart.setAlpha(0.5f);
            onLog("[SAFETY] Starter cooling down for " + (totalCooldownMs / 1000) + "s...");

            // 4. Re-enable button after cooldown finishes
            cooldownHandler.postDelayed(() -> {
                btnStart.setEnabled(true);
                btnStart.setAlpha(1.0f);
                onLog("[SAFETY] Ignition ready.");
            }, totalCooldownMs);
        });

        btnMenu.setOnClickListener(v -> {
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
        });

        switchVoltage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isTelemetryEnabled = true;
                bleManager.sendRawByteCommand((byte) 0x03);
                onLog("Telemetry request: Resuming live stream.");
            } else {
                isTelemetryEnabled = false;
                bleManager.sendRawByteCommand((byte) 0x02);
                if (txtVoltageValue != null) {
                    txtVoltageValue.setText("--.--V");
                }
                onLog("Telemetry request: Stopped live stream to save hardware power.");
            }
        });
    }

    // Helper to extract the pulse duration for precise timer matching
    private long parsePulseDuration(Spinner spinner, EditText customInput, long defaultMs) {
        int position = spinner.getSelectedItemPosition();
        switch (position) {
            case 1: return 2000;
            case 2: return 3000;
            case 3:
                try {
                    return Long.parseLong(customInput.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return defaultMs;
                }
            default: return defaultMs;
        }
    }

    private String formatCommand(String action, Spinner spinner, EditText customInput) {
        int selectedIndex = spinner.getSelectedItemPosition();
        switch (selectedIndex) {
            case 1: return action + ":2000";
            case 2: return action + ":3000";
            case 3:
                String customVal = customInput.getText().toString().trim();
                if (!customVal.isEmpty()) return action + ":" + customVal;
                onLog("Warning: Custom ms empty! Transmitting default signal.");
                return action;
            default: return action;
        }
    }

    private void checkPermissionsAndConnect() {
        if (!hasPermissions()) {
            onLog("Requesting hardware system permissions...");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
            }
            return;
        }
        bleManager.connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onLog("Permissions approved by user.");
                bleManager.connect();
            } else {
                onLog("CRITICAL ERROR: Bluetooth permissions denied.");
                onConnectionStateChanged(false, "Permissions Denied");
            }
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

                    // Mark this broadcast as handled so the Service knows NOT to send duplicate BLE commands
                    setResultCode(-1);
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
        bleManager.disconnect();
    }

    // --- BLE Manager Callbacks ---
    @Override
    public void onLog(String message) {
        runOnUiThread(() -> {
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            txtLog.append("[" + timeStamp + "] " + message + "\n");

            // Auto-scroll the terminal view down to the latest message
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
        });
    }

    @Override
    public void onServicesReady() {
        onLog("GATT pipeline established. Ready for control operations.");
    }

    @Override
    public void onVoltageReceived(float voltage) {
        runOnUiThread(() -> {
            // FIX: Only update the display if the user wants to see the data
            if (isTelemetryEnabled && txtVoltageValue != null) {
                txtVoltageValue.setText(String.format(Locale.getDefault(), "%.2fV", voltage));
            }
        });
    }


    // --- Utility Functional Interfaces for Cleaner Listeners ---
    private interface OnItemSelectedRunnable {
        void onSelected(AdapterView<?> parent, View view, int position, long id);
    }

    private static class SimpleSpinnerListener implements AdapterView.OnItemSelectedListener {
        private final OnItemSelectedRunnable runnable;
        SimpleSpinnerListener(OnItemSelectedRunnable runnable) { this.runnable = runnable; }
        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { runnable.onSelected(p, v, pos, id); }
        @Override public void onNothingSelected(AdapterView<?> p) {}
    }

    private interface SimpleTextWatcher extends TextWatcher {
        void onText(String text);
        @Override default void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override default void onTextChanged(CharSequence s, int start, int before, int count) { onText(s.toString()); }
        @Override default void afterTextChanged(Editable s) {}
    }
}