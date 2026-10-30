package in.kvapps.wirelessstart.enums;

public enum SignalState {
    SUPER_STRONG(-60, 2),      // >= -55 dBm (Level 2: Strong Only)
    MEDIUM_HIGH(-80, 1),       // -51 dBm to -80 dBm (Level 1: Medium)
    LOW_WAITING(-90, 0),     //  -81 dBm to -90 dBm (Level 0: Low)
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
     * and the user's configured 3-step connection threshold level (0, 1, or 2).
     *
     * @param rssi              The measured RSSI value from the advertisement packet.
     * @param userThreshold     The user setting (0 = Always Connect, 1 = Medium, 2 = Strong Only).
     * @return                  The corresponding SignalState.
     */
    public static SignalState evaluate(int rssi, int userThreshold) {
        if (rssi >= SUPER_STRONG.rssiLimit) {
            return SUPER_STRONG; // Always passes Level 2, 1, and 0
        } else if (rssi >= MEDIUM_HIGH.rssiLimit) {
            // Passes Level 0 (Always) and Level 1 (Medium).
            // If user selected Level 2 (Strong Only), treat as waiting.
            return (userThreshold >= 2) ? LOW_WAITING : MEDIUM_HIGH;
        } else if (rssi >= LOW_WAITING.rssiLimit) {
            // Weak signal
            // Only Level 0 (Always Connect) allows connecting here.
            // Levels 1 and 2 will wait.
            return (userThreshold == 0) ? LOW_TRIGGER : LOW_WAITING;
        } else {
            // For Signal lower than -90 (eg: -95), never connect
            return LOW_WAITING;
        }
    }
}