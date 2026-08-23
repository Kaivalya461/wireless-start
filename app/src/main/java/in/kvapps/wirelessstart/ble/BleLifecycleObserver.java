package in.kvapps.wirelessstart.ble;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.util.PermissionUtils;

public class BleLifecycleObserver implements DefaultLifecycleObserver {
    private final Context context;
    private final BleManager bleManager;
    private final PreferenceManager preferenceManager;
    private final LogCallback logCallback;

    public interface LogCallback {
        void onLog(String message);
    }

    public BleLifecycleObserver(Context context, BleManager bleManager, PreferenceManager preferenceManager, LogCallback logCallback) {
        this.context = context.getApplicationContext();
        this.bleManager = bleManager;
        this.preferenceManager = preferenceManager;
        this.logCallback = logCallback;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStart(owner);

        // Using ON_START (or ON_RESUME) via ProcessLifecycleOwner fires
        // reliably whenever the entire app transitions from background to foreground.
        if (PermissionUtils.hasBluetoothPermissions(context)) {
            if (!bleManager.isConnected()) {
                if (logCallback != null) {
                    logCallback.onLog("App brought to foreground. Checking BLE connection...");
                }

                boolean autoConnectSetting = preferenceManager != null && preferenceManager.isAutoConnectEnabled();
                bleManager.connect(autoConnectSetting);
            }
        }
    }
}