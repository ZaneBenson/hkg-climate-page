package hkgclimate.core;

/**
 * One saved preset: a name, a target for each front seat, the steering wheel and eco mode, and
 * the cabin climate items. Every item is LEAVE, meaning the preset does not touch it, or a value
 * in its range. A seat target is a SeatValues value (2..8); wheel, eco, ac and sync are 0 off or
 * 1 on; climate is CLIMATE_OFF, CLIMATE_AUTO or CLIMATE_MANUAL; tempF is TEMP_MIN_F..TEMP_MAX_F
 * (stored in degrees Fahrenheit whatever the car shows); fan is FAN_MIN..FAN_MAX; airflow is
 * AIRFLOW_MIN..AIRFLOW_MAX (1 face, 2 face and floor, 3 floor, 4 floor and defrost).
 * The constructors store the values with the climate mode applied: Auto leaves fan, A/C and
 * airflow alone, Off leaves temperature, fan, A/C, airflow and sync alone.
 */
public final class Preset {
    public static final int LEAVE = -1;
    public static final int MAX_NAME = 10;

    public static final int CLIMATE_OFF = 1;
    public static final int CLIMATE_AUTO = 2;
    public static final int CLIMATE_MANUAL = 3;
    public static final int TEMP_MIN_F = 62;
    public static final int TEMP_MAX_F = 82;
    public static final int FAN_MIN = 1;
    public static final int FAN_MAX = 8;
    public static final int AIRFLOW_MIN = 1;
    public static final int AIRFLOW_MAX = 4;

    public final String name;
    public final int driver;
    public final int passenger;
    public final int wheel;
    public final int eco;
    public final int climate;
    public final int tempF;
    public final int fan;
    public final int ac;
    public final int airflow;
    public final int sync;

    /** A seat-only preset: every other item is LEAVE. Keeps the name trimmed; use {@link #check} to validate. */
    public Preset(String name, int driver, int passenger) {
        this(name, driver, passenger, LEAVE, LEAVE, LEAVE, LEAVE, LEAVE, LEAVE, LEAVE, LEAVE);
    }

    /** Keeps the name trimmed and the values with the climate mode applied; use {@link #check} to validate. */
    public Preset(String name, int driver, int passenger, int wheel, int eco, int climate, int tempF,
            int fan, int ac, int airflow, int sync) {
        this.name = name == null ? "" : name.trim();
        this.driver = driver;
        this.passenger = passenger;
        this.wheel = wheel;
        this.eco = eco;
        this.climate = climate;
        boolean off = climate == CLIMATE_OFF;
        boolean manualItemsGreyed = off || climate == CLIMATE_AUTO;
        this.tempF = off ? LEAVE : tempF;
        this.fan = manualItemsGreyed ? LEAVE : fan;
        this.ac = manualItemsGreyed ? LEAVE : ac;
        this.airflow = manualItemsGreyed ? LEAVE : airflow;
        this.sync = off ? LEAVE : sync;
    }

    /** The target for the seat. */
    public int target(Seat s) { return s == Seat.DRIVER ? driver : passenger; }

    /** True when the preset sets a front seat. */
    public boolean hasSeatItems() { return driver != LEAVE || passenger != LEAVE; }

    /** True when the preset sets the steering wheel, eco mode or any cabin item. */
    public boolean hasRunnerItems() {
        return wheel != LEAVE || eco != LEAVE || climate != LEAVE || tempF != LEAVE || fan != LEAVE
                || ac != LEAVE || airflow != LEAVE || sync != LEAVE;
    }

    /** Null when a seat-only preset is valid, else a short reason. The name is checked after trimming. */
    public static String check(String name, int driver, int passenger) {
        String bad = badName(name);
        if (bad != null) return bad;
        if (!validTarget(driver) || !validTarget(passenger)) return "bad seat value";
        if (driver == LEAVE && passenger == LEAVE) return "choose at least one seat";
        return null;
    }

    /** Null when the preset is valid, else a short reason: name, every item in range, something set. */
    public static String check(Preset p) {
        String bad = badName(p.name);
        if (bad != null) return bad;
        if (!validTarget(p.driver) || !validTarget(p.passenger)) return "bad seat value";
        if (!validFlag(p.wheel) || !validFlag(p.eco) || !validFlag(p.ac) || !validFlag(p.sync)
                || !validRange(p.climate, CLIMATE_OFF, CLIMATE_MANUAL)
                || !validRange(p.tempF, TEMP_MIN_F, TEMP_MAX_F)
                || !validRange(p.fan, FAN_MIN, FAN_MAX)
                || !validRange(p.airflow, AIRFLOW_MIN, AIRFLOW_MAX)) {
            return "bad setting value";
        }
        if (!p.hasSeatItems() && !p.hasRunnerItems()) return "choose at least one setting";
        if (p.climate == CLIMATE_MANUAL && p.fan == LEAVE && p.ac == LEAVE && p.airflow == LEAVE) {
            return "manual needs a fan speed, A/C or airflow";
        }
        return null;
    }

    private static String badName(String name) {
        if (name == null) return "name 1-10 characters";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') return "name has a tab or line break";
        }
        int n = name.trim().length();
        if (n < 1 || n > MAX_NAME) return "name 1-10 characters";
        return null;
    }

    private static boolean validTarget(int v) {
        return v == LEAVE || SeatValues.isKnown(v);
    }

    private static boolean validFlag(int v) {
        return v == LEAVE || v == 0 || v == 1;
    }

    private static boolean validRange(int v, int min, int max) {
        return v == LEAVE || (v >= min && v <= max);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Preset)) return false;
        Preset p = (Preset) o;
        return name.equals(p.name) && driver == p.driver && passenger == p.passenger
                && wheel == p.wheel && eco == p.eco && climate == p.climate && tempF == p.tempF
                && fan == p.fan && ac == p.ac && airflow == p.airflow && sync == p.sync;
    }

    @Override
    public int hashCode() {
        int[] v = {driver, passenger, wheel, eco, climate, tempF, fan, ac, airflow, sync};
        int h = name.hashCode();
        for (int x : v) h = 31 * h + x;
        return h;
    }

    @Override
    public String toString() {
        return name + " (" + driver + ", " + passenger + ", wheel " + wheel + ", eco " + eco
                + ", climate " + climate + ", " + tempF + " F, fan " + fan + ", ac " + ac
                + ", airflow " + airflow + ", sync " + sync + ")";
    }
}
