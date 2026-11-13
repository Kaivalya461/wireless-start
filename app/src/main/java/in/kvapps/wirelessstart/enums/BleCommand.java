package in.kvapps.wirelessstart.enums;

public enum BleCommand {
    UNLOCK((byte) 0x01),
    LOCK((byte) 0x02),
    POWER_STATE((byte) 0x03),
    TIME_SYNC((byte) 0x10),
    FAILSAFE_STATE((byte) 0x11),
    SCHEDULED_RUN((byte) 0x12),
    GET_SCHEDULE((byte) 0x13),
    GET_CONFIG((byte) 0x14),
    GENERIC_TEXT((byte) 0x15);

    private final byte commandId;

    BleCommand(byte commandId) {
        this.commandId = commandId;
    }

    public byte getCommandId() {
        return commandId;
    }

    // Response status codes remain accessible here or as static constants
    public static final int STATUS_SUCCESS = 0x00;
    public static final int STATUS_FAILED = 0x01;

    /**
     * Resolves a legacy text command string to its corresponding BleCommand enum.
     */
    public static BleCommand resolve(String commandText) {
        if (commandText == null || commandText.isEmpty()) {
            return GENERIC_TEXT;
        }

        if (commandText.startsWith("UNLOCK")) {
            return UNLOCK;
        } else if (commandText.startsWith("LOCK")) {
            return LOCK;
        } else if (commandText.equals("PWR:ON") || commandText.equals("PWR:OFF")) {
            return POWER_STATE;
        } else if (commandText.startsWith("TIME:")) {
            return TIME_SYNC;
        } else if (commandText.equals("GET_CONFIG")) {
            return GET_CONFIG;
        } else if (commandText.startsWith("SCHED_RUN:")) {
            return SCHEDULED_RUN;
        } else if (commandText.equals("GET_ENGINE_SCHEDULE")) {
            return GET_SCHEDULE;
        } else if (commandText.startsWith("FAILSAFE:")) {
            return FAILSAFE_STATE;
        }

        return GENERIC_TEXT;
    }
}