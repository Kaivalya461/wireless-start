package in.kvapps.wirelessstart.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
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
    private String vehicleId; // Changed from long to String

    private int startPulseDuration; // in milliseconds
    private int stopPulseDuration;  // in milliseconds

    // Updated Constructor
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
}