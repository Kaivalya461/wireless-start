package in.kvapps.wirelessstart;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import in.kvapps.wirelessstart.adapter.VehicleAdapter;
import in.kvapps.wirelessstart.ble.BleDeviceSession;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.VehicleConnectionManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.domain.DeviceProtocolHandler;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.PermissionUtils;
import in.kvapps.wirelessstart.util.VehicleUtils;

public class MainActivity extends AppCompatActivity implements BleManager.BleListener, VehicleConnectionManager.ConnectionLifecycleListener {
    private static final String TAG = Constants.PHONE_MAIN_ACTIVITY_TAG;

    // UI Controls
    private View cardLogSection;
    private TextView txtLog;
    private ScrollView scrollLog;
    private ImageButton btnMenu;
    private RecyclerView recyclerVehicles;
    private VehicleAdapter vehicleAdapter;
    private List<Vehicle> vehicleList;
    private Map<String, VehicleSettings> vehicleSettingsMap;

    // Architecture & Helpers
    private VoltageDbHelper dbHelper;
    private BleManager bleManager;
    private VehicleRepository vehicleRepository;
    private DeviceProtocolHandler protocolHandler;
    private PreferenceManager preferenceManager;
    private VehicleConnectionManager connectionManager;
    private BroadcastReceiver watchCommandReceiver;
    private boolean isTelemetryEnabled = false;
    private long commandStartTime = 0;
    private static boolean isAppInForeground = false;
    private Vehicle selectedVehicle;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (PermissionUtils.evaluatePermissionsResult(result)) {
                    onLog("Permissions approved by user.");
                } else {
                    onLog("CRITICAL ERROR: Required BLUETOOTH permissions denied.");
                }
            });

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result ->
                    onLog("Settings updated successfully.")
            );

    private final ActivityResultLauncher<Intent> editConfigLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadVehicles();
                    selectedVehicle = preferenceManager.getSelectedVehicle();
                    onLog("Vehicle Configuration updated.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(Color.parseColor("#121212"));

        initDependencies();
        initUiViews();
        loadStoredLogsForToday();
        setupClickListeners();
        registerReceivers();

        pruneOldDatabaseRecords();
        checkPermissionsAndConnect();
    }

    private void initDependencies() {
        dbHelper = new VoltageDbHelper(this);
        MyApplication app = (MyApplication) getApplication();

        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();
        vehicleRepository = app.getVehicleRepository();
        isTelemetryEnabled = preferenceManager.isTelemetryEnabled();

        if (bleManager != null) {
            bleManager.setListener(this);
        }

        protocolHandler = new DeviceProtocolHandler(this, new DeviceProtocolHandler.ProtocolListener() {
            @Override
            public void onVoltageReady(String mac, float voltage) {
                runOnUiThread(() -> {
                    if (isTelemetryEnabled) {
                        long now = System.currentTimeMillis();
                        if (dbHelper != null) dbHelper.insertReading(mac, now, voltage);
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
            startForegroundService(new Intent(this, in.kvapps.wirelessstart.ble.BleForegroundService.class));
        }
    }

    private void initUiViews() {
        txtLog = findViewById(R.id.txt_log);
        scrollLog = findViewById(R.id.scroll_log);
        btnMenu = findViewById(R.id.btn_menu);
        cardLogSection = findViewById(R.id.card_log_section);
        recyclerVehicles = findViewById(R.id.recycler_vehicles);

        vehicleList = new ArrayList<>();
        vehicleSettingsMap = new HashMap<>();
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

        // Initialize connection manager observer
        connectionManager = new VehicleConnectionManager(this, preferenceManager, vehicleAdapter, this);
    }

    private void loadVehicles() {
        new Thread(() -> {
            // Fetch vehicles and vehicle-settings from Database
            List<Vehicle> savedVehicles = vehicleRepository.getAllVehiclesWithLiveState(TAG);
            Map<String, VehicleSettings> settingsMap = vehicleRepository.getAllVehicleSettingsMap();

            runOnUiThread(() -> {
                vehicleList.clear();
                if (savedVehicles != null) vehicleList.addAll(savedVehicles);
                if (!settingsMap.isEmpty()) vehicleSettingsMap.putAll(settingsMap);
                vehicleAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void setupClickListeners() {
        btnMenu.setOnClickListener(this::showPopupMenu);
        cardLogSection.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LogHistoryActivity.class)));
    }

    @Override
    public boolean isAppInForeground() {
        return isAppInForeground;
    }

    @Override
    public void onServicesReady(Vehicle vehicle) {
        runOnUiThread(() -> {
            vehicleAdapter.updateVehicleConnection(vehicle.getId(), vehicle.isConnected(), vehicle.getStatusText());

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
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        connectionManager.register();

        if (bleManager != null) {
            bleManager.setListener(this);
        }
        loadStoredLogsForToday();

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
        connectionManager.unregister();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchCommandReceiver != null) unregisterReceiver(watchCommandReceiver);
        if (dbHelper != null) dbHelper.close();

        if (bleManager != null) {
            bleManager.setListener(null);
        }
    }

    public void onLog(String message) {
        AppLogger.logToDatabaseAndLogcat(this, TAG, message);
        runOnUiThread(() -> {
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            SpannableString styledLog = AppLogger.formatLogLine("[" + timeStamp + "] " + message + "\n");
            txtLog.append(styledLog);
            if (scrollLog != null) scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    public void onDataReceived(String macAddress, byte[] rawData) {
        if (protocolHandler != null) protocolHandler.parseIncomingData(macAddress, rawData);
    }

    // --- Actions & Helpers ---
    private void handleStartAction(Vehicle vehicle) {
        executeCommand(vehicle, "START", 3000, false);
    }

    private void handleStopAction(Vehicle vehicle) {
        executeCommand(vehicle, "STOP", 2000, false);
    }

    private void executeCommand(Vehicle vehicle, String action ,long cooldown, boolean isWatch) {
        if (vehicleList.isEmpty()) return;

        var settings = vehicleSettingsMap.get(vehicle.getId());
        var command = VehicleUtils.getFormattedCommand(action, settings);
        vehicleAdapter.triggerCooldown(vehicle, cooldown);
        commandStartTime = System.currentTimeMillis();

        BleDeviceSession session = bleManager.getSession(vehicle.getMacAddress());
        if (session != null && session.isConnected()) {
            session.sendBleCommand(
                    command,
                    () -> handleCommandResult(command, Constants.COMMAND_SUCCESS, isWatch),
                    () -> handleCommandResult(command, Constants.COMMAND_FAILURE, isWatch)
            );
        } else {
            handleCommandResult(command, Constants.COMMAND_FAILURE, isWatch);
        }
    }

    private void handleCommandResult(String command, String result, boolean isWatch) {
        long timeTaken = System.currentTimeMillis() - commandStartTime;
        onLog((isWatch ? "[WATCH RX] " : "") + "Command: " + command + " | " + result + " | TimeTaken: " + timeTaken + "ms");

        if (isWatch) {
            FeedbackUtils.sendCmdResultAckToWatch(this, result);
        } else if (Constants.COMMAND_SUCCESS.equals(result)) {
            FeedbackUtils.triggerDoubleVibrate(this);
        }
        commandStartTime = 0;
    }

    private void handleReconnect(Vehicle vehicle) {
        onLog("Manual reconnect requested...");
        vehicle.setConnected(false);
        vehicle.setStatusText("Reconnecting");
        vehicleAdapter.updateVehicleConnection(vehicle.getId(), false, "Reconnecting");
        bleManager.connect(vehicle.getMacAddress(), preferenceManager.isAutoConnectEnabled());
    }

    private void handleTelemetryToggle(boolean isChecked) {
        isTelemetryEnabled = isChecked;
        BleDeviceSession session = bleManager != null && selectedVehicle != null ? bleManager.getSession(selectedVehicle.getMacAddress()) : null;

        if (session != null && session.isConnected()) {
            session.syncTelemetryState(isChecked);
            if (!isChecked && !vehicleList.isEmpty()) {
                vehicleList.get(0).setVoltage("--.--V");
                vehicleAdapter.notifyItemChanged(0);
            }
        }
        preferenceManager.setTelemetryEnabled(isChecked);
    }

    private void checkPermissionsAndConnect() {
        if (!PermissionUtils.hasBluetoothPermissions(this) || !PermissionUtils.hasNotificationPermissions(this)) {
            requestPermissionsLauncher.launch(PermissionUtils.getRequiredPermissions());
            return;
        }
        if (!preferenceManager.isForegroundServiceEnabled()) {
            bleManager.startMultiVehicleScan();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceivers() {
        // Watch Command Receiver
        watchCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String cmd = intent.getStringExtra("COMMAND");
                Log.i(TAG, "Setting  from DB, vehicleSettingsMap -> " + cmd);
                if (cmd != null && bleManager != null && bleManager.isConnected(selectedVehicle.getMacAddress())) {
                    executeCommand(selectedVehicle, cmd, 3000, true);
                    setResultCode(Activity.RESULT_OK);
                }
            }
        };
        IntentFilter watchFilter = new IntentFilter("DIO_HARDWARE_TRIGGER");
        registerReceiverCompat(watchCommandReceiver, watchFilter);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverCompat(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void loadStoredLogsForToday() {
        if (dbHelper == null) return;
        txtLog.setText("");
        List<String> todayLogs = dbHelper.getTodayLogs();
        if (todayLogs != null && !todayLogs.isEmpty()) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (String log : todayLogs) builder.append(AppLogger.formatLogLine(log + "\n"));
            txtLog.setText(builder);
            if (scrollLog != null) scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void pruneOldDatabaseRecords() {
        long thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        if (dbHelper != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    new Thread(() -> dbHelper.deleteOldRecords(thirtyDaysAgoMillis)).start(), 5000);
        }
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);
        popup.getMenu().add(0, 1, 0, "Manage Vehicles");
        popup.getMenu().add(0, 2, 1, "Settings");
        popup.getMenu().add(0, 3, 2, "About & Credits");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) { editConfigLauncher.launch(new Intent(this, EditConfigActivity.class)); return true; }
            else if (id == 2) { settingsLauncher.launch(new Intent(this, SettingsActivity.class)); return true; }
            else if (id == 3) { showAboutDialog(); return true; }
            return false;
        });
        popup.show();
    }

    private void showAboutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("About & Credits")
                .setMessage("App Version: " + BuildConfig.VERSION_NAME + "\n\nLauncher Icon by Satawat Anukul")
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .show();
    }
}