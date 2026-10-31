package hkgclimate.core;

/**
 * What the car reported for the cabin climate, the steering wheel and eco mode at one moment.
 * Every int is UNKNOWN (-1) when it could not be read; the temperatures are Float.NaN then.
 * unit: 1 Celsius, 2 Fahrenheit. opState: 1 on, 2 off. auto, ac, sync, driverOnly: 1 on, 0 off.
 * airflow: 0 auto, 1..4 as in Preset. fanRaw: 0 off, 1 auto, 2..9 speeds 1..8. Temperatures are
 * in the display unit; the passenger's is 0 or NaN when the car does not show it. wheel: 1 on,
 * 0 off. eco: 1 on, 0 off (as the getter reports it, not as it is sent).
 */
public final class CabinState {
    public static final int UNKNOWN = -1;
    public static final int UNIT_CELSIUS = 1;
    public static final int UNIT_FAHRENHEIT = 2;
    public static final int OP_ON = 1;
    public static final int OP_OFF = 2;

    /** Nothing known. */
    public static final CabinState NONE = of(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
            UNKNOWN, UNKNOWN, Float.NaN, Float.NaN, UNKNOWN, UNKNOWN);

    public final int unit;
    public final int opState;
    public final int auto;
    public final int ac;
    public final int airflow;
    public final int fanRaw;
    public final int sync;
    public final int driverOnly;
    public final float driverTemp;
    public final float passengerTemp;
    public final int wheel;
    public final int eco;

    private CabinState(int unit, int opState, int auto, int ac, int airflow, int fanRaw, int sync,
            int driverOnly, float driverTemp, float passengerTemp, int wheel, int eco) {
        this.unit = unit;
        this.opState = opState;
        this.auto = auto;
        this.ac = ac;
        this.airflow = airflow;
        this.fanRaw = fanRaw;
        this.sync = sync;
        this.driverOnly = driverOnly;
        this.driverTemp = driverTemp;
        this.passengerTemp = passengerTemp;
        this.wheel = wheel;
        this.eco = eco;
    }

    public static CabinState of(int unit, int opState, int auto, int ac, int airflow, int fanRaw,
            int sync, int driverOnly, float driverTemp, float passengerTemp, int wheel, int eco) {
        return new CabinState(unit, opState, auto, ac, airflow, fanRaw, sync, driverOnly,
                driverTemp, passengerTemp, wheel, eco);
    }

    /** True when the climate's operating state is known (on or off). */
    public boolean cabinKnown() { return opState == OP_ON || opState == OP_OFF; }

    @Override
    public String toString() {
        return "unit " + unit + ", op " + opState + ", auto " + auto + ", ac " + ac + ", airflow "
                + airflow + ", fan " + fanRaw + ", sync " + sync + ", driver only " + driverOnly
                + ", " + driverTemp + "/" + passengerTemp + ", wheel " + wheel + ", eco " + eco;
    }
}
