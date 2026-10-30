package hkgclimate.core;

/** The numbers the stock seat service uses, and what they mean. */
public final class SeatValues {
    public static final int OFF = 2;
    public static final int HEAT_HIGH = 8;
    public static final int HEAT_MID = 7;
    public static final int HEAT_LOW = 6;
    public static final int COOL_HIGH = 5;
    public static final int COOL_MID = 4;
    public static final int COOL_LOW = 3;
    /** Seat option value meaning heat and cool are both fitted; anything else is heat only. */
    public static final int HEAT_AND_COOL_OPTION = 6;

    private SeatValues() { }

    public static boolean isKnown(int v) {
        return v >= OFF && v <= HEAT_HIGH;
    }

    /** 0 off, 1 low, 2 mid, 3 high; 0 for an unknown value. */
    public static int level(int v) {
        switch (v) {
            case HEAT_HIGH: case COOL_HIGH: return 3;
            case HEAT_MID: case COOL_MID: return 2;
            case HEAT_LOW: case COOL_LOW: return 1;
            default: return 0;
        }
    }

    /** HEAT or COOL; null for off and for unknown values. */
    public static Mode modeOf(int v) {
        switch (v) {
            case HEAT_HIGH: case HEAT_MID: case HEAT_LOW: return Mode.HEAT;
            case COOL_HIGH: case COOL_MID: case COOL_LOW: return Mode.COOL;
            default: return null;
        }
    }

    /** Plain words for the page, or null for an unknown value. */
    public static String words(int v) {
        switch (v) {
            case OFF: return "off";
            case HEAT_HIGH: return "heat high";
            case HEAT_MID: return "heat mid";
            case HEAT_LOW: return "heat low";
            case COOL_HIGH: return "cool high";
            case COOL_MID: return "cool mid";
            case COOL_LOW: return "cool low";
            default: return null;
        }
    }
}
