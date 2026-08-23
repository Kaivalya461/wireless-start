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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import in.kvapps.wirelessstart.data.PreferenceManager;

public class EditConfigActivity extends AppCompatActivity {

    private TextInputEditText inputName, inputMac;
    private MaterialButton btnScan, btnSave;

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private final Handler handler = new Handler();

    private final ArrayList<BluetoothDevice> scannedDevices = new ArrayList<>();
    private AlertDialog scanDialog;
    private PreferenceManager preferenceManager;
    private ScannedDeviceAdapter scanAdapter;

    private static final int REQUEST_CODE_PERMISSIONS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_config);

        // Initialize PreferenceManager
        preferenceManager = new PreferenceManager(this);

        inputName = findViewById(R.id.input_edit_name);
        inputMac = findViewById(R.id.input_edit_mac);
        btnScan = findViewById(R.id.btn_scan_ble);
        btnSave = findViewById(R.id.btn_save_config);

        // Pre-populate fields with currently saved values
        inputName.setText(preferenceManager.getTargetHwName());
        inputMac.setText(preferenceManager.getTargetMacAddress());

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        btnScan.setOnClickListener(v -> checkPermissionsAndScan());

        btnSave.setOnClickListener(v -> {
            String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
            String mac = inputMac.getText() != null ? inputMac.getText().toString().trim() : "";

            if (!name.isEmpty() && !mac.isEmpty()) {
                // Save directly using PreferenceManager
                preferenceManager.saveTargetHwName(name);
                preferenceManager.saveTargetMacAddress(mac);

                // EXPLICITLY set RESULT_OK so the launcher callback triggers
                setResult(RESULT_OK);

                Toast.makeText(this, "Configuration Saved!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionsAndScan() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) and higher: Only need BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // Android 11 (API 30) and lower: Still require Fine Location to perform BLE scans
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

        // Initialize custom adapter
        scanAdapter = new ScannedDeviceAdapter(this, scannedDevices);

        // Build using MaterialAlertDialogBuilder for native Material styling
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this);

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

    @Override
    protected void onPause() {
        super.onPause();
        stopBleScan();
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