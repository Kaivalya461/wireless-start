package in.kvapps.wirelessstart.model;

import androidx.room.Embedded;
import androidx.room.Relation;

public class VehicleWithSettings {

    @Embedded
    private Vehicle vehicle;

    @Relation(
            parentColumn = "id",       // Vehicle.id
            entityColumn = "vehicleId" // VehicleSettings.vehicleId
    )
    private VehicleSettings vehicleSettings;

    // Getters and Setters
    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public VehicleSettings getVehicleSettings() {
        return vehicleSettings;
    }

    public void setVehicleSettings(VehicleSettings vehicleSettings) {
        this.vehicleSettings = vehicleSettings;
    }
}