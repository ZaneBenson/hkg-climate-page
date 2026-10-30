package hkgclimate.core;

/** The outside temperature at which a seat turns on cooling instead of heating, kept in whole °F. */
public final class SwitchTemp {
    public static final int DEFAULT_F = 60;
    public static final int MIN_F = 40;
    public static final int MAX_F = 85;

    private SwitchTemp() { }

    /** The value to show for a stored °F value; rawUnit 1 is Celsius, anything else Fahrenheit. */
    public static int toDisplay(int f, int rawUnit) {
        if (rawUnit == 1) return Math.round((f - 32) * 5f / 9f);
        return f;
    }

    /** The whole °F to store for a shown value, kept within MIN_F..MAX_F. */
    public static int fromDisplay(int v, int rawUnit) {
        int f = rawUnit == 1 ? Math.round(v * 9f / 5f + 32f) : v;
        return Math.max(MIN_F, Math.min(MAX_F, f));
    }
}
