package in.kvapps.wirelessstart;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.VehicleDistanceManager;
import in.kvapps.wirelessstart.ble.BackgroundVehicleEventManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.repository.VehicleRepository;

public class MyApplication extends android.app.Application {

    private BleManager bleManager;
    private PreferenceManager preferenceManager;
    private VehicleRepository vehicleRepository;
    private WearSyncManager wearSyncManager;
    private BackgroundVehicleEventManager backgroundVehicleEventManager;

    @Override
    public void onCreate() {
        super.onCreate();

        preferenceManager = new PreferenceManager(this);

        // Initialize VehicleRepository once globally
        vehicleRepository = new VehicleRepository(this);

        // Centralize BleManager initialization here once at application startup
        bleManager = new BleManager(this, null);

        // Initialize BackgroundVehicleEventManager as a single instance app-wide
        backgroundVehicleEventManager = BackgroundVehicleEventManager.getInstance(this);

        // Initialize WearSyncManager as a single instance app-wide
        wearSyncManager = WearSyncManager.getInstance(this);

        VehicleDistanceManager.getInstance(this);

        // Observe when the app comes to the foreground
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                DefaultLifecycleObserver.super.onStart(owner);

                var defaultVehicle = vehicleRepository.getDefaultVehicle();
                if (!preferenceManager.isForegroundServiceEnabled()
                        && bleManager != null
                        && defaultVehicle != null
                        && !bleManager.isConnected(defaultVehicle.getMacAddress())) {
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

    public WearSyncManager getWearSyncManager() {
        return wearSyncManager;
    }
}