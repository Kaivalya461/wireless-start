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
    private boolean isStartEngineEnabled = true; // Defaults to true here

    @ColumnInfo(defaultValue = "0")
    private boolean isStopEngineEnabled = false; // Defaults to false here

    // 1. Full constructor used by Room when reading/writing from the database
    public VehicleSettings(@NonNull String vehicleId, int startPulseDuration, int stopPulseDuration, boolean isStartEngineEnabled, boolean isStopEngineEnabled) {
        this.vehicleId = vehicleId;
        this.startPulseDuration = startPulseDuration;
        this.stopPulseDuration = stopPulseDuration;
        this.isStartEngineEnabled = isStartEngineEnabled;
        this.isStopEngineEnabled = isStopEngineEnabled;
    }

    // 2. Overloaded constructor for your existing app code (defaults flags to false)
    @Ignore
    public VehicleSettings(@NonNull String vehicleId, int startPulseDuration, int stopPulseDuration) {
        this.vehicleId = vehicleId;
        this.startPulseDuration = startPulseDuration;
        this.stopPulseDuration = stopPulseDuration;
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
}