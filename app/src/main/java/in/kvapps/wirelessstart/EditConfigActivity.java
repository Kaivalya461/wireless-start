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
import android.content.Intent;
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
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.kvapps.wirelessstart.adapter.VehicleConfigAdapter;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.repository.VehicleRepository;

public class EditConfigActivity extends AppCompatActivity {
    private static final String TAG = "EditConfigActivity";

    private TextInputEditText inputName, inputMac;
    private MaterialButton btnScan, btnSave, btnAddVehicle, btnSaveSettings;
    private TextView tvCancelAction, tvSelectedVehicleName;
    private TextView txtStartVal, txtStopVal;
    private Slider sliderStart, sliderStop;
    private NestedScrollView nestedScrollView;
    private MaterialCardView cardActiveConfig, cardVehicleSettings;
    private RecyclerView recyclerViewVehicles;

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ArrayList<BluetoothDevice> scannedDevices = new ArrayList<>();
    private AlertDialog scanDialog;
    private PreferenceManager prefs;
    private VehicleRepository vehicleRepository;
    private BleManager bleManager;
    private ScannedDeviceAdapter scanAdapter;
    private VehicleConfigAdapter vehicleConfigAdapter;

    private String selectedVehicleId = null;
    private float initialStartValue, initialStopValue;
    private float startSliderMin, stopSliderMin;
    private float startSliderDefault, stopSliderDefault;
    private static final int REQUEST_CODE_PERMISSIONS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_config);

        // Initialize Repository & Managers
        MyApplication app = (MyApplication) getApplication();
        prefs = app.getPreferenceManager();
        bleManager = app.getBleManager();
        vehicleRepository = app.getVehicleRepository();
        setupResourcesData();

        // Initialize views
        inputName = findViewById(R.id.input_edit_name);
        inputMac = findViewById(R.id.input_edit_mac);
        btnScan = findViewById(R.id.btn_scan_ble);
        btnSave = findViewById(R.id.btn_save_config);
        btnAddVehicle = findViewById(R.id.btn_add_vehicle);
        tvCancelAction = findViewById(R.id.tv_cancel_action);
        cardActiveConfig = findViewById(R.id.card_active_config);
        cardVehicleSettings = findViewById(R.id.card_vehicle_settings);
        recyclerViewVehicles = findViewById(R.id.recycler_vehicles);
        nestedScrollView = findViewById(R.id.nested_scroll_view);

        // Settings card components
        tvSelectedVehicleName = findViewById(R.id.tv_selected_vehicle_name);
        sliderStart = findViewById(R.id.slider_start);
        sliderStop = findViewById(R.id.slider_stop);
        txtStartVal = findViewById(R.id.txt_start_duration_value);
        txtStopVal = findViewById(R.id.txt_stop_duration_value);
        btnSaveSettings = findViewById(R.id.btn_save_settings);

        // Hide add config form by default on launch
        cardActiveConfig.setVisibility(View.GONE);

        setupVehicleRecyclerView();
        loadInitialSelection();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        btnScan.setOnClickListener(v -> checkPermissionsAndScan());
        btnSave.setOnClickListener(v -> saveNewVehicleConfiguration());
        btnAddVehicle.setOnClickListener(v -> enterCreateMode());
        btnSaveSettings.setOnClickListener(v -> saveVehicleSettings());

        tvCancelAction.setOnClickListener(v -> {
            cardActiveConfig.setVisibility(View.GONE);
            cardVehicleSettings.setVisibility(View.VISIBLE);
        });

        // Slider value formatting & change listeners
        sliderStart.addOnChangeListener((slider, value, fromUser) -> {
            updateDurationLabel(txtStartVal, value);
            if (fromUser) {
                updateSaveSettingsButtonState();
            }
        });

        sliderStop.addOnChangeListener((slider, value, fromUser) -> {
            updateDurationLabel(txtStopVal, value);
            if (fromUser) {
                updateSaveSettingsButtonState();
            }
        });

        // Focus listener to scroll down when typing in input fields
        View.OnFocusChangeListener scrollListener = (v, hasFocus) -> {
            if (hasFocus && nestedScrollView != null) {
                nestedScrollView.postDelayed(() -> {
                    nestedScrollView.smoothScrollTo(0, cardActiveConfig.getBottom());
                }, 200);
            }
        };
        inputName.setOnFocusChangeListener(scrollListener);
        inputMac.setOnFocusChangeListener(scrollListener);
    }

    private void updateDurationLabel(TextView label, float valueMs) {
        float seconds = valueMs / 1000f;
        label.setText(String.format(Locale.getDefault(), "%.1fs", seconds));
    }

    private void loadInitialSelection() {
        Vehicle selectedVehicle = vehicleRepository.getDefaultVehicle();
        if (selectedVehicle != null) {
            onVehicleSelected(selectedVehicle);
        }
    }

    private void enterCreateMode() {
        List<Vehicle> vehicles = vehicleRepository.getAllVehicles();
        if (vehicles.size() >= 2) {
            Toast.makeText(this, "Maximum limit of 2 vehicles reached", Toast.LENGTH_SHORT).show();
            cardActiveConfig.setVisibility(View.GONE);
            cardVehicleSettings.setVisibility(View.VISIBLE); // Ensure settings stay visible
            return;
        }

        cardActiveConfig.setVisibility(View.VISIBLE);
        cardVehicleSettings.setVisibility(View.GONE);

        inputName.setText("");
        inputMac.setText("");

        if (nestedScrollView != null) {
            nestedScrollView.post(() -> nestedScrollView.smoothScrollTo(0, cardActiveConfig.getBottom()));
        }
    }

    private void setupVehicleRecyclerView() {
        recyclerViewVehicles.setLayoutManager(new LinearLayoutManager(this));

        List<Vehicle> vehicles = vehicleRepository.getAllVehiclesWithLiveState(TAG);

        vehicleConfigAdapter = new VehicleConfigAdapter(
                vehicles,
                selectedVehicleId,
                new VehicleConfigAdapter.OnVehicleActionListener() {
                    @Override
                    public void onSelect(Vehicle vehicle) {
                        vehicleRepository.setDefaultVehicle(vehicle.getId());
                        onVehicleSelected(vehicle);
                        cardActiveConfig.setVisibility(View.GONE);
                        setResult(RESULT_OK);
                    }

                    @Override
                    public void onDelete(Vehicle vehicle) {
                        List<Vehicle> currentList = vehicleRepository.getAllVehicles();
                        if (currentList.size() <= 1) {
                            Toast.makeText(EditConfigActivity.this, "Cannot delete the last remaining vehicle", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        var defaultVehicle = vehicleRepository.getDefaultVehicle();
                        if (defaultVehicle != null && defaultVehicle.getId().equals(vehicle.getId())) {
                            Toast.makeText(EditConfigActivity.this, "Cannot delete the Active vehicle", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Disconnect BLE if active, then delete via Repository
                        if (bleManager.isConnected(vehicle.getMacAddress())) {
                            bleManager.disconnect(vehicle.getMacAddress());
                        }
                        vehicleRepository.deleteVehicle(vehicle);
                        bleManager.refreshVehiclesScan();
                        ConnectionStateRepository.getInstance().removeState(vehicle.getMacAddress());

                        List<Vehicle> updatedList = vehicleRepository.getAllVehiclesWithLiveState(TAG);
                        vehicleConfigAdapter.updateData(updatedList, selectedVehicleId);
                        cardActiveConfig.setVisibility(View.GONE);
                        Toast.makeText(EditConfigActivity.this, "Vehicle deleted", Toast.LENGTH_SHORT).show();

                        if (!updatedList.isEmpty()) {
                            onVehicleSelected(updatedList.get(0));
                        }

                        setResult(RESULT_OK);
                    }
                }
        );

        recyclerViewVehicles.setAdapter(vehicleConfigAdapter);
    }

    private void onVehicleSelected(Vehicle vehicle) {
        selectedVehicleId = vehicle.getId();
        tvSelectedVehicleName.setText(vehicle.getName());

        vehicleConfigAdapter.updateData(vehicleRepository.getAllVehiclesWithLiveState(TAG), selectedVehicleId);

        VehicleSettings settings = vehicleRepository.getSettingsForVehicle(selectedVehicleId);

        // Load saved millisecond values (defaulting to 1.0s if not set)
        initialStartValue = settings.getStartPulseDuration();
        if (initialStartValue < startSliderMin) initialStartValue = startSliderDefault;
        sliderStart.setValue(initialStartValue);
        updateDurationLabel(txtStartVal, initialStartValue);

        initialStopValue = settings.getStopPulseDuration();
        if (initialStopValue < stopSliderMin) initialStopValue = stopSliderDefault;
        sliderStop.setValue(initialStopValue);
        updateDurationLabel(txtStopVal, initialStopValue);

        // Update button state after setting values
        updateSaveSettingsButtonState();
    }

    private void saveNewVehicleConfiguration() {
        String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        String mac = inputMac.getText() != null ? inputMac.getText().toString().trim() : "";

        if (!name.isEmpty() && !mac.isEmpty()) {
            List<Vehicle> vehicles = vehicleRepository.getAllVehicles();
            if (vehicles.size() >= 2) {
                Toast.makeText(this, "Maximum limit of 2 vehicles reached.", Toast.LENGTH_LONG).show();
                return;
            }

            // Add vehicle and default settings (1.0s / 1000ms each) via Repository
            String newVehicleId = vehicleRepository.addVehicleWithSettings(name, mac, (int) startSliderDefault, (int) stopSliderDefault);

            Toast.makeText(this, "New Vehicle Added!", Toast.LENGTH_SHORT).show();

            List<Vehicle> updatedList = vehicleRepository.getAllVehiclesWithLiveState(TAG);
            vehicleConfigAdapter.updateData(updatedList, newVehicleId);

            Vehicle addedObj = vehicleRepository.getVehicleById(newVehicleId);
            if (addedObj != null) {
                onVehicleSelected(addedObj);
            }

            // --- HIDE KEYBOARD START ---
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }

            cardActiveConfig.setVisibility(View.GONE);
            cardVehicleSettings.setVisibility(View.VISIBLE);

            Intent resultIntent = new Intent();
            resultIntent.putExtra("EXTRA_NEW_VEHICLE_ID", newVehicleId);
            setResult(RESULT_OK, resultIntent);
        } else {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveVehicleSettings() {
        if (selectedVehicleId == null) {
            Toast.makeText(this, "No vehicle selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int startMs = (int) sliderStart.getValue();
        int stopMs = (int) sliderStop.getValue();

        vehicleRepository.saveVehicleSettings(selectedVehicleId, startMs, stopMs);

        // Update baseline to current values and disable button until next change
        initialStartValue = sliderStart.getValue();
        initialStopValue = sliderStop.getValue();
        updateSaveSettingsButtonState();

        Toast.makeText(this, "Vehicle settings saved!", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
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

    private void updateSaveSettingsButtonState() {
        boolean hasChanged = (sliderStart.getValue() != initialStartValue) ||
                (sliderStop.getValue() != initialStopValue);

        // Material 3 automatically handles the disabled dimming/styling
        // and enabled primary color when you use standard theme attributes.
        btnSaveSettings.setEnabled(hasChanged);
    }

    private void setupResourcesData() {
        android.util.TypedValue outValue = new android.util.TypedValue();

        getResources().getValue(R.dimen.start_slider_min_value, outValue, true);
        startSliderMin = outValue.getFloat();

        getResources().getValue(R.dimen.stop_slider_min_value, outValue, true);
        stopSliderMin = outValue.getFloat();

        getResources().getValue(R.dimen.start_slider_default_value, outValue, true);
        startSliderDefault = outValue.getFloat();

        getResources().getValue(R.dimen.stop_slider_default_value, outValue, true);
        stopSliderDefault = outValue.getFloat();
    }

    public static class ScannedDeviceAdapter extends BaseAdapter {
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