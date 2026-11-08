package cn.erindax.bphone.airstrike;

public enum AirstrikeMode {
    BOMBARDMENT("bombardment", false),
    STRAFING_RUN("strafing_run", true);

    private final String serializedName;
    private final boolean requiresTwoPoints;

    AirstrikeMode(String serializedName, boolean requiresTwoPoints) {
        this.serializedName = serializedName;
        this.requiresTwoPoints = requiresTwoPoints;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean requiresTwoPoints() {
        return requiresTwoPoints;
    }

    public AirstrikeMode next() {
        AirstrikeMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static AirstrikeMode byOrdinal(int ordinal) {
        AirstrikeMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return BOMBARDMENT;
        }

        return values[ordinal];
    }
}
