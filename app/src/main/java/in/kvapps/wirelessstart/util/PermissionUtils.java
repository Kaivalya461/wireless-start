package in.kvapps.wirelessstart.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Map;

public class PermissionUtils {
    public static final int PERMISSION_REQUEST_CODE = 101;

    public static boolean hasBluetoothPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean hasNotificationPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // Helper to get the required permissions array dynamically based on SDK version
    public static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.POST_NOTIFICATIONS
                };
            } else {
                return new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                };
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.POST_NOTIFICATIONS};
        }
        return new String[]{};
    }

    public static boolean evaluatePermissionsResult(Map<String, Boolean> result) {
        boolean bluetoothGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Boolean connectGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
            Boolean scanGranted = result.get(Manifest.permission.BLUETOOTH_SCAN);
            if ((connectGranted != null && !connectGranted) || (scanGranted != null && !scanGranted)) {
                bluetoothGranted = false;
            }
        }
        return bluetoothGranted;
    }
}