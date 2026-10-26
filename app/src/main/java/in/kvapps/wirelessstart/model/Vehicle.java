package in.kvapps.wirelessstart.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "vehicles")
public class Vehicle {
    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private String macAddress;


    // Transient runtime UI states (ignored by Room database)
    @Ignore
    private boolean isConnected;
    @Ignore
    private String statusText = "SYSTEMS: OFFLINE";
    @Ignore
    private String voltage = "--.--V";
    @Ignore
    private boolean isBusy = false;

    // Primary constructor for Room
    public Vehicle(@NonNull String id, String name, String macAddress) {
        this.id = id;
        this.name = name;
        this.macAddress = macAddress;
        this.isConnected = false;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    // Getters and Setters for UI Runtime States
    public boolean isConnected() { return isConnected; }
    public void setConnected(boolean connected) { isConnected = connected; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getVoltage() { return voltage; }
    public void setVoltage(String voltage) { this.voltage = voltage; }

    public boolean isBusy() { return isBusy; }
    public void setBusy(boolean busy) { isBusy = busy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return id.equals(vehicle.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}