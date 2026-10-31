package hkgclimate.core;

/** The one-line text for a preset in the settings list. */
public final class PresetSummary {
    private static final String SEP = " · ";

    private PresetSummary() { }

    /** "Cold: Driver heat high · Passenger off"; a seat left as is is not named. */
    public static String of(Preset p) {
        StringBuilder sb = new StringBuilder(p.name).append(':');
        boolean first = true;
        for (Seat s : Seat.values()) {
            int v = p.target(s);
            if (v == Preset.LEAVE) continue;
            String w = SeatValues.words(v);
            sb.append(first ? " " : SEP).append(s == Seat.DRIVER ? "Driver " : "Passenger ")
                    .append(w == null ? String.valueOf(v) : w);
            first = false;
        }
        return sb.toString();
    }
}
