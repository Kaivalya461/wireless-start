package in.kvapps.wirelessstart.enums;

public enum SignalState {
    SUPER_STRONG(-64, 2),      // >= -64 dBm (Level 2: Strong Only)
    MEDIUM_HIGH(-75, 1),       // -65 dBm to -75 dBm (Level 1: Medium)
    LOW_WAITING(-90, 0),     //  -76 dBm to -90 dBm (Level 0: Low)
    LOW_TRIGGER(-999, -1);      // < -90 dBm, never connect

    private final int rssiLimit;
    private final int requiredThresholdLevel;

    SignalState(int rssiLimit, int requiredThresholdLevel) {
        this.rssiLimit = rssiLimit;
        this.requiredThresholdLevel = requiredThresholdLevel;
    }

    public int getRssiLimit() {
        return rssiLimit;
    }

    public int getRequiredThresholdLevel() {
        return requiredThresholdLevel;
    }

    /**
     * Evaluates the signal state dynamically based on measured RSSI
     * and the vehicle's specific configured connect threshold (in dBm).
     *
     * @param rssi              The measured RSSI value from the advertisement packet.
     * @param connectThreshold  The vehicle's configured connect RSSI threshold (e.g. -75 dBm).
     * @return                  The corresponding SignalState.
     */
    public static SignalState evaluate(int rssi, int connectThreshold) {
        if (rssi >= connectThreshold) {
            // Signal meets or exceeds the vehicle's connect requirement -> Trigger connection
            return SignalState.MEDIUM_HIGH;
        } else {
            // Signal is weaker than the required connect threshold -> Wait for signal to improve
            return SignalState.LOW_WAITING;
        }
    }
}