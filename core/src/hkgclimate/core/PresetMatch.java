package hkgclimate.core;

import java.util.Set;

/**
 * Whether the car still reads at the targets a preset reached, for the applied-preset highlight.
 * Only the items named in doneItems count: the ones the run (and the tap's seat sends) left at
 * their target. An item the run left alone by design or reported not done is never named, so it
 * never counts. Each item is judged as PresetRunner judges it done.
 */
public final class PresetMatch {
    public static final String DRIVER_SEAT = "driver seat";
    public static final String PASSENGER_SEAT = "passenger seat";
    public static final String WHEEL = "wheel";
    public static final String ECO = "eco";
    public static final String CLIMATE_OFF = "climate off";
    public static final String AUTO = "auto";
    public static final String AC = "ac";
    public static final String AIRFLOW = "airflow";
    public static final String FAN = "fan";
    public static final String MANUAL = "manual";
    public static final String SYNC = "sync";
    public static final String DRIVER_TEMP = "driver temperature";
    public static final String PASSENGER_TEMP = "passenger temperature";

    private PresetMatch() { }

    /**
     * True when an item in doneItems now reads differently from the preset's target or cannot be
     * read. seatValues is indexed by Seat.ordinal() (null, or a value that is not a seat value,
     * is unknown); a null state is unknown. A name it does not know counts as drift: it never
     * claims a match it cannot check.
     */
    public static boolean drifted(Preset p, int[] seatValues, CabinState s, Set<String> doneItems) {
        if (doneItems == null || doneItems.isEmpty()) return false;
        CabinState c = s == null ? CabinState.NONE : s;
        for (String item : doneItems) {
            if (!matches(p, seatValues, c, item)) return true;
        }
        return false;
    }

    private static boolean matches(Preset p, int[] seats, CabinState s, String item) {
        if (DRIVER_SEAT.equals(item)) return seatAt(seats, Seat.DRIVER, p.driver);
        if (PASSENGER_SEAT.equals(item)) return seatAt(seats, Seat.PASSENGER, p.passenger);
        if (WHEEL.equals(item)) return onOff(s.wheel) && s.wheel == p.wheel;
        if (ECO.equals(item)) return onOff(s.eco) && s.eco == p.eco;
        if (CLIMATE_OFF.equals(item)) return s.opState == CabinState.OP_OFF;
        if (AUTO.equals(item)) return s.opState == CabinState.OP_ON && s.auto == 1;
        if (MANUAL.equals(item)) return s.opState == CabinState.OP_ON && s.auto == 0;
        if (AC.equals(item)) return s.cabinKnown() && onOff(s.ac) && s.ac == p.ac;
        if (AIRFLOW.equals(item)) return s.cabinKnown() && s.airflow == p.airflow;
        if (FAN.equals(item)) return s.cabinKnown() && s.fanRaw == p.fan + 1;
        if (SYNC.equals(item)) return s.cabinKnown() && onOff(s.sync) && s.sync == p.sync;
        if (DRIVER_TEMP.equals(item)) return tempAt(s, s.driverTemp, p.driverTempF);
        if (PASSENGER_TEMP.equals(item)) {
            // a synced passenger follows the driver and its own reading may lag (as the runner judges it)
            return tempAt(s, s.sync == 1 ? s.driverTemp : s.passengerTemp, p.passengerTempF);
        }
        return false;
    }

    private static boolean seatAt(int[] seats, Seat seat, int target) {
        if (seats == null || seat.ordinal() >= seats.length) return false;
        int v = seats[seat.ordinal()];
        return SeatValues.isKnown(v) && v == target;
    }

    private static boolean tempAt(CabinState s, float reading, int targetF) {
        if (targetF == Preset.LEAVE) return false;
        if (!PresetRunner.tempKnown(s) || !PresetRunner.shown(reading)) return false;
        return PresetRunner.stepsOf(reading, s.unit) == PresetRunner.targetSteps(s.unit, targetF);
    }

    private static boolean onOff(int v) { return v == 0 || v == 1; }
}
