package hkgclimate.core;

/**
 * The seat choices of the preset editor, in the order shown, and which of them it offers for a seat.
 * A choice is a position in LABELS/VALUES. Cool choices are offered only on a seat with heat and
 * cool, except that the seat's stored choice is always offered, so a seat the owner does not touch
 * keeps its value when the preset is saved.
 */
public final class SeatChoices {
    public static final String[] LABELS = {"Leave as is", "Off", "Heat high", "Heat mid", "Heat low",
            "Cool high", "Cool mid", "Cool low"};
    public static final int[] VALUES = {Preset.LEAVE, SeatValues.OFF, SeatValues.HEAT_HIGH,
            SeatValues.HEAT_MID, SeatValues.HEAT_LOW, SeatValues.COOL_HIGH, SeatValues.COOL_MID,
            SeatValues.COOL_LOW};
    /** Choices from this position on are the cool ones. */
    public static final int FIRST_COOL = 5;
    public static final String NOT_AVAILABLE = " (not available on this seat)";

    private SeatChoices() { }

    /** True when the raw seat option is known and is not heat and cool; -1 (unknown) counts as heat and cool. */
    public static boolean heatOnly(int option) {
        return option >= 0 && option != SeatValues.HEAT_AND_COOL_OPTION;
    }

    /** The position of the value; 0 (Leave as is) for a value that is not a choice. */
    public static int positionOf(int value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i] == value) return i;
        }
        return 0;
    }

    /** Whether the position is offered for a seat with this option whose stored value is current. */
    public static boolean offered(int position, int option, int current) {
        if (position < FIRST_COOL || !heatOnly(option)) return true;
        return position == positionOf(current);
    }

    /** The button text: a cool choice kept only because it is stored says it is not available. */
    public static String label(int position, int option) {
        return position >= FIRST_COOL && heatOnly(option) ? LABELS[position] + NOT_AVAILABLE : LABELS[position];
    }
}
