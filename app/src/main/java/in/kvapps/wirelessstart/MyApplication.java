package in.kvapps.wirelessstart;

import android.app.Application;
import androidx.lifecycle.ProcessLifecycleOwner;

import in.kvapps.wirelessstart.ble.BleLifecycleObserver;
import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.util.AppLogger;

public class MyApplication extends Application {

    private BleManager bleManager;
    private PreferenceManager preferenceManager;

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Initialize core managers
        preferenceManager = new PreferenceManager(this);

        // Note: Pass a null listener initially since the UI/MainActivity
        // will dynamically attach/re-bind its listener on resume.
        bleManager = new BleManager(this, null);

        // 2. Register globally via ProcessLifecycleOwner.
        // This ensures it ONLY triggers when the entire app goes to background / comes back,
        // completely ignoring navigation between activities inside your app.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(
                new BleLifecycleObserver(
                        this,
                        bleManager,
                        preferenceManager,
                        message -> AppLogger.logToDatabaseAndLogcat(this, "", message)
                )
        );
    }

    public BleManager getBleManager() {
        return bleManager;
    }

    public PreferenceManager getPreferenceManager() {
        return preferenceManager;
    }
}