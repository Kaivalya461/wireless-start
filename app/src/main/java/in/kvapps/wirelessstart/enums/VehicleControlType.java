package in.kvapps.wirelessstart.enums;

public enum VehicleControlType {
    START_STOP("START", "STOP"),
    LOCK_UNLOCK("UNLOCK", "LOCK");

    private final String primaryActionText;
    private final String secondaryActionText;

    VehicleControlType(String primaryActionText, String secondaryActionText) {
        this.primaryActionText = primaryActionText;
        this.secondaryActionText = secondaryActionText;
    }

    public String getPrimaryActionText() {
        return primaryActionText;
    }

    public String getSecondaryActionText() {
        return secondaryActionText;
    }
}