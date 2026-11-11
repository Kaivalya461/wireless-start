package in.kvapps.wirelessstart.util;

import android.util.Log;

import java.util.List;
import java.util.Optional;

import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.enums.VehicleControlType;
import in.kvapps.wirelessstart.enums.VehicleEventType;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.model.VehicleWithSettings;

public class VehicleUtils {

    /**
     * Enriches a standard list of Vehicles with live BLE connection states.
     */
    public static List<Vehicle> enrichVehiclesWithLiveState(String tag, List<Vehicle> vehicles) {
        if (vehicles == null) return null;
        ConnectionStateRepository repo = ConnectionStateRepository.getInstance();

        for (Vehicle vehicle : vehicles) {
            if (vehicle != null) {
                enrichSingleVehicle(tag, vehicle, repo);
            }
        }
        return vehicles;
    }

    /**
     * Enriches a list of VehicleWithSettings items with live BLE connection states.
     */
    public static List<VehicleWithSettings> enrichVehiclesWithSettingsLiveState(String tag, List<VehicleWithSettings> items) {
        if (items == null) return null;
        ConnectionStateRepository repo = ConnectionStateRepository.getInstance();

        for (VehicleWithSettings item : items) {
            if (item != null && item.getVehicle() != null) {
                enrichSingleVehicle(tag, item.getVehicle(), repo);
            }
        }
        return items;
    }

    /**
     * Private helper to apply connection state and status text to a vehicle.
     */
    private static void enrichSingleVehicle(String tag, Vehicle vehicle, ConnectionStateRepository repo) {
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

    /**
     * Resolves active pulse duration string for a given action.
     */
    public static String getFormattedCommand(String action, VehicleSettings settings) {
        if (action == null) return "";

        long duration = 0;
        if (settings != null) {
            duration = switch (action.toUpperCase()) {
                case "START" -> settings.getStartPulseDuration();
                case "STOP" -> settings.getStopPulseDuration();
                case "UNLOCK", "LOCK" -> 500; // Default to 500 for these
                default -> 1000; // Fallback default duration
            };
        }

        return action.toUpperCase() + ":" + duration;
    }

    public static Optional<VehicleEventType> getVehicleEventType(ConnectionState connectionState) {
        return Optional.ofNullable(switch (connectionState) {
            case CONNECTED -> VehicleEventType.CONNECTION_SUCCESS;
            case DISCONNECTED -> VehicleEventType.LINK_LOSS_DISCONNECT;
            default -> null;
        });
    }

    public static String getVehicleActionText(VehicleSettings vehicleSettings, boolean isPrimary) {
        if (vehicleSettings == null) return null;

        var controlType = VehicleControlType.valueOf(vehicleSettings.getControlType());
        return isPrimary ? controlType.getPrimaryActionText() : controlType.getSecondaryActionText();
    }
}