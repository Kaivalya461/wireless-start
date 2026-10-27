package in.kvapps.wirelessstart.util;

import android.util.Log;

import java.util.List;
import java.util.Map;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;

public class VehicleUtils {
    public static List<Vehicle> enrichVehiclesWithLiveState(String tag, List<Vehicle> vehicles) {
        ConnectionStateRepository repo = ConnectionStateRepository.getInstance();

        for (Vehicle vehicle : vehicles) {
            ConnectionStateRepository.DeviceConnectionState stateObj = repo.getState(vehicle.getMacAddress());

            if (stateObj != null) {
                vehicle.setConnected(stateObj.isConnected());
                vehicle.setStatusText(stateObj.getStatusText());

                // Helpful debug log to verify match
                Log.d(tag, "State found for MAC: " + vehicle.getMacAddress() + " -> Connected: " + stateObj.isConnected());
            } else {
                vehicle.setConnected(false);
                vehicle.setStatusText("SYSTEMS: OFFLINE");
            }
        }
        return vehicles;
    }

    /**
     * Resolves active pulse duration string for a given action.
     */
    public static String getFormattedCommand(String action, VehicleSettings settings) {
        boolean isStart = "START".equalsIgnoreCase(action);
        long duration = isStart ? settings.getStartPulseDuration() : settings.getStopPulseDuration();
        return action + ":" + duration;
    }
}
