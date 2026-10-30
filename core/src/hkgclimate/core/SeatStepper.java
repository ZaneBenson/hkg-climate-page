package hkgclimate.core;

/** What one tap on a seat button does: off -> high -> mid -> low -> off. */
public final class SeatStepper {
    private SeatStepper() { }

    /**
     * The value to set after a tap. Off turns on to high of modeIfTurningOn; a seat that is
     * already on steps down within its own mode. -1 when current is not a known value.
     */
    public static int next(int current, Mode modeIfTurningOn) {
        switch (current) {
            case SeatValues.OFF:
                return modeIfTurningOn == Mode.COOL ? SeatValues.COOL_HIGH : SeatValues.HEAT_HIGH;
            case SeatValues.HEAT_HIGH: return SeatValues.HEAT_MID;
            case SeatValues.HEAT_MID: return SeatValues.HEAT_LOW;
            case SeatValues.COOL_HIGH: return SeatValues.COOL_MID;
            case SeatValues.COOL_MID: return SeatValues.COOL_LOW;
            case SeatValues.HEAT_LOW:
            case SeatValues.COOL_LOW:
                return SeatValues.OFF;
            default: return -1;
        }
    }

    /**
     * Heat unless the car has heat and cool, the outside temperature is known, and it is at or
     * above the switch temperature.
     */
    public static Mode chooseMode(OutsideTemp t, int switchTempF, int option) {
        if (option != SeatValues.HEAT_AND_COOL_OPTION) return Mode.HEAT;
        if (t == null || !t.valid()) return Mode.HEAT;
        return t.fahrenheit() < switchTempF ? Mode.HEAT : Mode.COOL;
    }
}
