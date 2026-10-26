package in.kvapps.wirelessstart;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import in.kvapps.wirelessstart.adapter.VehicleConfigAdapter;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.util.VehicleUtils;

public class EditConfigActivity extends AppCompatActivity {
    private static final String TAG = "EditConfigActivity";

    private TextInputEditText inputName, inputMac;
    private MaterialButton btnScan, btnSave, btnAddVehicle;
    private TextView tvCancelAction;
    private NestedScrollView nestedScrollView;
    private MaterialCardView cardActiveConfig;
    private RecyclerView recyclerViewVehicles;

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ArrayList<BluetoothDevice> scannedDevices = new ArrayList<>();
    private AlertDialog scanDialog;
    private PreferenceManager preferenceManager;
    private BleManager bleManager;
    private ScannedDeviceAdapter scanAdapter;
    private VehicleConfigAdapter vehicleConfigAdapter;

    private static final int REQUEST_CODE_PERMISSIONS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_config);

        // Initialize Managers from Application
        MyApplication app = (MyApplication) getApplication();
        preferenceManager = app.getPreferenceManager();
        bleManager = app.getBleManager();

        // Initialize views
        inputName = findViewById(R.id.input_edit_name);
        inputMac = findViewById(R.id.input_edit_mac);
        btnScan = findViewById(R.id.btn_scan_ble);
        btnSave = findViewById(R.id.btn_save_config);
        btnAddVehicle = findViewById(R.id.btn_add_vehicle);
        tvCancelAction = findViewById(R.id.tv_cancel_action);
        cardActiveConfig = findViewById(R.id.card_active_config);
        recyclerViewVehicles = findViewById(R.id.recycler_vehicles);
        nestedScrollView = findViewById(R.id.nested_scroll_view);

        // Hide configuration card by default on launch, show only list
        cardActiveConfig.setVisibility(View.GONE);

        setupVehicleRecyclerView();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        btnScan.setOnClickListener(v -> checkPermissionsAndScan());
        btnSave.setOnClickListener(v -> saveConfiguration());
        btnAddVehicle.setOnClickListener(v -> enterCreateMode());
        tvCancelAction.setOnClickListener(v -> {
            cardActiveConfig.setVisibility(View.GONE); // Hide card on cancel
        });

        // Focus listener to scroll down when typing in either input field
        View.OnFocusChangeListener scrollListener = (v, hasFocus) -> {
            if (hasFocus && nestedScrollView != null) {
                // Small delay ensures the keyboard has started opening before scrolling
                nestedScrollView.postDelayed(() -> {
                    nestedScrollView.smoothScrollTo(0, cardActiveConfig.getBottom());
                }, 200);
            }
        };
        inputName.setOnFocusChangeListener(scrollListener);
        inputMac.setOnFocusChangeListener(scrollListener);
    }

    private void enterCreateMode() {
        if (preferenceManager.getVehicles().size() >= 2) {
            Toast.makeText(this, "Maximum limit of 2 vehicles reached", Toast.LENGTH_SHORT).show();
            cardActiveConfig.setVisibility(View.GONE);
            return;
        }

        cardActiveConfig.setVisibility(View.VISIBLE);
        inputName.setText("");
        inputMac.setText("");

        if (nestedScrollView != null) {
            nestedScrollView.post(() -> nestedScrollView.smoothScrollTo(0, cardActiveConfig.getBottom()));
        }
    }

    private void setupVehicleRecyclerView() {
        recyclerViewVehicles.setLayoutManager(new LinearLayoutManager(this));

        vehicleConfigAdapter = new VehicleConfigAdapter(
                preferenceManager.getVehicles(),
                preferenceManager.getSelectedVehicleId(),
                new VehicleConfigAdapter.OnVehicleActionListener() {
                    @Override
                    public void onSelect(Vehicle vehicle) {
                        preferenceManager.setSelectedVehicleId(vehicle.getId());
                        vehicleConfigAdapter.updateData(
                                VehicleUtils.enrichVehiclesWithLiveState(TAG, preferenceManager.getVehicles()),
                                preferenceManager.getSelectedVehicleId()
                        );
                        cardActiveConfig.setVisibility(View.GONE);
                        Toast.makeText(EditConfigActivity.this, "Switched to " + vehicle.getName(), Toast.LENGTH_SHORT).show();

                        // Set result so MainActivity updates when exiting
                        setResult(RESULT_OK);
                    }

                    @Override
                    public void onDelete(Vehicle vehicle) {
                        if (preferenceManager.getVehicles().size() <= 1) {
                            Toast.makeText(EditConfigActivity.this, "Cannot delete the last remaining vehicle", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        VehicleUtils.deleteVehicle(preferenceManager, bleManager, vehicle);
                        vehicleConfigAdapter.updateData(
                                VehicleUtils.enrichVehiclesWithLiveState(TAG, preferenceManager.getVehicles()),
                                preferenceManager.getSelectedVehicleId()
                        );
                        cardActiveConfig.setVisibility(View.GONE);
                        Toast.makeText(EditConfigActivity.this, "Vehicle deleted", Toast.LENGTH_SHORT).show();

                        // Set result so MainActivity reloads the list and reconnects
                        setResult(RESULT_OK);
                    }
                }
        );

        recyclerViewVehicles.setAdapter(vehicleConfigAdapter);
    }

    private void saveConfiguration() {
        String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        String mac = inputMac.getText() != null ? inputMac.getText().toString().trim() : "";

        if (!name.isEmpty() && !mac.isEmpty()) {
            // Check if the vehicle limit (max 2) has already been reached
            if (preferenceManager.getVehicles().size() >= 2) {
                Toast.makeText(this, "Maximum limit of 2 vehicles reached. Please delete one to add a new vehicle.", Toast.LENGTH_LONG).show();
                return;
            }

            VehicleUtils.addVehicle(preferenceManager, bleManager, name, mac);
            Toast.makeText(this, "New Vehicle Added!", Toast.LENGTH_SHORT).show();

            vehicleConfigAdapter.updateData(
                    VehicleUtils.enrichVehiclesWithLiveState(TAG, preferenceManager.getVehicles()),
                    preferenceManager.getSelectedVehicleId()
            );
            cardActiveConfig.setVisibility(View.GONE); // Hide card after saving
            setResult(RESULT_OK);
        } else {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBleScan();
    }

    private void checkPermissionsAndScan() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
            return;
        }

        startBleScan();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (!scannedDevices.contains(device)) {
                scannedDevices.add(device);
                if (scanAdapter != null) {
                    runOnUiThread(() -> scanAdapter.notifyDataSetChanged());
                }
            }
        }
    };

    private void startBleScan() {
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE Scanning not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        scannedDevices.clear();
        scanAdapter = new ScannedDeviceAdapter(this, scannedDevices);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Select Device");
        builder.setAdapter(scanAdapter, (dialog, which) -> {
            BluetoothDevice selectedDevice = scannedDevices.get(which);
            try {
                inputMac.setText(selectedDevice.getAddress());
                if (selectedDevice.getName() != null) {
                    inputName.setText(selectedDevice.getName());
                }
            } catch (SecurityException e) {
                Log.e("BLE_SCAN", "Security exception getting device info", e);
            }
            stopBleScan();
        });
        builder.setOnCancelListener(dialog -> stopBleScan());

        scanDialog = builder.create();
        scanDialog.show();

        isScanning = true;
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            bluetoothLeScanner.startScan(null, settings, scanCallback);
        } catch (SecurityException e) {
            Toast.makeText(this, "Bluetooth scan permission denied", Toast.LENGTH_SHORT).show();
            return;
        }

        handler.postDelayed(this::stopBleScan, 10000);
    }

    private void stopBleScan() {
        if (isScanning && bluetoothLeScanner != null) {
            isScanning = false;
            try {
                bluetoothLeScanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                Log.e("BLE_SCAN", "Security exception stopping scan", e);
            }
        }
    }

    public class ScannedDeviceAdapter extends BaseAdapter {
        private final Context context;
        private final List<BluetoothDevice> devices;

        public ScannedDeviceAdapter(Context context, List<BluetoothDevice> devices) {
            this.context = context;
            this.devices = devices;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_scanned_device, parent, false);
            }

            BluetoothDevice device = devices.get(position);
            TextView nameView = convertView.findViewById(R.id.text_device_name);
            TextView macView = convertView.findViewById(R.id.text_device_mac);

            try {
                String name = device.getName();
                nameView.setText(name != null && !name.isEmpty() ? name : "Unknown Device");
                macView.setText(device.getAddress());
            } catch (SecurityException e) {
                nameView.setText("Protected Device");
                macView.setText(device.getAddress());
            }

            return convertView;
        }
    }
}