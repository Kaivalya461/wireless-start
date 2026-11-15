package in.kvapps.wirelessstart.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "command_logs")
public class CommandLogEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String macAddress;
    private String command;    // e.g., "START:500"
    private String result;     // e.g., "SUCCESS", "FAILURE", "SYSTEM_FAILURE
    private long timestamp;
    private long timeTaken;    // Execution time in milliseconds

    public CommandLogEntity(String macAddress, String command, String result, long timestamp, long timeTaken) {
        this.macAddress = macAddress;
        this.command = command;
        this.result = result;
        this.timestamp = timestamp;
        this.timeTaken = timeTaken;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getMacAddress() { return macAddress; }
    public String getCommand() { return command; }
    public String getResult() { return result; }
    public long getTimestamp() { return timestamp; }
    public long getTimeTaken() { return timeTaken; }
}