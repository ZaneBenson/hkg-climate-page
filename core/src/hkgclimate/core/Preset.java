package hkgclimate.core;

/**
 * One saved preset: a name and a target for each front seat. A target is a seat value
 * (SeatValues 2..8) or LEAVE, meaning the preset does not touch that seat.
 */
public final class Preset {
    public static final int LEAVE = -1;
    public static final int MAX_NAME = 10;

    public final String name;
    public final int driver;
    public final int passenger;

    /** Keeps the name trimmed; it does not validate, use {@link #check} for that. */
    public Preset(String name, int driver, int passenger) {
        this.name = name == null ? "" : name.trim();
        this.driver = driver;
        this.passenger = passenger;
    }

    /** The target for the seat. */
    public int target(Seat s) { return s == Seat.DRIVER ? driver : passenger; }

    /** Null when the preset is valid, else a short reason. The name is checked after trimming. */
    public static String check(String name, int driver, int passenger) {
        if (name == null) return "name 1-10 characters";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') return "name has a tab or line break";
        }
        int n = name.trim().length();
        if (n < 1 || n > MAX_NAME) return "name 1-10 characters";
        if (!validTarget(driver) || !validTarget(passenger)) return "bad seat value";
        if (driver == LEAVE && passenger == LEAVE) return "choose at least one seat";
        return null;
    }

    private static boolean validTarget(int v) {
        return v == LEAVE || SeatValues.isKnown(v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Preset)) return false;
        Preset p = (Preset) o;
        return name.equals(p.name) && driver == p.driver && passenger == p.passenger;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + driver) + passenger;
    }

    @Override
    public String toString() { return name + " (" + driver + ", " + passenger + ")"; }
}
