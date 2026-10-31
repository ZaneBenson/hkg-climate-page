package hkgclimate.core;

/**
 * The choices of the preset editor for every item except the seats (see {@link SeatChoices}), each
 * as (value, label) pairs in the order shown and starting with Leave as is, and which items the
 * climate mode greys out.
 */
public final class PresetChoices {
    /** The car's temperature unit value for Celsius (DATC getTemperatureUnit: 1 C, 2 F). */
    public static final int UNIT_CELSIUS = 1;

    public static final String ITEM_WHEEL = "wheel";
    public static final String ITEM_ECO = "eco";
    public static final String ITEM_CLIMATE = "climate";
    public static final String ITEM_TEMPERATURE = "temperature";
    public static final String ITEM_FAN = "fan";
    public static final String ITEM_AC = "ac";
    public static final String ITEM_AIRFLOW = "airflow";
    public static final String ITEM_SYNC = "sync";

    private static final String LEAVE_LABEL = "Leave as is";

    /** One offered value and the text the editor shows for it. */
    public static final class Choice {
        public final int value;
        public final String label;

        Choice(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    private PresetChoices() { }

    public static Choice[] wheel() { return onOff(); }

    public static Choice[] eco() { return onOff(); }

    public static Choice[] ac() { return onOff(); }

    public static Choice[] sync() { return onOff(); }

    public static Choice[] climate() {
        return new Choice[] {leave(), new Choice(Preset.CLIMATE_OFF, "Off"),
                new Choice(Preset.CLIMATE_AUTO, "Auto"), new Choice(Preset.CLIMATE_MANUAL, "Manual")};
    }

    public static Choice[] fan() {
        Choice[] out = new Choice[Preset.FAN_MAX - Preset.FAN_MIN + 2];
        out[0] = leave();
        for (int v = Preset.FAN_MIN; v <= Preset.FAN_MAX; v++) {
            out[v - Preset.FAN_MIN + 1] = new Choice(v, String.valueOf(v));
        }
        return out;
    }

    public static Choice[] airflow() {
        return new Choice[] {leave(), new Choice(1, "Face"), new Choice(2, "Face and floor"),
                new Choice(3, "Floor"), new Choice(4, "Floor and defrost")};
    }

    /**
     * 62 to 82 degrees Fahrenheit; the value is always the Fahrenheit one. For the Celsius unit
     * the label is that temperature in Celsius to the nearest half degree, like "21.0 °C".
     */
    public static Choice[] temperatureChoices(int unit) {
        Choice[] out = new Choice[Preset.TEMP_MAX_F - Preset.TEMP_MIN_F + 2];
        out[0] = leave();
        for (int f = Preset.TEMP_MIN_F; f <= Preset.TEMP_MAX_F; f++) {
            String label = unit == UNIT_CELSIUS ? celsiusHalf(f) + " °C" : f + " °F";
            out[f - Preset.TEMP_MIN_F + 1] = new Choice(f, label);
        }
        return out;
    }

    /** The Celsius temperature of a Fahrenheit one, to the nearest 0.5. */
    public static double celsiusHalf(int f) {
        return Math.round((f - 32) * 5 / 9.0 * 2) / 2.0;
    }

    /** The position of the value in the choices; 0 (Leave as is) for a value that is not offered. */
    public static int positionOf(Choice[] choices, int value) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].value == value) return i;
        }
        return 0;
    }

    /**
     * Whether the climate mode greys the item out (it keeps showing its value but is stored as
     * Leave as is). Auto greys fan, A/C and airflow; Off also greys temperature and sync; any
     * other mode, and the seats, wheel, eco and climate itself, are never greyed.
     */
    public static boolean greyed(String item, int climate) {
        if (climate != Preset.CLIMATE_AUTO && climate != Preset.CLIMATE_OFF) return false;
        if (ITEM_FAN.equals(item) || ITEM_AC.equals(item) || ITEM_AIRFLOW.equals(item)) return true;
        return climate == Preset.CLIMATE_OFF && (ITEM_TEMPERATURE.equals(item) || ITEM_SYNC.equals(item));
    }

    private static Choice leave() { return new Choice(Preset.LEAVE, LEAVE_LABEL); }

    private static Choice[] onOff() {
        return new Choice[] {leave(), new Choice(1, "On"), new Choice(0, "Off")};
    }
}
