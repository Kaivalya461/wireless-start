package in.kvapps.wirelessstart.enums;

public enum DurationOption {
    MS_800("800ms", 800),
    MS_1700("1700ms", 1700),
    MS_2200("2200ms", 2200),
    CUSTOM("Custom", -1);

    private final String label;
    private final int valueMs;

    DurationOption(String label, int valueMs) {
        this.label = label;
        this.valueMs = valueMs;
    }

    public String getLabel() { return label; }
    public int getValueMs() { return valueMs; }

    // Helper method to extract just the string labels for your Spinner adapter
    public static String[] getLabels() {
        DurationOption[] options = values();
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].label;
        }
        return labels;
    }
}
