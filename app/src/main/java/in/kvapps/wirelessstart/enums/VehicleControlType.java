package in.kvapps.wirelessstart.enums;

public enum VehicleControlType {
    START_STOP("START", "STOP", null),
    LOCK_UNLOCK("UNLOCK", "LOCK", "PWR"); // Or whatever command prefix your power action uses

    private final String primaryActionText;
    private final String secondaryActionText;
    private final String tertiaryActionText;

    VehicleControlType(String primaryActionText, String secondaryActionText, String tertiaryActionText) {
        this.primaryActionText = primaryActionText;
        this.secondaryActionText = secondaryActionText;
        this.tertiaryActionText = tertiaryActionText;
    }

    public String getPrimaryActionText() {
        return primaryActionText;
    }

    public String getSecondaryActionText() {
        return secondaryActionText;
    }

    public String getTertiaryActionText() {
        return tertiaryActionText;
    }

    public boolean hasTertiaryAction() {
        return tertiaryActionText != null;
    }
}