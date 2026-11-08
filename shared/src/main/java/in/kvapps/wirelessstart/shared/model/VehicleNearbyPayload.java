package in.kvapps.wirelessstart.shared.model;

public class VehicleNearbyPayload {
    private String vehicleId;
    private String vehicleName;
    private int rssi;
    private boolean isConnected;
    private boolean isEnteredNearby;
    private long timestamp;

    // Constructor that automatically sets the current time
    public VehicleNearbyPayload(String vehicleId, String vehicleName, int rssi, boolean isConnected, boolean isEnteredNearby) {
        this.vehicleId = vehicleId;
        this.vehicleName = vehicleName;
        this.rssi = rssi;
        this.isConnected = isConnected;
        this.isEnteredNearby = isEnteredNearby;
        this.timestamp = System.currentTimeMillis();
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getVehicleName() {
        return vehicleName;
    }

    public int getRssi() {
        return rssi;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isEnteredNearby() {
        return isEnteredNearby;
    }

    public long getTimestamp() {
        return timestamp;
    }
}