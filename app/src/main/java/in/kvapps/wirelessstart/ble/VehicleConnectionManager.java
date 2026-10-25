package in.kvapps.wirelessstart.ble;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import in.kvapps.wirelessstart.adapter.VehicleAdapter;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.util.FeedbackUtils;

import java.util.HashMap;
import java.util.Map;

public class VehicleConnectionManager implements ConnectionStateRepository.OnConnectionStateChangedListener {
    private static final String TAG = "VehicleConnectionMgr";

    private final Context context;
    private final PreferenceManager preferenceManager;
    private final VehicleAdapter vehicleAdapter;
    private final ConnectionLifecycleListener listener;

    private final Map<String, ConnectionState> lastKnownStates = new HashMap<>();

    public interface ConnectionLifecycleListener {
        void onServicesReady(Vehicle vehicle);
        boolean isAppInForeground();
        void onLog(String message);
    }

    public VehicleConnectionManager(Context context, PreferenceManager preferenceManager,
                                    VehicleAdapter vehicleAdapter, ConnectionLifecycleListener listener) {
        this.context = context;
        this.preferenceManager = preferenceManager;
        this.vehicleAdapter = vehicleAdapter;
        this.listener = listener;
    }

    public void register() {
        ConnectionStateRepository.getInstance().addListener(this);
    }

    public void unregister() {
        ConnectionStateRepository.getInstance().removeListener(this);
        lastKnownStates.clear();
    }

    @Override
    public void onStateChanged(String macAddress, ConnectionStateRepository.DeviceConnectionState newState) {
        Vehicle vehicle = preferenceManager.getVehicleByMac(macAddress);
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
    }

    private void handleStateTransition(Vehicle vehicle, String macAddress,
                                       ConnectionState currentState,
                                       ConnectionState previousState,
                                       ConnectionStateRepository.DeviceConnectionState newState) {
        switch (currentState) {
            case CONNECTED:
                if (previousState != ConnectionState.CONNECTED) {
                    Log.i(TAG, "SSOT StateChanged: Newly Connected for " + macAddress);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> listener.onServicesReady(vehicle), 50);
                }
                break;

            case DISCONNECTED:
                if (previousState != ConnectionState.DISCONNECTED) {
                    Log.i(TAG, "SSOT StateChanged: Offline for " + vehicle.getMacAddress());
                    updateConnectionUi(vehicle);

                    long uptimeMillis = 0;
                    if (newState.getConnectedAtTime() > 0) {
                        uptimeMillis = System.currentTimeMillis() - newState.getConnectedAtTime();
                    }

                    FeedbackUtils.triggerDisconnectVibrate(context);
                    FeedbackUtils.showConnectionNotification(context, false, uptimeMillis, listener.isAppInForeground());
                }
                break;

            case SCANNING:
                if (previousState != ConnectionState.SCANNING) {
                    updateConnectionUi(vehicle);
                }
                break;

            case DISCOVERING:
            default:
                updateConnectionUi(vehicle);
                break;
        }
    }

    private void updateConnectionUi(Vehicle vehicle) {
        vehicleAdapter.updateVehicleConnection(vehicle.getId(), vehicle.isConnected(), vehicle.getStatusText());
    }
}