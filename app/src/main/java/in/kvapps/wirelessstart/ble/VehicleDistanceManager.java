package in.kvapps.wirelessstart.ble;

import static in.kvapps.wirelessstart.enums.VehicleEventType.CLOSEST_VEHICLE_CHANGED;
import static in.kvapps.wirelessstart.enums.VehicleEventType.ENTERED_NEARBY;
import static in.kvapps.wirelessstart.enums.VehicleEventType.EXITED_NEARBY;

import android.content.Context;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.SignalState;
import in.kvapps.wirelessstart.util.RssiFilter;

public class VehicleDistanceManager {
    private static VehicleDistanceManager instance;
    private Context context;
    private PreferenceManager preferenceManager;

    private final Map<String, RssiFilter> filterMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestRssiMap = new ConcurrentHashMap<>();

    // --- NEW: Track which vehicles already triggered the "nearby" alert for the current connection ---
    private final Set<String> nearbySentVehicles = ConcurrentHashMap.newKeySet();

    private String closestVehicleMac = null;
    private static final int HYSTERESIS_DBM = 5; // New vehicle must be 5dBm stronger to take crown
    private static final int NEARBY_RSSI_THRESHOLD = SignalState.SUPER_STRONG.getRssiLimit();

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

    public void updateVehicleRssi(String macAddress, boolean isConnected, int rawRssi) {
        // Check if preference manager is provided and toggle is disabled
        if (preferenceManager != null && !preferenceManager.isAutoDefaultVehicleEnabled()) {
            if (closestVehicleMac != null) {
                closestVehicleMac = null;
                // Do nothing in this case.
            }
            return;
        }

        latestRssiMap.put(macAddress, rawRssi);

        checkNearbyProximity(macAddress, isConnected, rawRssi);
        evaluateClosestVehicle();
    }

    // --- NEW: Handles the once-per-connection threshold logic ---
    private void checkNearbyProximity(String macAddress, boolean isConnected, int rssi) {
        if (rssi >= NEARBY_RSSI_THRESHOLD) {
            // Check if we haven't already sent it for this connection session
            if (!nearbySentVehicles.contains(macAddress)) {
                nearbySentVehicles.add(macAddress);

                var enteredNearbyData = new VehicleEventData(macAddress, isConnected, rssi, ENTERED_NEARBY);
                VehicleEventRepository.getInstance()
                        .emitEvent(macAddress, ENTERED_NEARBY, enteredNearbyData);
            }
        } else {
            // If the user backs away below threshold (eg: -57), allow it to trigger again if they come back close
            if (rssi < (NEARBY_RSSI_THRESHOLD - 16)) { // Adding a small buffer to prevent rapid flipping
                nearbySentVehicles.remove(macAddress);

                var exitedNearbyData = new VehicleEventData(macAddress, isConnected, rssi, EXITED_NEARBY);
                VehicleEventRepository.getInstance()
                        .emitEvent(macAddress, EXITED_NEARBY, exitedNearbyData);
            }
        }
    }

    public int getSmoothedWidth(String macAddress, int rawRssi, int parentWidth) {
        RssiFilter filter = filterMap.computeIfAbsent(macAddress, k -> new RssiFilter());
        return filter.filterAndSmooth(rawRssi, parentWidth);
    }

    public void removeVehicle(String macAddress) {
        filterMap.remove(macAddress);
        latestRssiMap.remove(macAddress);
        nearbySentVehicles.remove(macAddress);

        if (Objects.equals(closestVehicleMac, macAddress)) {
            closestVehicleMac = null;
            evaluateClosestVehicle();
        }
    }

    private synchronized void evaluateClosestVehicle() {
        if (preferenceManager != null && !preferenceManager.isAutoDefaultVehicleEnabled()) {
            if (closestVehicleMac != null) {
                closestVehicleMac = null;
                // Do nothing in this case
            }
            return;
        }

        if (latestRssiMap.isEmpty()) {
            if (closestVehicleMac != null) {
                closestVehicleMac = null;
                // Do nothing in this case
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
            VehicleEventRepository.getInstance()
                    .emitEvent(closestVehicleMac, CLOSEST_VEHICLE_CHANGED);
        }
    }

    public String getClosestVehicleMac() {
        return closestVehicleMac;
    }
}