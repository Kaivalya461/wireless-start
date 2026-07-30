package in.kvapps.wirelessstart.ble;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import in.kvapps.wirelessstart.util.PermissionUtils;

public class BleLifecycleObserver implements DefaultLifecycleObserver {
    private final Activity activity;
    private final BleManager bleManager;
    private final LogCallback logCallback;

    // Tracks whether the initial activity creation lifecycle has already passed
    private boolean hasStarted = false;

    public interface LogCallback {
        void onLog(String message);
    }

    // Update constructor to take Activity
    public BleLifecycleObserver(Activity activity, BleManager bleManager, LogCallback logCallback) {
        this.activity = activity;
        this.bleManager = bleManager;
        this.logCallback = logCallback;
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onResume(owner);

        // Skip the check on the very first cold-start resume,
        // because your initial onCreate flow handles the first connection.
        if (!hasStarted) {
            hasStarted = true;
            return;
        }

        // From this point on, every time the user returns to the app
        // (e.g., coming back into range or switching back from another app):
        if (PermissionUtils.hasBluetoothPermissions(activity)) {
            if (!bleManager.isConnected()) {
                if (logCallback != null) {
                    logCallback.onLog("App resumed. Checking BLE connection...");
                }
                bleManager.connect();
            }
        }
    }
}