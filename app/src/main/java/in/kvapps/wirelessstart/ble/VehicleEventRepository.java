package in.kvapps.wirelessstart.ble;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.VehicleEventType;

public class VehicleEventRepository {

    private static VehicleEventRepository instance;
    private final Set<VehicleEventListener> listeners = new CopyOnWriteArraySet<>();

    public interface VehicleEventListener {
        void onVehicleEvent(String macAddress, VehicleEventType vehicleEventType, VehicleEventData vehicleEventData);
    }

    private VehicleEventRepository() {}

    public static synchronized VehicleEventRepository getInstance() {
        if (instance == null) {
            instance = new VehicleEventRepository();
        }
        return instance;
    }

    public void addListener(VehicleEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(VehicleEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void emitEvent(String macAddress, VehicleEventType vehicleEventType) {
        if (macAddress == null) return;

        emitEvent(macAddress, vehicleEventType, null);
    }

    public void emitEvent(String macAddress, VehicleEventType vehicleEventType, VehicleEventData vehicleEventData) {
        if (macAddress == null) return;

        for (VehicleEventListener listener : listeners) {
            try {
                listener.onVehicleEvent(macAddress, vehicleEventType, vehicleEventData);
            } catch (Exception ignored) {}
        }
    }
}