package in.kvapps.wirelessstart;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import in.kvapps.wirelessstart.ble.BleForegroundService;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;

public class MyApplication extends android.app.Application {

    private BleManager bleManager; // Fallback singleton when BLE Foreground Service is disabled
    private PreferenceManager preferenceManager;

    @Override
    public void onCreate() {
        super.onCreate();

        preferenceManager = new PreferenceManager(this);

        // Start Foreground Service if enabled, otherwise initialize standalone BleManager
        if (preferenceManager.isForegroundServiceEnabled()) {
            startBleForegroundService();
        } else {
            bleManager = new BleManager(this, null);
        }

        // Observe when the app comes to the foreground (opened from memory/recent apps)
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                DefaultLifecycleObserver.super.onStart(owner);

                // When app comes to the foreground, check connection and force connect
                BleManager manager = getBleManager();
                if (manager != null && !manager.isConnected()) {
                    // Force an instant connection attempt regardless of background auto-connect setting
                    manager.connect(false);
                }
            }
        });
    }

    public BleManager getBleManager() {
        // 1. If Foreground Service is running, fetch its active manager instance
        BleForegroundService serviceInstance = BleForegroundService.getInstance();
        if (serviceInstance != null) {
            return serviceInstance.getBleManager();
        }

        // 2. Fallback: If service is disabled/not running, return the application-level instance
        if (bleManager == null) {
            bleManager = new BleManager(this, null);
        }
        return bleManager;
    }

    public PreferenceManager getPreferenceManager() {
        return preferenceManager;
    }

    private void startBleForegroundService() {
        Intent serviceIntent = new Intent(this, BleForegroundService.class);
        startForegroundService(serviceIntent);
    }
}