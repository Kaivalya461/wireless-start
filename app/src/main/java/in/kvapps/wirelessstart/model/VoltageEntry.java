package in.kvapps.wirelessstart.model;

public class VoltageEntry {
    private final long timestamp;
    private final float voltage;

    public VoltageEntry(long timestamp, float voltage) {
        this.timestamp = timestamp;
        this.voltage = voltage;
    }

    public long getTimestamp() { return timestamp; }
    public float getVoltage() { return voltage; }
}
