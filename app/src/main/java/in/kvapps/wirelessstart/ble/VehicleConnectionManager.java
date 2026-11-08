package in.kvapps.wirelessstart.ble;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import in.kvapps.wirelessstart.adapter.VehicleAdapter;
import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.enums.VehicleEventType;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.util.FeedbackUtils;

import java.util.HashMap;
import java.util.Map;

public class VehicleConnectionManager implements ConnectionStateRepository.OnConnectionStateChangedListener, VehicleEventRepository.VehicleEventListener {
    private static final String TAG = "VehicleConnectionMgr";
    private static final long RSSI_TIMEOUT_MS = 8000;

    private final Context context;
    private final VehicleRepository vehicleRepository;
    private final VehicleAdapter vehicleAdapter;
    private final ConnectionLifecycleListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<String, ConnectionState> lastKnownStates = new HashMap<>();
    private final Map<String, Runnable> rssiTimeoutRunnables = new HashMap<>();

    public interface ConnectionLifecycleListener {
        void onServicesReady(Vehicle vehicle);
        void onLog(String message);
    }

    public VehicleConnectionManager(Context context, VehicleRepository vehicleRepository,
                                    VehicleAdapter vehicleAdapter, ConnectionLifecycleListener listener) {
        this.context = context;
        this.vehicleRepository = vehicleRepository;
        this.vehicleAdapter = vehicleAdapter;
        this.listener = listener;
    }

    public void register() {
        ConnectionStateRepository.getInstance().addListener(this);
        VehicleEventRepository.getInstance().addListener(this);
    }

    public void unregister() {
        ConnectionStateRepository.getInstance().removeListener(this);
        VehicleEventRepository.getInstance().removeListener(this);
        mainHandler.removeCallbacksAndMessages(null);
        rssiTimeoutRunnables.clear();
    }

    /*
    * Manages all the MainActivity UI States
    * */
    @Override
    public void onStateChanged(String macAddress, ConnectionStateRepository.DeviceConnectionState newState) {
        // Ensure all state processing and UI updates run strictly on the Main Thread
        mainHandler.post(() -> {
            Vehicle vehicle = vehicleRepository.getVehicleByMac(macAddress);
            if (vehicle == null) {
                listener.onLog("onStateChanged() -> Missing Vehicle object for MAC: " + macAddress);
                return;
            }

            ConnectionState currentState = newState.getState();
            ConnectionState previousState = lastKnownStates.get(macAddress);

            vehicle.setConnected(newState.isConnected());
            vehicle.setStatusText(newState.getStatusText());

            handleStateTransition(vehicle, macAddress, currentState, previousState, newState);
            lastKnownStates.put(macAddress, currentState);
        });
    }

    /*
     * Manages the vehicle connection events to trigger one time actions like initialization sequence, updating system-activity-log screen with CONN updates.
     * */
    @Override
    public void onVehicleEvent(String macAddress, VehicleEventType vehicleEventType, VehicleEventData vehicleEventData) {
        mainHandler.post(() -> {
            Vehicle vehicle = vehicleRepository.getVehicleByMac(macAddress);
            if (vehicle == null) {
                listener.onLog("onVehicleEvent() -> Missing Vehicle object for vehicleEventType: " + vehicleEventType + ", MAC: " + macAddress);
                return;
            }

            switch (vehicleEventType) {
                case CONNECTION_SUCCESS:
                    listener.onServicesReady(vehicle);
                    break;
                case LINK_LOSS_DISCONNECT:
                    FeedbackUtils.triggerDisconnectVibrate(context);
                    listener.onLog("[" + vehicle.getName() + "] Disconnected");
                    break;
                case ENTERED_NEARBY:
                case EXITED_NEARBY:
                    // Do nothing in MainActivity
                    break;
                default:
                    break;
            }
        });
    }

    private void handleStateTransition(Vehicle vehicle, String macAddress,
                                       ConnectionState currentState,
                                       ConnectionState previousState,
                                       ConnectionStateRepository.DeviceConnectionState newState) {
        switch (currentState) {
            case CONNECTED:
            case DISCONNECTED:
            case SCANNING:
            case RECONNECTING:
                updateConnectionUi(vehicle);
                cancelRssiTimeout(macAddress);
                break;

            case NEARBY_FAR_AWAY:
                // previousState check not needed for NEARBY_FAR_AWAY; as this workflow is sent continuously for rssi updates
                updateConnectionUi(vehicle);
                // Reset/Start the watchdog timer. If RSSI packets stop, it falls back to SCANNING.
                resetRssiTimeout(macAddress);
                break;

            case DISCOVERING:
            default:
                updateConnectionUi(vehicle);
                cancelRssiTimeout(macAddress);
                break;
        }
    }

    private void updateConnectionUi(Vehicle vehicle) {
        vehicleAdapter.updateVehicleConnection(vehicle.getId(), vehicle.isConnected(), vehicle.getStatusText());
    }

    private void resetRssiTimeout(String macAddress) {
        cancelRssiTimeout(macAddress);

        Runnable timeoutRunnable = () -> {
            Log.i(TAG, "RSSI timeout reached for " + macAddress + ". Device likely out of range or powered off. Resetting to SCANNING.");

            ConnectionStateRepository.getInstance().updateState(macAddress, ConnectionState.SCANNING, "Scanning");

            rssiTimeoutRunnables.remove(macAddress);
        };

        rssiTimeoutRunnables.put(macAddress, timeoutRunnable);
        mainHandler.postDelayed(timeoutRunnable, RSSI_TIMEOUT_MS);
    }

    private void cancelRssiTimeout(String macAddress) {
        Runnable existingRunnable = rssiTimeoutRunnables.remove(macAddress);
        if (existingRunnable != null) {
            mainHandler.removeCallbacks(existingRunnable);
        }
    }
}