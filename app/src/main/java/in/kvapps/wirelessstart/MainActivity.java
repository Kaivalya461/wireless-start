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
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import in.kvapps.wirelessstart.adapter.VehicleAdapter;
import in.kvapps.wirelessstart.ble.BleDeviceSession;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.domain.DeviceProtocolHandler;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.PermissionUtils;

public class MainActivity extends AppCompatActivity implements BleManager.BleListener, ConnectionStateRepository.OnConnectionStateChangedListener {
    private static final String TAG = Constants.PHONE_MAIN_ACTIVITY_TAG;

    // UI Controls
    private View cardLogSection;
    private TextView txtLog;
    private ScrollView scrollLog;
    private ImageButton btnMenu;
    private RecyclerView recyclerVehicles;
    private VehicleAdapter vehicleAdapter;
    private List<Vehicle> vehicleList;

    // Architecture & Helpers
    private VoltageDbHelper dbHelper;
    private BleManager bleManager;
    private DeviceProtocolHandler protocolHandler;
    private PreferenceManager preferenceManager;
    private BroadcastReceiver watchCommandReceiver;

    private boolean isTelemetryEnabled = false;
    private long commandStartTime = 0;
    private static boolean isAppInForeground = false;
    private Vehicle selectedVehicle;

    private BroadcastReceiver scheduleReceiver; // For Scheduled Engine Run/Stop
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean bluetoothGranted = PermissionUtils.evaluatePermissionsResult(result);

                if (bluetoothGranted) {
                    onLog("Permissions approved by user.");
                } else {
                    onLog("CRITICAL ERROR: Required BLUETOOTH permissions denied.");
                }
            });

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (preferenceManager.isAutoConnectEnabled() && (bleManager != null && !bleManager.isConnected(selectedVehicle.getMacAddress()))) {
                    onLog("Settings updated. Reconnecting...");
                    bleManager.connect(selectedVehicle.getMacAddress(), true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDependencies();
        initUiViews();
        loadStoredLogsForToday();
        setupClickListeners();
        registerWatchReceiver();
        registerScheduleReceiver();

        pruneOldDatabaseRecords();
        checkPermissionsAndConnect();
    }

    private void initDependencies() {
        dbHelper = new VoltageDbHelper(this);

        // Get the shared MyApplication instance
        MyApplication app = (MyApplication) getApplication();

        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();
        isTelemetryEnabled = preferenceManager.isTelemetryEnabled();

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
                        if (!vehicleList.isEmpty()) {
                            vehicleList.get(0).setVoltage(String.format(Locale.getDefault(), "%.2fV", voltage));
                            vehicleAdapter.notifyItemChanged(0);
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
        txtLog = findViewById(R.id.txt_log);
        scrollLog = findViewById(R.id.scroll_log);
        btnMenu = findViewById(R.id.btn_menu);
        cardLogSection = findViewById(R.id.card_log_section);
        recyclerVehicles = findViewById(R.id.recycler_vehicles);

        vehicleList = new ArrayList<>();

        vehicleAdapter = new VehicleAdapter(vehicleList, new VehicleAdapter.VehicleActionListener() {
            @Override
            public void onStartClick(Vehicle vehicle, int position) {
                handleStartAction(vehicle);
            }

            @Override
            public void onStopClick(Vehicle vehicle, int position) {
                handleStopAction(vehicle);
            }

            @Override
            public void onReconnectClick(Vehicle vehicle, int position) {
                handleReconnect(vehicle);
            }

            @Override
            public void onVoltageClick(Vehicle vehicle, int position) {
                startActivity(new Intent(MainActivity.this, VoltageHistoryActivity.class));
            }
        });

        recyclerVehicles.setLayoutManager(new LinearLayoutManager(this));
        recyclerVehicles.setAdapter(vehicleAdapter);

        if (recyclerVehicles.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) recyclerVehicles.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        loadVehicles();
        selectedVehicle = preferenceManager.getSelectedVehicle();
    }

    private void loadVehicles() {
        new Thread(() -> {
            List<Vehicle> savedVehicles = preferenceManager.getVehicles();

            runOnUiThread(() -> {
                vehicleList.clear();
                if (savedVehicles != null) {
                    vehicleList.addAll(savedVehicles);
                }
                vehicleAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void setupClickListeners() {
        btnMenu.setOnClickListener(this::showPopupMenu);
        cardLogSection.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LogHistoryActivity.class);
            startActivity(intent);
        });
    }

    private void handleStartAction(Vehicle vehicle) {
        if (vehicleList.isEmpty()) return;

        long pulseMs = preferenceManager.getSelectedStartPulseDuration();
        long totalCooldownMs = pulseMs + 3000;

        vehicleAdapter.triggerCooldown(vehicle, totalCooldownMs);

        String command = preferenceManager.getFormattedCommand("START");

        // Record start time before sending
        this.commandStartTime = System.currentTimeMillis();

        BleDeviceSession session = bleManager.getSession(vehicle.getMacAddress());
        if (session != null && session.isConnected()) {
            session.sendBleCommand(
                    command,
                    () -> handleCommandResult(command, Constants.COMMAND_SUCCESS, false),
                    () -> handleCommandResult(command, Constants.COMMAND_FAILURE, false)
            );
        } else {
            handleCommandResult(command, Constants.COMMAND_FAILURE, false);
        }
    }

    private void handleStopAction(Vehicle vehicle) {
        if (vehicleList.isEmpty()) return;
        long totalCooldownMs = 2000;

        vehicleAdapter.triggerCooldown(vehicle, totalCooldownMs);

        String command = preferenceManager.getFormattedCommand("STOP");
        this.commandStartTime = System.currentTimeMillis();

        BleDeviceSession session = bleManager.getSession(vehicle.getMacAddress());
        if (session != null && session.isConnected()) {
            session.sendBleCommand(
                    command,
                    () -> handleCommandResult(command, Constants.COMMAND_SUCCESS, false),
                    () -> handleCommandResult(command, Constants.COMMAND_FAILURE, false)
            );
        } else {
            handleCommandResult(command, Constants.COMMAND_FAILURE, false);
        }
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);
        popup.getMenu().add(0, 1, 0, "Vehicle Config");
        popup.getMenu().add(0, 2, 1, "Settings");
        popup.getMenu().add(0, 3, 2, "About & Credits");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                Intent intent = new Intent(MainActivity.this, EditConfigActivity.class);
                editConfigLauncher.launch(intent);
                return true;
            } else if (id == 2) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                settingsLauncher.launch(intent);
                return true;
            } else if (id == 3) {
                showAboutDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void handleTelemetryToggle(boolean isChecked) {
        isTelemetryEnabled = isChecked;
        BleDeviceSession session = bleManager != null && selectedVehicle != null ? bleManager.getSession(selectedVehicle.getMacAddress()) : null;

        if (session != null && session.isConnected()) {
            if (isChecked) {
                session.syncTelemetryState(true);
                onLog("Telemetry request: Resuming live stream.");
            } else {
                session.syncTelemetryState(false);
                if (!vehicleList.isEmpty()) {
                    vehicleList.get(0).setVoltage("--.--V");
                    vehicleAdapter.notifyItemChanged(0);
                }
                onLog("Telemetry request: Stopped live stream to save hardware power.");
            }
        }
        preferenceManager.setTelemetryEnabled(isChecked);
    }

    private void checkPermissionsAndConnect() {
        if (!PermissionUtils.hasBluetoothPermissions(this) || !PermissionUtils.hasNotificationPermissions(this)) {
            onLog("Requesting hardware and system permissions...");
            requestPermissionsLauncher.launch(PermissionUtils.getRequiredPermissions());
            return;
        }

        // Only trigger connect from MainActivity if Foreground Service is disabled.
        if (!preferenceManager.isForegroundServiceEnabled()) {
            bleManager.connect(selectedVehicle.getMacAddress(), preferenceManager.isAutoConnectEnabled());
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerWatchReceiver() {
        watchCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String formattedCommand = intent.getStringExtra("COMMAND");
                if (formattedCommand != null) {
                    if (bleManager == null || !bleManager.isConnected(selectedVehicle.getMacAddress())) {
                        return;
                    }

                    // Record start time when the watch trigger is received by UI
                    commandStartTime = System.currentTimeMillis();

                    BleDeviceSession session = bleManager.getSession(selectedVehicle.getMacAddress());
                    if (session != null && session.isConnected()) {
//                        onLog("[WATCH RX] UI handling trigger: " + formattedCommand);
                        session.sendBleCommand(
                                formattedCommand,
                                () -> handleCommandResult(formattedCommand, Constants.COMMAND_SUCCESS, true),
                                () -> handleCommandResult(formattedCommand, Constants.COMMAND_FAILURE, true)
                        );
                        setResultCode(Activity.RESULT_OK);
                    }
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

        // Register as listener to the Single Source of Truth repository
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                ConnectionStateRepository.getInstance().addListener(this);
            }, 50);

        // Re-bind this activity as the active BLE listener when returning from other layouts/activities
        if (bleManager != null) {
            bleManager.setListener(this);
        }
        // Refresh the log UI from the database every time the activity comes to the foreground
        loadStoredLogsForToday();

        // Only check for preference changes if BLE is connected and services are initialized
        if (preferenceManager != null && bleManager != null && bleManager.isConnected(selectedVehicle.getMacAddress())) {
            boolean newTelemetryState = preferenceManager.isTelemetryEnabled();
            if (newTelemetryState != isTelemetryEnabled) {
                handleTelemetryToggle(newTelemetryState);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        // Unregister from repository when paused to avoid leaking references
        ConnectionStateRepository.getInstance().removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchCommandReceiver != null) unregisterReceiver(watchCommandReceiver);
        if (scheduleReceiver != null) unregisterReceiver(scheduleReceiver);
        if (dbHelper != null) dbHelper.close();
        ConnectionStateRepository.getInstance().removeListener(this);

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

    // --- ConnectionStateRepository Observer Callback (SSOT) ---
    @Override
    public void onStateChanged(String macAddress, ConnectionStateRepository.DeviceConnectionState newState) {
        runOnUiThread(() -> {
            Vehicle vehicle = preferenceManager.getVehicleByMac(macAddress);
            if (vehicle == null) {
                onLog("onStateChanged() -> Missing Vehicle object");
                return;
            }

            vehicle.setConnected(newState.isConnected());
            vehicle.setStatusText(newState.getStatusText());

            if (ConnectionState.CONNECTED.equals(newState.getState())) {
                Log.i(TAG, "SSOT StateChanged: Connected for " + macAddress);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    onServicesReady(vehicle);
                }, 50);
            } else if (ConnectionState.DISCONNECTED.equals(newState.getState())) {
                Log.i(TAG, "SSOT StateChanged: Offline for " + macAddress);
                updateConnectionUi(vehicle);

                // Calculate total uptime if we have a valid start time
                long uptimeMillis = 0;
                if (newState.getConnectedAtTime() > 0) {
                    uptimeMillis = System.currentTimeMillis() - newState.getConnectedAtTime();
                }

                FeedbackUtils.triggerDisconnectVibrate(this);
                FeedbackUtils.showConnectionNotification(this, false, uptimeMillis, isAppInForeground);
            } else if (ConnectionState.DISCOVERING.equals(newState.getState())) {
                updateConnectionUi(vehicle);
            }
        });
    }

    private void updateConnectionUi(Vehicle vehicle) {
        vehicleAdapter.updateVehicleConnection(vehicle.getId(), vehicle.isConnected(), vehicle.getStatusText());
    }

    public void onServicesReady(Vehicle vehicle) {
        // 0. Phone App Updates - CONN indicator to green and Enable Operation buttons
        updateConnectionUi(vehicle);

        // 1. Sequentially sync initialization settings AND log completion at the end
        boolean savedTelemetryState = preferenceManager.isTelemetryEnabled();
        boolean savedFailSafeState = preferenceManager.isFailSafeEnabled();

        BleDeviceSession session = bleManager.getSession(vehicle.getMacAddress());
        if (session != null) {
            session.syncInitializationSequence(
                    session::sendAutoTimeSync,
                    () -> session.syncTelemetryState(savedTelemetryState),
                    () -> session.syncFailSafeState(savedFailSafeState),
                    session::requestScheduleFromEsp32,
                    () -> {
                        FeedbackUtils.triggerDoubleVibrate(this);
                        FeedbackUtils.showConnectionNotification(this, true, 0L, isAppInForeground);

                        onLog("Connection established. Ready for control operations.");
                    }
            );
        }
    }

    @Override
    public void onDataReceived(String macAddress, byte[] rawData) {
        // Hand raw bytes over to our dedicated protocol handler domain layer
        if (protocolHandler != null) {
            protocolHandler.parseIncomingData(rawData);
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

    private void handleReconnect(Vehicle vehicle) {
        onLog("Manual reconnect requested...");
        vehicle.setConnected(false);
        vehicle.setStatusText("Reconnecting");
        updateConnectionUi(vehicle);
        bleManager.connect(vehicle.getMacAddress(), preferenceManager.isAutoConnectEnabled());
    }

    private void pruneOldDatabaseRecords() {
        long thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        if (dbHelper != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                new Thread(() -> dbHelper.deleteOldRecords(thirtyDaysAgoMillis)).start();
            }, 5000); // Wait 5 seconds after app launch before cleaning up
        }
    }

    private final ActivityResultLauncher<Intent> editConfigLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadVehicles();
                    selectedVehicle = preferenceManager.getSelectedVehicle();

                    onLog("Vehicle Configuration updated.");
                    bleManager.connect(selectedVehicle.getMacAddress(), preferenceManager.isAutoConnectEnabled());
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
                    if (bleManager != null && bleManager.isConnected(selectedVehicle.getMacAddress())) {
                        BleDeviceSession session = bleManager.getSession(selectedVehicle.getMacAddress());
                        if (session != null && session.isConnected()) {
//                            onLog("[WATCH RX] Syncing engine-run schedule to hardware: " + targetEpoch);
                            session.sendScheduledEpoch(targetEpoch);

                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (session.isConnected()) {
                                    session.requestScheduleFromEsp32();
                                }
                            }, 500);

                            setResultCode(Activity.RESULT_OK);
                        }
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