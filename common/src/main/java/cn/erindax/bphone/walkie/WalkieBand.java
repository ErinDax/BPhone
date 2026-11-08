package cn.erindax.bphone.walkie;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum WalkieBand {
    AM(520, 1710, 900, 10, "kHz"),
    PM(760, 1080, 980, 5, "MHz");

    private final int minFrequency;
    private final int maxFrequency;
    private final int defaultFrequency;
    private final int step;
    private final String unit;

    WalkieBand(int minFrequency, int maxFrequency, int defaultFrequency, int step, String unit) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.defaultFrequency = defaultFrequency;
        this.step = step;
        this.unit = unit;
    }

    public int defaultFrequency() {
        return defaultFrequency;
    }

    public int step() {
        return step;
    }

    public WalkieBand next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public int clamp(int frequency) {
        int clamped = Math.max(minFrequency, Math.min(maxFrequency, frequency));
        int offset = clamped - minFrequency;
        int snapped = minFrequency + Math.round(offset / (float) step) * step;
        return Math.max(minFrequency, Math.min(maxFrequency, snapped));
    }

    public Component display(int frequency) {
        int clamped = clamp(frequency);
        if (this == PM) {
            return Component.literal(name() + " " + String.format(Locale.ROOT, "%.1f", clamped / 10.0F) + " " + unit);
        }

        return Component.literal(name() + " " + clamped + " " + unit);
    }

    public static WalkieBand byName(String name) {
        for (WalkieBand band : values()) {
            if (band.name().equalsIgnoreCase(name)) {
                return band;
            }
        }

        return AM;
    }
}
