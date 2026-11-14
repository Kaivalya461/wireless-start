package in.kvapps.wirelessstart.dto;

import in.kvapps.wirelessstart.enums.VehicleEventType;

public class VehicleEventData {
    private String macAddress;
    private boolean isConnected;
    private int rssi;
    private VehicleEventType vehicleEventType;
    private boolean isPoweredOn; // Added power state field

    // Default constructor
    public VehicleEventData() {
    }

    // Full parameterized constructor
    public VehicleEventData(String macAddress, boolean isConnected, int rssi, VehicleEventType vehicleEventType, boolean isPoweredOn) {
        this.macAddress = macAddress;
        this.isConnected = isConnected;
        this.rssi = rssi;
        this.vehicleEventType = vehicleEventType;
        this.isPoweredOn = isPoweredOn;
    }

    // Overloaded legacy constructor for backward compatibility
    public VehicleEventData(String macAddress, boolean isConnected, int rssi, VehicleEventType vehicleEventType) {
        this.macAddress = macAddress;
        this.isConnected = isConnected;
        this.rssi = rssi;
        this.vehicleEventType = vehicleEventType;
        this.isPoweredOn = false;
    }

    // Getters
    public String getMacAddress() {
        return macAddress;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public int getRssi() {
        return rssi;
    }

    public VehicleEventType getVehicleEventType() {
        return vehicleEventType;
    }

    public boolean isPoweredOn() {
        return isPoweredOn;
    }

    // toString method
    @Override
    public String toString() {
        return "VehicleEventData{" +
                "macAddress='" + macAddress + '\'' +
                ", isConnected=" + isConnected +
                ", rssi=" + rssi +
                ", vehicleEventType=" + vehicleEventType +
                ", isPoweredOn=" + isPoweredOn +
                '}';
    }
}