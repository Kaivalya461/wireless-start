package in.kvapps.wirelessstart.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "vehicle_settings",
        foreignKeys = @ForeignKey(
                entity = Vehicle.class,
                parentColumns = "id",
                childColumns = "vehicleId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("vehicleId")}
)
public class VehicleSettings {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String vehicleId;

    private int startPulseDuration; // in milliseconds
    private int stopPulseDuration;  // in milliseconds

    @ColumnInfo(defaultValue = "1")
    private boolean isStartEngineEnabled = true;

    @ColumnInfo(defaultValue = "0")
    private boolean isStopEngineEnabled = false;

    @ColumnInfo(defaultValue = "0")
    private boolean isPowerSwitchEnabled = false;

    @NonNull
    @ColumnInfo(defaultValue = "START_STOP")
    private String controlType = "START_STOP";

    @ColumnInfo(defaultValue = "0")
    private boolean supportsAck = false;

    // 1. Full constructor for Room
    public VehicleSettings(@NonNull String vehicleId, int startPulseDuration, int stopPulseDuration,
                           boolean isStartEngineEnabled, boolean isStopEngineEnabled,
                           boolean isPowerSwitchEnabled, @NonNull String controlType, boolean supportsAck) {
        this.vehicleId = vehicleId;
        this.startPulseDuration = startPulseDuration;
        this.stopPulseDuration = stopPulseDuration;
        this.isStartEngineEnabled = isStartEngineEnabled;
        this.isStopEngineEnabled = isStopEngineEnabled;
        this.isPowerSwitchEnabled = isPowerSwitchEnabled;
        this.controlType = controlType;
        this.supportsAck = supportsAck;
    }

    // 2. Overloaded constructor for legacy code
    @Ignore
    public VehicleSettings(@NonNull String vehicleId, int startPulseDuration, int stopPulseDuration) {
        this.vehicleId = vehicleId;
        this.startPulseDuration = startPulseDuration;
        this.stopPulseDuration = stopPulseDuration;
        this.isStartEngineEnabled = true;
        this.isStopEngineEnabled = false;
        this.isPowerSwitchEnabled = false;
        this.controlType = "START_STOP";
        this.supportsAck = false;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(@NonNull String vehicleId) { this.vehicleId = vehicleId; }

    public int getStartPulseDuration() { return startPulseDuration; }
    public void setStartPulseDuration(int startPulseDuration) { this.startPulseDuration = startPulseDuration; }

    public int getStopPulseDuration() { return stopPulseDuration; }
    public void setStopPulseDuration(int stopPulseDuration) { this.stopPulseDuration = stopPulseDuration; }

    public boolean isStartEngineEnabled() { return isStartEngineEnabled; }
    public void setStartEngineEnabled(boolean startEngineEnabled) { isStartEngineEnabled = startEngineEnabled; }

    public boolean isStopEngineEnabled() { return isStopEngineEnabled; }
    public void setStopEngineEnabled(boolean stopEngineEnabled) { isStopEngineEnabled = stopEngineEnabled; }

    public boolean isPowerSwitchEnabled() { return isPowerSwitchEnabled; }
    public void setPowerSwitchEnabled(boolean powerSwitchEnabled) { isPowerSwitchEnabled = powerSwitchEnabled; }

    @NonNull
    public String getControlType() { return controlType; }
    public void setControlType(@NonNull String controlType) { this.controlType = controlType; }

    public boolean isSupportsAck() { return supportsAck; }
    public void setSupportsAck(boolean supportsAck) { this.supportsAck = supportsAck; }
}