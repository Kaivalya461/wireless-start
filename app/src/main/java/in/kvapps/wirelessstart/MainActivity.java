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
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.domain.DeviceProtocolHandler;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.PermissionUtils;
import in.kvapps.wirelessstart.util.UiUtils;
import in.kvapps.wirelessstart.util.WearSyncUtils;

public class MainActivity extends AppCompatActivity implements BleManager.BleListener {
    private static final String TAG = Constants.PHONE_MAIN_ACTIVITY_TAG;
    // UI Controls
    private View statusIndicator, panelVoltage, cardLogSection;
    private TextView txtStatus, txtLog, txtVoltageValue;
    private ScrollView scrollLog;
    private Button btnStart;
    private ImageButton btnMenu, btnReconnect;
    private Spinner spinnerStart;
    private EditText inputCustomStart;
    private SwitchCompat switchVoltage;

    // Architecture & Helpers
    private VoltageDbHelper dbHelper;
    private BleManager bleManager;
    private DeviceProtocolHandler protocolHandler;
    private PreferenceManager preferenceManager;
    private BroadcastReceiver watchCommandReceiver;
    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());

    private boolean isTelemetryEnabled = false;
    private long commandStartTime = 0;
    private long connectionStartTime = 0;
    private static boolean isAppInForeground = false;

    private BroadcastReceiver scheduleReceiver; // For Scheduled Engine Run/Stop
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean bluetoothGranted = PermissionUtils.evaluatePermissionsResult(result);

                if (bluetoothGranted) {
                    onLog("Permissions approved by user.");
                    bleManager.connect(preferenceManager.isAutoConnectEnabled());
                } else {
                    onLog("CRITICAL ERROR: Required BLUETOOTH permissions denied.");
                    onConnectionStateChanged(false, "Permissions Denied");
                }
            });

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Check if you need to refresh or reconnect based on settings changes
                if (preferenceManager.isAutoConnectEnabled() && (bleManager != null && !bleManager.isConnected())) {
                    onLog("Settings updated. Reconnecting...");
                    bleManager.connect(true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDependencies();
        initUiViews();
        loadStoredLogsForToday();
        loadTelemetryPreference();
        setupSpinnersAndPersistence();
        setupClickListeners();
        registerWatchReceiver();
        registerScheduleReceiver();

        updateConnectionUi(false);
        pruneOldDatabaseRecords();
        checkPermissionsAndConnect();
    }

    private void initDependencies() {
        dbHelper = new VoltageDbHelper(this);

        // Get the shared MyApplication instance
        MyApplication app = (MyApplication) getApplication();

        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();

        // IMPORTANT: Since bleManager is now a shared app-wide singleton,
        // update its listener to point to the current MainActivity instance
        if (bleManager != null) {
            bleManager.setListener(this);
        }

        protocolHandler = new DeviceProtocolHandler(this, new DeviceProtocolHandler.ProtocolListener() {
            @Override
            public void onVoltageReady(float voltage) {
                // This handles your voltage business logic cleanly on the UI thread
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

            @Override
            public void onProtocolLog(String message) {
                onLog(message);
            }
        });

        if (preferenceManager.isForegroundServiceEnabled()) {
            startBleForegroundService();
        }
    }

    private void initUiViews() {
        statusIndicator = findViewById(R.id.status_indicator);
        txtStatus = findViewById(R.id.txt_status);
        txtLog = findViewById(R.id.txt_log);
        scrollLog = findViewById(R.id.scroll_log);
        btnStart = findViewById(R.id.btn_start);
        btnMenu = findViewById(R.id.btn_menu);
        btnReconnect = findViewById(R.id.btn_reconnect);
        spinnerStart = findViewById(R.id.spinner_start);
        inputCustomStart = findViewById(R.id.input_custom_start);
        txtVoltageValue = findViewById(R.id.txt_voltage_value);
        switchVoltage = findViewById(R.id.switch_voltage);
        panelVoltage = findViewById(R.id.panel_voltage);
        cardLogSection = findViewById(R.id.card_log_section);
    }

    private void setupSpinnersAndPersistence() {
        UiUtils.setupDurationSpinner(this, spinnerStart, inputCustomStart, preferenceManager);
    }

    private void setupClickListeners() {
        btnStart.setOnClickListener(v -> handleStartAction());
        btnMenu.setOnClickListener(this::showPopupMenu);
        panelVoltage.setOnClickListener(v -> startActivity(new Intent(this, VoltageHistoryActivity.class)));
        switchVoltage.setOnCheckedChangeListener((buttonView, isChecked) -> handleTelemetryToggle(isChecked));
        cardLogSection.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LogHistoryActivity.class);
            startActivity(intent);
        });
        btnReconnect.setOnClickListener(v -> handleReconnect());
        btnReconnect.setOnLongClickListener(v -> {
            v.setTooltipText("Reconnect");
            return false;
        });
    }

    private void handleStartAction() {
        String command = preferenceManager.getFormattedCommand("START");

        // Record start time before sending
        this.commandStartTime = System.currentTimeMillis();

        bleManager.sendBleCommand(
                command,
                () -> handleCommandResult(command, Constants.COMMAND_SUCCESS, false),  //onSuccess callback
                () -> handleCommandResult(command, Constants.COMMAND_FAILURE, false)    //onFailure callback
        );

        long pulseMs = preferenceManager.getSelectedStartPulseDuration();
        long totalCooldownMs = pulseMs + 3000; // Pulse time + 3s starter motor resting cooldown

        UiUtils.setButtonState(btnStart, false, 0.5f);
        onLog("[SAFETY] Cooling down for " + (totalCooldownMs / 1000) + "s...");

        cooldownHandler.postDelayed(() -> {
            UiUtils.setButtonState(btnStart, true, 1.0f);
            onLog("[SAFETY] Start Engine button is re-enabled");
        }, totalCooldownMs);
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);

        // Add menu items (ID 1 for Reconnect, ID 2 for Edit Config, ID 3 for Additional Settings)
        popup.getMenu().add(0, 1, 0, "Reconnect");
        popup.getMenu().add(0, 2, 1, "Edit Config"); // Restored Edit Config option
        popup.getMenu().add(0, 3, 2, "Settings");    // New Settings Activity option
        popup.getMenu().add(0, 4, 3, "About & Credits");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                handleReconnect();
                return true;
            } else if (id == 2) {
                // Launch the Edit Config Activity
                Intent intent = new Intent(MainActivity.this, EditConfigActivity.class);
                editConfigLauncher.launch(intent);
                return true;
            } else if (id == 3) {
                // Launch the new Settings Activity
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                settingsLauncher.launch(intent);
                return true;
            } else if (id == 4) {
                showAboutDialog();
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
        if (!PermissionUtils.hasBluetoothPermissions(this) || !PermissionUtils.hasNotificationPermissions(this)) {
            onLog("Requesting hardware and system permissions...");
            requestPermissionsLauncher.launch(PermissionUtils.getRequiredPermissions());
            return;
        }
        bleManager.connect(preferenceManager.isAutoConnectEnabled());
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerWatchReceiver() {
        watchCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String formattedCommand = intent.getStringExtra("COMMAND");
                if (formattedCommand != null) {
                    if (bleManager == null || !bleManager.isConnected()) {
                        // Do NOT call setResultCode(-1), letting the broadcast fall through to the service.
                        return;
                    }

                    // Record start time when the watch trigger is received by UI
                    commandStartTime = System.currentTimeMillis();

//                    onLog("[WATCH RX] UI handling trigger: " + formattedCommand);
                    bleManager.sendBleCommand(
                            formattedCommand,
                            () -> handleCommandResult(formattedCommand, Constants.COMMAND_SUCCESS, true),
                            () -> handleCommandResult(formattedCommand, Constants.COMMAND_FAILURE, true)
                    );
                    setResultCode(Activity.RESULT_OK); // Mark handled
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
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;

        // Re-bind this activity as the active BLE listener when returning from other layouts/activities
        if (bleManager != null) {
            bleManager.setListener(this);
        }
        // Refresh the log UI from the database every time the activity comes to the foreground
        loadStoredLogsForToday();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchCommandReceiver != null) unregisterReceiver(watchCommandReceiver);
        if (scheduleReceiver != null) unregisterReceiver(scheduleReceiver);
        if (dbHelper != null) dbHelper.close();

        // DO NOT disconnect or release bleManager here, because it's shared globally!
        // Just clear the UI listener reference to prevent memory leaks:
        if (bleManager != null) {
            bleManager.setListener(null);
        }
    }

    // --- BLE Manager Callbacks ---
    @Override
    public void onLog(String message) {
        // 1. Save to Database and Logcat via shared utility
        AppLogger.logToDatabaseAndLogcat(this, TAG, message);

        // 2. Handle UI updates on the Main Thread
        runOnUiThread(() -> {
            long currentTime = System.currentTimeMillis();
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(currentTime));

            String fullLogLine = "[" + timeStamp + "] " + message + "\n";

            // Use helper function for color styling
            SpannableString styledLog = AppLogger.formatLogLine(fullLogLine);
            txtLog.append(styledLog);

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
            WearSyncUtils.syncBleStatusToWatch(this, isConnected, statusText);

            if (isConnected) {
                // All Connection Success code is handled inside onServicesReady
                Log.i(TAG, "Received onConnectionStateChanged, isConnected: TRUE");
            } else {
                Log.i(TAG, "Received onConnectionStateChanged, isConnected: FALSE");
                updateConnectionUi(false);

                // Calculate total uptime if we have a valid start time
                long uptimeMillis = 0;
                if (connectionStartTime > 0) {
                    uptimeMillis = System.currentTimeMillis() - connectionStartTime;
                    connectionStartTime = 0; // Reset
                }

                FeedbackUtils.sendHapticToWatch(this, Constants.HAPTIC_DISCONNECT);
                FeedbackUtils.triggerDisconnectVibrate(this);
                FeedbackUtils.showConnectionNotification(this, false, uptimeMillis, isAppInForeground);
            }
        });
    }

    private void updateConnectionUi(boolean isConnected) {
        // Target Device connection Status Indicator
        int resourceId = isConnected ? R.drawable.indicator_online : R.drawable.indicator_offline;
        statusIndicator.setBackgroundResource(resourceId);

        UiUtils.setButtonState(btnStart, isConnected, isConnected ? 1.0f : 0.5f);

        // Show the reconnect button ONLY when disconnected, hide it when connected
        if (btnReconnect != null) {
            btnReconnect.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onServicesReady() {
        // Record connection uptime start
        connectionStartTime = System.currentTimeMillis();

        // 0. Phone App Updates - CONN indicator to green and Enable Operation buttons
        updateConnectionUi(true);

        // 1. Sequentially sync initialization settings AND log completion at the end
        boolean savedTelemetryState = preferenceManager.isTelemetryEnabled();
        boolean savedFailSafeState = preferenceManager.isFailSafeEnabled();

        bleManager.syncInitializationSequence(
                () -> bleManager.sendAutoTimeSync(),
                () -> bleManager.syncTelemetryState(savedTelemetryState),
                () -> bleManager.syncFailSafeState(savedFailSafeState),
                () -> bleManager.requestScheduleFromEsp32(),
                () -> {
                    // Feedback Haptics and Notifications (Immediate UX feedback)
                    FeedbackUtils.sendHapticToWatch(this, Constants.HAPTIC_CONNECT);
                    FeedbackUtils.triggerDoubleVibrate(this);
                    FeedbackUtils.showConnectionNotification(this, true, 0L, isAppInForeground);

                    onLog("Connection established. Ready for control operations.");
                }
        );
    }

    @Override
    public void onDataReceived(byte[] rawData) {
        // Hand raw bytes over to our dedicated protocol handler domain layer
        if (protocolHandler != null) {
            protocolHandler.parseIncomingData(rawData);
        }
    }

    // Load saved telemetry state into local variable
    private void loadTelemetryPreference() {
        isTelemetryEnabled = preferenceManager.isTelemetryEnabled();

        if (switchVoltage != null) {
            // Set switch checked state without triggering listeners (if any)
            switchVoltage.setChecked(isTelemetryEnabled);
        }
    }

    private void loadStoredLogsForToday() {
        if (dbHelper == null) return;

        // Clear existing text just in case
        txtLog.setText("");

        // Fetch logs from SQLite database for the current day
        java.util.List<String> todayLogs = dbHelper.getTodayLogs();

        if (todayLogs != null && !todayLogs.isEmpty()) {
            SpannableStringBuilder spannableBuilder = new SpannableStringBuilder();

            for (String logLine : todayLogs) {
                String fullLine = logLine + "\n";
                // Use helper function for color styling
                spannableBuilder.append(AppLogger.formatLogLine(fullLine));
            }

            txtLog.setText(spannableBuilder);

            // Scroll down to the latest log entry automatically
            if (scrollLog != null) {
                scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
            }
        } else {
            // Optional welcome log if no history exists for today yet
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String welcomeLine = "[" + timeStamp + "] SYSTEMS: Initialized fresh session logs.\n";
            txtLog.append(AppLogger.formatLogLine(welcomeLine));
        }
    }

    private void handleCommandResult(String executedCommand, String resultPayload, boolean isWatchRx) {
        long timeTaken = System.currentTimeMillis() - commandStartTime;

        // Add [Watch] prefix if the trigger came from the watch
        String logPrefix = isWatchRx ? "[WATCH RX] " : "";

        onLog(logPrefix + "Command: " + executedCommand + " | " + resultPayload + " | TimeTaken: " + timeTaken + "ms");

        if (isWatchRx) {
            FeedbackUtils.sendCmdResultAckToWatch(this, resultPayload);
        } else {
            if (Constants.COMMAND_SUCCESS.equals(resultPayload)) {
                FeedbackUtils.triggerDoubleVibrate(this);
            }
        }

        // Reset timer
        commandStartTime = 0;
    }

    private void handleReconnect() {
        onLog("Manual reconnect requested...");
        updateConnectionUi(false);
        checkPermissionsAndConnect();
    }

    private void pruneOldDatabaseRecords() {
        long thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        if (dbHelper != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                new Thread(() -> dbHelper.deleteOldRecords(thirtyDaysAgoMillis)).start();
            }, 5000); // Wait 5 seconds after app launch before cleaning up
        }
    }

    private final ActivityResultLauncher<Intent> editConfigLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    onConnectionStateChanged(false, "Reconnecting");
                    onLog("Configuration updated. Reconnecting...");
                    bleManager.connect(preferenceManager.isAutoConnectEnabled());
                }
            });

    private void showAboutDialog() {
        String versionName = BuildConfig.VERSION_NAME;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("About & Credits")
                .setMessage("App Version: " + versionName + "\n\n" +
                        "Launcher Icon made by 'Satawat Anukul' from www.flaticon.com/authors/satawat-anukul")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void startBleForegroundService() {
        startForegroundService(new Intent(this, in.kvapps.wirelessstart.ble.BleForegroundService.class));
    }

    private void stopBleForegroundService() {
        stopService(new Intent(this, in.kvapps.wirelessstart.ble.BleForegroundService.class));
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerScheduleReceiver() {
        scheduleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!preferenceManager.isEngineScheduleEnabled()) {
                    onLog("[WATCH RX] Scheduled Engine-Run feature is disabled");
                    return;
                }

                long targetEpoch = intent.getLongExtra("EPOCH", -1);
                if (targetEpoch >= 0) {
                    if (bleManager != null && bleManager.isConnected()) {
//                        onLog("[WATCH RX] Syncing engine-run schedule to hardware: " + targetEpoch);
                        bleManager.sendScheduledEpoch(targetEpoch);

                        // Request for updated schedule from ESP32 after some delay
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (bleManager != null && bleManager.isConnected()) {
                                bleManager.requestScheduleFromEsp32();
                            }
                        }, 500); // 0.5 sec delay

                        setResultCode(Activity.RESULT_OK); // Mark as handled by foreground UI
                    }
                } else {
                    Log.i(TAG, "Received Invalid Epoch Time for Engine-Schedule-Run");
                }
            }
        };

        IntentFilter filter = new IntentFilter("DIO_SCHEDULE_TRIGGER");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scheduleReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scheduleReceiver, filter);
        }
    }
}