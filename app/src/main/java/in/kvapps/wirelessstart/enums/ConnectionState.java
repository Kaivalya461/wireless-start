package in.kvapps.wirelessstart.enums;

public enum ConnectionState {
    CONNECTED("Connected"),
    DISCOVERING("Discovering..."),
    DISCONNECTED("Disconnected"),
    SCANNING("Scanning..."),
    NEARBY_FAR_AWAY("Nearby (Far Away)"),
    RECONNECTING("Reconnecting...");

    private final String displayName;

    ConnectionState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}