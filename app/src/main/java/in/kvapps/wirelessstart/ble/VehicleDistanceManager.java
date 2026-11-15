package in.kvapps.wirelessstart.ble;

import static in.kvapps.wirelessstart.enums.VehicleEventType.CLOSEST_VEHICLE_CHANGED;
import static in.kvapps.wirelessstart.enums.VehicleEventType.DISCONNECT_THRESHOLD_EXCEEDED;
import static in.kvapps.wirelessstart.enums.VehicleEventType.ENTERED_NEARBY;
import static in.kvapps.wirelessstart.enums.VehicleEventType.EXITED_NEARBY;

import android.content.Context;
import android.util.Log;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.SignalState;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.util.RssiFilter;

public class VehicleDistanceManager {
    private static final String TAG = "VehicleDistanceManager";
    private static VehicleDistanceManager instance;
    private Context context;
    private PreferenceManager preferenceManager;
    private VehicleRepository vehicleRepository;

    private final Map<String, RssiFilter> filterMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestRssiMap = new ConcurrentHashMap<>();

    // In-memory cache to prevent repetitive DB queries every second
    private final Map<String, Integer> disconnectThresholdCache = new ConcurrentHashMap<>();

    // Track which vehicles already triggered the "nearby" alert for the current connection
    private final Set<String> nearbySentVehicles = ConcurrentHashMap.newKeySet();

    // Hysteresis Tracking for Force Disconnect
    private final Map<String, Integer> consecutiveLowRssiMap = new ConcurrentHashMap<>();
    private static final int REQUIRED_CONSECUTIVE_LOW_READINGS = 4; // Must stay below threshold for 4 polls (rssi updates flow every 800ms)
    private String closestVehicleMac = null;
    private static final int HYSTERESIS_DBM = 5; // New vehicle must be 5dBm stronger to take crown
    private static final int NEARBY_RSSI_THRESHOLD = SignalState.SUPER_STRONG.getRssiLimit();

    // Debouncing counters for Proximity to prevent flutter while riding
    private final Map<String, Integer> consecutiveNearbyCountMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFarCountMap = new ConcurrentHashMap<>();
    private static final int REQUIRED_PROXIMITY_READINGS = 3; // Must sustain for 3 consecutive polls (800ms x 3poll = ~2.4s)

    private VehicleDistanceManager(Context context) {
        this.context = context.getApplicationContext(); // Always use Application Context to prevent memory leaks
        MyApplication app = (MyApplication) this.context;
        this.preferenceManager = app.getPreferenceManager();
        this.vehicleRepository = app.getVehicleRepository();
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

        checkForceDisconnect(macAddress, isConnected, rawRssi);
        checkNearbyProximity(macAddress, isConnected, rawRssi);
        evaluateClosestVehicle();
    }

    private void checkForceDisconnect(String macAddress, boolean isConnected, int rssi) {
        if (!isConnected) return;

        // Fetch from cache, or query DB ONCE and store it
        int disconnectThreshold = disconnectThresholdCache.computeIfAbsent(macAddress, mac -> {
            Log.i(TAG, "Cache miss for " + mac + ". Querying DB for disconnect threshold.");
            if (vehicleRepository != null) {
                return vehicleRepository.getDisconnectThreshold(mac);
            }
            return -85; // Fallback default
        });

        if (rssi < disconnectThreshold) {
            int lowCount = consecutiveLowRssiMap.getOrDefault(macAddress, 0) + 1;
            consecutiveLowRssiMap.put(macAddress, lowCount);

            if (lowCount >= REQUIRED_CONSECUTIVE_LOW_READINGS) {
                consecutiveLowRssiMap.remove(macAddress);

                // Emit disconnect event with the live RSSI data
                var disconnectData = new VehicleEventData(macAddress, isConnected, rssi, DISCONNECT_THRESHOLD_EXCEEDED);
                VehicleEventRepository.getInstance()
                        .emitEvent(macAddress, DISCONNECT_THRESHOLD_EXCEEDED, disconnectData);
            }
        } else {
            // RSSI recovered above threshold, reset the consecutive low counter
            consecutiveLowRssiMap.remove(macAddress);
        }
    }

    private void checkNearbyProximity(String macAddress, boolean isConnected, int rssi) {
        int exitThreshold = NEARBY_RSSI_THRESHOLD - 11; // Hysteresis buffer band

        if (rssi >= NEARBY_RSSI_THRESHOLD) {
            // Reset far counter since we are seeing strong RSSI
            consecutiveFarCountMap.remove(macAddress);

            // Increment consecutive strong readings
            int strongCount = consecutiveNearbyCountMap.getOrDefault(macAddress, 0) + 1;
            consecutiveNearbyCountMap.put(macAddress, strongCount);

            if (strongCount >= REQUIRED_PROXIMITY_READINGS) {
                // Check if we haven't already sent it for this connection session
                if (!nearbySentVehicles.contains(macAddress)) {
                    nearbySentVehicles.add(macAddress);

                    var enteredNearbyData = new VehicleEventData(macAddress, isConnected, rssi, ENTERED_NEARBY);
                    VehicleEventRepository.getInstance()
                            .emitEvent(macAddress, ENTERED_NEARBY, enteredNearbyData);
                }
            }
        } else {
            // Reset near counter since RSSI dropped below the enter threshold
            consecutiveNearbyCountMap.remove(macAddress);

            // Only trigger exit if RSSI drops significantly below the buffer band consistently
            if (rssi < exitThreshold) {
                int farCount = consecutiveFarCountMap.getOrDefault(macAddress, 0) + 1;
                consecutiveFarCountMap.put(macAddress, farCount);

                if (farCount >= REQUIRED_PROXIMITY_READINGS) {
                    consecutiveFarCountMap.remove(macAddress);
                    nearbySentVehicles.remove(macAddress);

                    var exitedNearbyData = new VehicleEventData(macAddress, isConnected, rssi, EXITED_NEARBY);
                    VehicleEventRepository.getInstance()
                            .emitEvent(macAddress, EXITED_NEARBY, exitedNearbyData);
                }
            } else {
                // If we are in the buffer zone (between exitThreshold and NEARBY_RSSI_THRESHOLD),
                // don't count it as a full exit to prevent rapid state flapping.
                consecutiveFarCountMap.remove(macAddress);
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
        consecutiveLowRssiMap.remove(macAddress);
        disconnectThresholdCache.remove(macAddress);
        consecutiveNearbyCountMap.remove(macAddress);
        consecutiveFarCountMap.remove(macAddress);

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

    public void clearAllVehicleCacheData() {
        disconnectThresholdCache.clear();
    }

    public String getClosestVehicleMac() {
        return closestVehicleMac;
    }
}