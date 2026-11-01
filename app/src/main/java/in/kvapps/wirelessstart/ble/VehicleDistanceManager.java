package in.kvapps.wirelessstart.ble;

import android.content.Context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.util.RssiFilter;

public class VehicleDistanceManager {
    private static VehicleDistanceManager instance;
    private Context context;
    private PreferenceManager preferenceManager;

    private final Map<String, RssiFilter> filterMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestRssiMap = new ConcurrentHashMap<>();

    private String closestVehicleMac = null;
    private static final int HYSTERESIS_DBM = 5; // New vehicle must be 5dBm stronger to take crown

    private OnClosestVehicleChangedListener listener;

    public interface OnClosestVehicleChangedListener {
        void onClosestVehicleChanged(String newClosestMac);
    }

    private VehicleDistanceManager(Context context) {
        this.context = context.getApplicationContext(); // Always use Application Context to prevent memory leaks
        MyApplication app = (MyApplication) this.context;
        this.preferenceManager = app.getPreferenceManager();
    }

    public static synchronized VehicleDistanceManager getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleDistanceManager(context);
        }
        return instance;
    }

    public static synchronized VehicleDistanceManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VehicleDistanceManager must be initialized with context first via getInstance(Context)");
        }
        return instance;
    }

    public void setListener(OnClosestVehicleChangedListener listener) {
        this.listener = listener;
    }

    public void updateVehicleRssi(String macAddress, int rawRssi) {
        // Check if preference manager is provided and toggle is disabled
        if (preferenceManager != null && !preferenceManager.isAutoDefaultVehicleEnabled()) {
            if (closestVehicleMac != null) {
                closestVehicleMac = null;
                if (listener != null) listener.onClosestVehicleChanged(null);
            }
            return;
        }

        latestRssiMap.put(macAddress, rawRssi);
        evaluateClosestVehicle();
    }

    public int getSmoothedWidth(String macAddress, int rawRssi, int parentWidth) {
        RssiFilter filter = filterMap.computeIfAbsent(macAddress, k -> new RssiFilter());
        return filter.filterAndSmooth(rawRssi, parentWidth);
    }

    public void removeVehicle(String macAddress) {
        filterMap.remove(macAddress);
        latestRssiMap.remove(macAddress);
        if (Objects.equals(closestVehicleMac, macAddress)) {
            closestVehicleMac = null;
            evaluateClosestVehicle();
        }
    }

    private synchronized void evaluateClosestVehicle() {
        if (preferenceManager != null && !preferenceManager.isAutoDefaultVehicleEnabled()) {
            if (closestVehicleMac != null) {
                closestVehicleMac = null;
                if (listener != null) listener.onClosestVehicleChanged(null);
            }
            return;
        }

        if (latestRssiMap.isEmpty()) {
            if (closestVehicleMac != null) {
                closestVehicleMac = null;
                if (listener != null) listener.onClosestVehicleChanged(null);
            }
            return;
        }

        String bestMac = closestVehicleMac;
        int bestRssi = (closestVehicleMac != null) ? latestRssiMap.getOrDefault(closestVehicleMac, -999) : -999;

        for (Map.Entry<String, Integer> entry : latestRssiMap.entrySet()) {
            String mac = entry.getKey();
            int rssi = entry.getValue();

            if (bestMac == null) {
                bestMac = mac;
                bestRssi = rssi;
            } else if (!mac.equals(bestMac)) {
                // Hysteresis check: new device must be stronger by at least HYSTERESIS_DBM
                if (rssi > (bestRssi + HYSTERESIS_DBM)) {
                    bestMac = mac;
                    bestRssi = rssi;
                }
            }
        }

        if (!Objects.equals(closestVehicleMac, bestMac)) {
            closestVehicleMac = bestMac;
            if (listener != null) {
                listener.onClosestVehicleChanged(closestVehicleMac);
            }
        }
    }

    public String getClosestVehicleMac() {
        return closestVehicleMac;
    }
}