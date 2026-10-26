package in.kvapps.wirelessstart.ble;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import in.kvapps.wirelessstart.enums.ConnectionState;

public class ConnectionStateRepository {

    private static ConnectionStateRepository instance;

    private final Map<String, DeviceConnectionState> stateMap = new ConcurrentHashMap<>();
    private final Set<OnConnectionStateChangedListener> listeners = new CopyOnWriteArraySet<>();

    public interface OnConnectionStateChangedListener {
        void onStateChanged(String macAddress, DeviceConnectionState newState);
    }

    private ConnectionStateRepository() {}

    public static synchronized ConnectionStateRepository getInstance() {
        if (instance == null) {
            instance = new ConnectionStateRepository();
        }
        return instance;
    }

    public void addListener(OnConnectionStateChangedListener listener) {
        if (listener != null) {
            listeners.add(listener);
            for (Map.Entry<String, DeviceConnectionState> entry : stateMap.entrySet()) {
                listener.onStateChanged(entry.getKey(), entry.getValue());
            }
        }
    }

    public void removeListener(OnConnectionStateChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void updateState(String macAddress, ConnectionState state, String statusText) {
        if (macAddress == null) return;

        long connectedAtTime = ConnectionState.CONNECTED.equals(state) ? System.currentTimeMillis() : 0L;
        DeviceConnectionState newState = new DeviceConnectionState(macAddress, state, statusText, connectedAtTime);
        stateMap.put(macAddress, newState);

        for (OnConnectionStateChangedListener listener : listeners) {
            try {
                listener.onStateChanged(macAddress, newState);
            } catch (Exception ignored) {}
        }
    }

    public void removeState(String macAddress) {
        if (macAddress == null) return;

        // Remove from the map
        stateMap.remove(macAddress);
    }

    public DeviceConnectionState getState(String macAddress) {
        return stateMap.get(macAddress);
    }

    public static class DeviceConnectionState {
        private final String macAddress;
        private final ConnectionState state;
        private final String statusText;
        private final long connectedAtTime;

        public DeviceConnectionState(String macAddress, ConnectionState state, String statusText, long connectedAtTime) {
            this.macAddress = macAddress;
            this.state = state;
            this.statusText = statusText;
            this.connectedAtTime = connectedAtTime;
        }

        public String getMacAddress() { return macAddress; }
        public ConnectionState getState() { return state; }
        public boolean isConnected() { return state == ConnectionState.CONNECTED; }
        public String getStatusText() { return statusText; }
        public long getConnectedAtTime() { return connectedAtTime; }
    }
}