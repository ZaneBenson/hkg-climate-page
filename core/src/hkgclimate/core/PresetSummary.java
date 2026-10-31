package hkgclimate.core;

/** The one-line text for a preset in the settings list. */
public final class PresetSummary {
    private static final String SEP = " · ";

    private PresetSummary() { }

    /**
     * "Cold: Driver heat high · Passenger off · Wheel on · Auto · 70 °F": every item the preset
     * sets, seats first; an item left as is is not named.
     */
    public static String of(Preset p) {
        StringBuilder sb = new StringBuilder(p.name).append(':');
        boolean first = true;
        for (Seat s : Seat.values()) {
            int v = p.target(s);
            if (v == Preset.LEAVE) continue;
            String w = SeatValues.words(v);
            first = add(sb, first, (s == Seat.DRIVER ? "Driver " : "Passenger ")
                    + (w == null ? String.valueOf(v) : w));
        }
        first = onOff(sb, first, "Wheel", p.wheel);
        first = onOff(sb, first, "Eco", p.eco);
        if (p.climate == Preset.CLIMATE_OFF) first = add(sb, first, "Climate off");
        else if (p.climate == Preset.CLIMATE_AUTO) first = add(sb, first, "Auto");
        else if (p.climate == Preset.CLIMATE_MANUAL) first = add(sb, first, "Manual");
        else if (p.climate != Preset.LEAVE) first = add(sb, first, "Climate " + p.climate);
        if (p.tempF != Preset.LEAVE) first = add(sb, first, p.tempF + " °F");
        if (p.fan != Preset.LEAVE) first = add(sb, first, "Fan " + p.fan);
        first = onOff(sb, first, "A/C", p.ac);
        if (p.airflow != Preset.LEAVE) {
            first = add(sb, first, "Airflow " + airflowWords(p.airflow));
        }
        onOff(sb, first, "Sync", p.sync);
        return sb.toString();
    }

    private static boolean onOff(StringBuilder sb, boolean first, String item, int v) {
        if (v == Preset.LEAVE) return first;
        return add(sb, first, item + (v == 1 ? " on" : v == 0 ? " off" : " " + v));
    }

    private static boolean add(StringBuilder sb, boolean first, String text) {
        sb.append(first ? " " : SEP).append(text);
        return false;
    }

    private static String airflowWords(int v) {
        PresetChoices.Choice[] all = PresetChoices.airflow();
        int pos = PresetChoices.positionOf(all, v);
        return pos == 0 ? String.valueOf(v) : all[pos].label.toLowerCase();
    }
}
