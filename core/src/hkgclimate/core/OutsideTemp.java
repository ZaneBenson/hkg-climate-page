package hkgclimate.core;

/** One outside-temperature reading: a Celsius and a Fahrenheit value, each with its state. */
public final class OutsideTemp {
    private static final int GOOD_STATE = 0xffff;
    private static final float NO_VALUE = 100000f;
    private static final float SENSOR_FAULT = -40f;

    public static final OutsideTemp UNKNOWN = new OutsideTemp(0f, 0, 0f, 0);

    private final float tempC;
    private final int stateC;
    private final float tempF;
    private final int stateF;

    private OutsideTemp(float tempC, int stateC, float tempF, int stateF) {
        this.tempC = tempC;
        this.stateC = stateC;
        this.tempF = tempF;
        this.stateF = stateF;
    }

    public static OutsideTemp of(float tempC, int stateC, float tempF, int stateF) {
        return new OutsideTemp(tempC, stateC, tempF, stateF);
    }

    private static boolean good(float value, int state) {
        return state == GOOD_STATE && !Float.isNaN(value) && !Float.isInfinite(value)
                && value != NO_VALUE && value != SENSOR_FAULT;
    }

    /** True when the Fahrenheit reading is usable (the heat/cool choice compares in Fahrenheit). */
    public boolean valid() {
        return good(tempF, stateF);
    }

    public float fahrenheit() {
        return tempF;
    }

    /**
     * "65 °F" or "18 °C" (whole degrees, rounded down). rawUnit 1 is Celsius; anything else is
     * Fahrenheit. Null when the reading is not valid.
     */
    public String display(int rawUnit) {
        if (!valid()) return null;
        if (rawUnit == 1) {
            if (!good(tempC, stateC)) return null;
            return (int) Math.floor(tempC) + " °C";
        }
        return (int) Math.floor(tempF) + " °F";
    }
}
