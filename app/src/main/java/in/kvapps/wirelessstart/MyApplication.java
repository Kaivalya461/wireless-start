package in.kvapps.wirelessstart;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.repository.VehicleRepository;

public class MyApplication extends android.app.Application {

    private BleManager bleManager;
    private PreferenceManager preferenceManager;
    private VehicleRepository vehicleRepository;

    @Override
    public void onCreate() {
        super.onCreate();

        preferenceManager = new PreferenceManager(this);

        // Initialize VehicleRepository once globally
        vehicleRepository = new VehicleRepository(this);

        // Centralize BleManager initialization here once at application startup
        bleManager = new BleManager(this, null);

        // Observe when the app comes to the foreground
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                DefaultLifecycleObserver.super.onStart(owner);

                var selectedVehicle = preferenceManager.getSelectedVehicle();
                if (!preferenceManager.isForegroundServiceEnabled()
                        && bleManager != null
                        && selectedVehicle != null
                        && !bleManager.isConnected(selectedVehicle.getMacAddress())) {
                    bleManager.startMultiVehicleScan();
                }
            }
        });
    }

    public BleManager getBleManager() {
        return bleManager;
    }

    public PreferenceManager getPreferenceManager() {
        return preferenceManager;
    }

    public VehicleRepository getVehicleRepository() {
        return vehicleRepository;
    }
}