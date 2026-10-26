package in.kvapps.wirelessstart.util;

import android.util.Log;

import java.util.List;

import in.kvapps.wirelessstart.ble.BleManager;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.model.Vehicle;

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

    public static void addVehicle(PreferenceManager preferenceManager, BleManager bleManager,
                                  String vehicleName, String vehicleMac) {
        preferenceManager.addVehicle(vehicleName, vehicleMac);
        bleManager.refreshVehicles();
    }

    public static void deleteVehicle(PreferenceManager preferenceManager, BleManager bleManager, Vehicle vehicle) {
        // If vehicle is already connected, release it.
        if (bleManager.isConnected(vehicle.getMacAddress())) {
            bleManager.disconnect(vehicle.getMacAddress());
        }

        preferenceManager.deleteVehicle(vehicle.getId());
        ConnectionStateRepository repo = ConnectionStateRepository.getInstance();
        repo.removeState(vehicle.getMacAddress());
        bleManager.refreshVehicles();
    }
}
