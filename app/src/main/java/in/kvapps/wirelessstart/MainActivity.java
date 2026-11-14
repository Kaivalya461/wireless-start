package in.kvapps.wirelessstart;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.kvapps.wirelessstart.adapter.VehicleAdapter;
import in.kvapps.wirelessstart.ble.BleCommandManager;
import in.kvapps.wirelessstart.ble.BleDeviceSession;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.ble.VehicleUiConnectionManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.domain.DeviceProtocolHandler;
import in.kvapps.wirelessstart.dto.CmdExecResultData;
import in.kvapps.wirelessstart.enums.CommandActionType;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleWithSettings;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.util.AppLogger;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.PermissionUtils;

public class MainActivity extends AppCompatActivity implements BleManager.BleListener, VehicleUiConnectionManager.ConnectionLifecycleListener {
    private static final String TAG = Constants.PHONE_MAIN_ACTIVITY_TAG;

    // UI Controls
    private View cardLogSection;
    private TextView txtLog;
    private ScrollView scrollLog;
    private ImageButton btnMenu;
    private RecyclerView recyclerVehicles;
    private VehicleAdapter vehicleAdapter;

    // Architecture & Helpers
    private VoltageDbHelper dbHelper;
    private BleManager bleManager;
    private VehicleRepository vehicleRepository;
    private DeviceProtocolHandler protocolHandler;
    private PreferenceManager preferenceManager;
    private VehicleUiConnectionManager connectionManager;
    private BleCommandManager bleCommandManager;
    private boolean isTelemetryEnabled = false;
    private static boolean isAppInForeground = false;
    private Vehicle selectedVehicle;
    private final Set<String> pendingFirstTimeConfigVehicles = new HashSet<>();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (PermissionUtils.evaluatePermissionsResult(result)) {
                    onLog("Permissions approved by user.");
                } else {
                    onLog("CRITICAL ERROR: Required BLUETOOTH permissions denied.");
                }
            });

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (!preferenceManager.isAutoConnectEnabled()) {
                        bleManager.stopMultiVehicleScan();
                    } else {
                        bleManager.startMultiVehicleScan();
                    }
                    onLog("Settings updated successfully.");
                }
            });

    private final ActivityResultLauncher<Intent> editConfigLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String newVehicleId = result.getData().getStringExtra("EXTRA_NEW_VEHICLE_ID");
                    if (newVehicleId != null) {
                        pendingFirstTimeConfigVehicles.add(newVehicleId);
                        bleManager.refreshVehiclesScan();
                    }
                } else if (result.getResultCode() == RESULT_OK) {
                    selectedVehicle = vehicleRepository.getDefaultVehicle();
                    onLog("Vehicle Configuration updated.");
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

        pruneOldDatabaseRecords();
        checkPermissionsAndConnect();
        checkFirstRunOrNoVehicles();
    }

    private void initDependencies() {
        dbHelper = new VoltageDbHelper(this);
        MyApplication app = (MyApplication) getApplication();

        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();
        vehicleRepository = app.getVehicleRepository();
        bleCommandManager = app.getBleCommandManager();
        isTelemetryEnabled = preferenceManager.isTelemetryEnabled();

        if (bleManager != null) {
            bleManager.setListener(this);
        }

        protocolHandler = new DeviceProtocolHandler(this, new DeviceProtocolHandler.ProtocolListener() {
            @Override
            public void onVoltageReady(String mac, float voltage) {
                if (isTelemetryEnabled) {
                    long now = System.currentTimeMillis();

                    // 1. Offload SQLite disk write to the background thread executor
                    backgroundExecutor.execute(() -> {
                        if (dbHelper != null) {
                            dbHelper.insertReading(mac, now, voltage);
                        }
                    });

                    // 2. Perform targeted UI update on the main thread using payloads
                    runOnUiThread(() -> {
                        List<VehicleWithSettings> currentList = vehicleAdapter.getCurrentList();
                        for (int i = 0; i < currentList.size(); i++) {
                            VehicleWithSettings item = currentList.get(i);
                            if (item.getVehicle() != null && mac.equalsIgnoreCase(item.getVehicle().getMacAddress())) {
                                item.getVehicle().setVoltage(String.format(Locale.getDefault(), "%.2fV", voltage));

                                // Pass payload so only the TextView updates, avoiding full card rebinds
                                vehicleAdapter.notifyItemChanged(i, VehicleAdapter.PAYLOAD_VOLTAGE);
                                break;
                            }
                        }
                    });
                }
            }

            @Override
            public void onProtocolLog(String message) {
                onLog(message);
            }

            @Override
            public void onVehicleConfigReceived(String macAddress, String deviceName, boolean startEnabled, boolean stopEnabled,
                                                boolean battEnabled, boolean powerSwitchEnabled, boolean supportsAck) {
                var vehicle = vehicleRepository.getVehicleByMac(macAddress);
                if (vehicle != null) {
                    vehicleRepository.saveVehicleConfigSettings(vehicle.getId(), startEnabled, stopEnabled, powerSwitchEnabled, supportsAck);
                    pendingFirstTimeConfigVehicles.remove(vehicle.getId());
                    handleReconnect(vehicle);
                }
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

        vehicleAdapter = new VehicleAdapter(new VehicleAdapter.VehicleActionListener() {
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

            @Override
            public void onPowerClick(Vehicle vehicle, boolean isChecked, int position) {
                handlePowerSwitchAction(vehicle, isChecked);
            }
        });

        recyclerVehicles.setLayoutManager(new LinearLayoutManager(this));
        recyclerVehicles.setAdapter(vehicleAdapter);

        if (recyclerVehicles.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) recyclerVehicles.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        // Observe LiveData from Room and submit list directly to ListAdapter
        vehicleRepository.getAllVehiclesWithSettingsAndLiveState(TAG).observe(this, savedList -> {
            vehicleAdapter.submitList(savedList);
        });

        selectedVehicle = vehicleRepository.getDefaultVehicle();

        // Initialize connection manager observer
        connectionManager = new VehicleUiConnectionManager(this, vehicleRepository, vehicleAdapter, this);
    }

    private void setupClickListeners() {
        btnMenu.setOnClickListener(this::showPopupMenu);
        cardLogSection.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LogHistoryActivity.class)));
    }

    @Override
    public void onServicesReady(Vehicle vehicle) {
        runOnUiThread(() -> {
            BleDeviceSession session = bleManager.getSession(vehicle.getMacAddress());
            if (session != null && pendingFirstTimeConfigVehicles.contains(vehicle.getId())) {
                session.fetchVehicleConfig();
            }

            FeedbackUtils.triggerDoubleVibrate(this);
            FeedbackUtils.showConnectionNotification(this, true, 0L, isAppInForeground);
            onLog("[" + vehicle.getName() + "] Ready for control.");
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

        if (preferenceManager != null && bleManager != null) {
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
        backgroundExecutor.shutdown();
        if (dbHelper != null) dbHelper.close();

        if (bleManager != null) {
            bleManager.setListener(null);
        }
    }

    @Override
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
        vehicleAdapter.triggerCooldown(vehicle, 3000);

        bleCommandManager.sendCommand(
                vehicle.getMacAddress(),
                CommandActionType.PRIMARY,
                resultPayload -> handleCommandResult(resultPayload, false),
                false
        );
    }

    private void handleStopAction(Vehicle vehicle) {
        vehicleAdapter.triggerCooldown(vehicle, 2000);

        bleCommandManager.sendCommand(
                vehicle.getMacAddress(),
                CommandActionType.SECONDARY,
                resultPayload -> handleCommandResult(resultPayload, false),
                false
        );
    }

    private void handlePowerSwitchAction(Vehicle vehicle, boolean switchState) {
        bleCommandManager.sendCommand(
                vehicle.getMacAddress(),
                CommandActionType.TERTIARY,
                switchState,
                resultPayload -> handleCommandResult(resultPayload, false),
                false
        );
    }

    private void handleCommandResult(CmdExecResultData resultPayload, boolean isWatch) {
        onLog((isWatch ? "[WATCH RX] " : "") + "Command: " + resultPayload.getExecutedCommand() + " | " + resultPayload.getResult() + " | TimeTaken: " + resultPayload.getTimeTaken() + "ms");

        if (isWatch) {
            FeedbackUtils.sendCmdResultAckToWatch(this, resultPayload.getResult());
        } else if (Constants.COMMAND_SUCCESS.equals(resultPayload.getResult())) {
            FeedbackUtils.triggerDoubleVibrate(this);
        }
    }

    private void handleReconnect(Vehicle vehicle) {
        onLog("Manual reconnect requested...");
        ConnectionStateRepository.getInstance().updateState(vehicle.getMacAddress(), ConnectionState.RECONNECTING, "Reconnecting");
        bleManager.disconnect(vehicle.getMacAddress());
        bleManager.connect(vehicle.getMacAddress(), preferenceManager.isAutoConnectEnabled());
        bleManager.refreshVehiclesScan();
    }

    private void handleTelemetryToggle(boolean isChecked) {
        isTelemetryEnabled = isChecked;

        List<VehicleWithSettings> currentList = vehicleAdapter.getCurrentList();
        if (currentList != null && bleManager != null) {
            for (int i = 0; i < currentList.size(); i++) {
                VehicleWithSettings item = currentList.get(i);
                if (item != null && item.getVehicle() != null) {
                    Vehicle vehicle = item.getVehicle();
                    BleDeviceSession session = bleManager.getSession(vehicle.getMacAddress());

                    if (session != null && session.isConnected()) {
                        session.syncTelemetryState(isChecked);
                        if (!isChecked) {
                            vehicle.setVoltage("--.--V");
                            vehicleAdapter.notifyItemChanged(i);
                        }
                    }
                }
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

    private void loadStoredLogsForToday() {
        if (dbHelper == null) return;

        // Clear the view immediately on the UI thread so it doesn't show stale data
        txtLog.setText("");

        backgroundExecutor.execute(() -> {
            // Fetch all logs from DB on background thread
            List<String> todayLogs = dbHelper.getTodayLogs();

            if (todayLogs != null && !todayLogs.isEmpty()) {
                // Optimization: Keep only the last 100 logs to prevent memory bloat and massive layout passes
                int totalLogs = todayLogs.size();
                int startIndex = Math.max(0, totalLogs - 100);
                List<String> recentLogs = todayLogs.subList(startIndex, totalLogs);

                SpannableStringBuilder builder = new SpannableStringBuilder();
                for (String log : recentLogs) {
                    builder.append(AppLogger.formatLogLine(log + "\n"));
                }

                // Post the heavy Spannable string back to the main thread UI
                runOnUiThread(() -> {
                    txtLog.setText(builder);
                    if (scrollLog != null) {
                        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
                    }
                });
            }
        });
    }

    private void pruneOldDatabaseRecords() {
        long thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
        if (dbHelper != null) {
            // Wait 20 seconds after launch so BLE scans and UI setup finish completely
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                backgroundExecutor.execute(() -> {
                    dbHelper.deleteOldRecords(thirtyDaysAgoMillis);
                });
            }, 20000);
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

    private void checkFirstRunOrNoVehicles() {
        // Query database to see if any vehicles exist
        List<Vehicle> vehicles = vehicleRepository.getAllVehicles();
        if (vehicles == null || vehicles.isEmpty()) {
            onLog("No vehicles found. Launching initial configuration...");
            // Launch EditConfigActivity for first-time setup
            editConfigLauncher.launch(new Intent(this, EditConfigActivity.class));
        }
    }
}