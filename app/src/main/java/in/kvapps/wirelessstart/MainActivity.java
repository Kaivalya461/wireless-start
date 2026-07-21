package in.kvapps.wirelessstart;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    // UI Elements
    private View statusIndicator;
    private TextView txtStatus, txtLog;
    private Button btnStart, btnStop;

    // Bluetooth Objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private android.content.BroadcastReceiver watchCommandReceiver;


    // Hardware Configurations (Match these to your ESP32 target later)
    private static final String ESP32_MAC = "KV:ES:PX:CC:DD:EE:FF";

    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Link Java variables to XML elements
        statusIndicator = findViewById(R.id.status_indicator);
        txtStatus = findViewById(R.id.txt_status);
        txtLog = findViewById(R.id.txt_log);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // 2. Setup Click Listeners
        btnStart.setOnClickListener(v -> sendBleCommand("START"));
        btnStop.setOnClickListener(v -> sendBleCommand("STOP"));

        // 3. Verify System Permissions before attempting connection
        checkPermissionsAndConnect();

        // Listen for internal background service events matching the watch channel
        watchCommandReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                String actionCommand = intent.getStringExtra("COMMAND");
                if (actionCommand != null) {
                    logToConsole("[WATCH RX] Remote execution received: " + actionCommand);
                    sendBleCommand(actionCommand); // Automatically pipes the command directly to the ESP32
                }
            }
        };

        // Register the intent pipe receiver
        registerReceiver(watchCommandReceiver, new android.content.IntentFilter("DIO_HARDWARE_TRIGGER"), android.content.Context.RECEIVER_NOT_EXPORTED);

    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

                logToConsole("Requesting hardware system permissions...");
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        // If permissions are already granted or running older Android OS
        initializeHardwareConnection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                logToConsole("Permissions approved by user.");
                initializeHardwareConnection();
            } else {
                logToConsole("CRITICAL ERROR: Bluetooth permissions denied.");
                updateUiState(false, "Permissions Denied");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watchCommandReceiver != null) {
            unregisterReceiver(watchCommandReceiver);
        }
    }

    private void initializeHardwareConnection() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            logToConsole("System Alert: Please turn on phone Bluetooth.");
            updateUiState(false, "Phone Bluetooth Off");
            return;
        }

        logToConsole("Searching for Dio hardware [" + ESP32_MAC + "]...");
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(ESP32_MAC);

            // Explicitly check for BLUETOOTH_CONNECT permission before making the call
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    logToConsole("Error: Missing runtime Bluetooth connection permission.");
                    updateUiState(false, "Permission Missing");
                    return;
                }
            }

            // Wrapped inside a try-catch for SecurityException as required by the IDE compiler
            bluetoothGatt = device.connectGatt(this, true, gattCallback);

        } catch (IllegalArgumentException e) {
            logToConsole("Configuration Error: Invalid MAC address provided.");
        } catch (SecurityException e) {
            logToConsole("Security Error: Operating system blocked connection profile.");
            updateUiState(false, "Security Exception");
        }
    }

    // 4. BLE Lifecycle Event Callbacks
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> updateUiState(true, "Dio Hardware Ready"));
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    runOnUiThread(() -> logToConsole("Security Error: Failed to discover services due to missing permission."));
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> updateUiState(false, "Dio Hardware Offline"));
                commandCharacteristic = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    runOnUiThread(() -> logToConsole("Data pipeline channel mapped."));
                } else {
                    runOnUiThread(() -> logToConsole("Error: Service UUID matching failed."));
                }
            }
        }
    };

    private void sendBleCommand(String command) {
        if (commandCharacteristic != null && bluetoothGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Modern Android 13+ secure write execution method
                    bluetoothGatt.writeCharacteristic(
                            commandCharacteristic,
                            command.getBytes(),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                } else {
                    // Legacy fallback compatibility for older Android versions
                    commandCharacteristic.setValue(command.getBytes());
                    bluetoothGatt.writeCharacteristic(commandCharacteristic);
                }
                logToConsole("Command Transmitted -> " + command);
            } catch (SecurityException e) {
                logToConsole("Security Exception: Missing OS permission mapping.");
            }
        } else {
            logToConsole("Action Blocked: Hardware connection is offline.");
        }
    }

    // 5. Interface UI Thread Synchronization Helpers
    private void updateUiState(final boolean isConnected, final String statusText) {
        txtStatus.setText(statusText);
        statusIndicator.setBackgroundColor(isConnected ? 0xFF4CAF50 : 0xFFE53935); // Green vs Red Hex Colors
        logToConsole("System state: " + statusText);
    }

    private void logToConsole(final String message) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        txtLog.append("[" + timeStamp + "] " + message + "\n");
    }
}
