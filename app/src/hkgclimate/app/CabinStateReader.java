package hkgclimate.app;

import android.content.Context;
import com.hkmc.VehicleInfoManager;
import com.hkmc.datc.DATCManager;
import hkgclimate.core.CabinReader;
import hkgclimate.core.CabinState;

/**
 * Reads the cabin climate, the steering wheel and eco mode from the stock services for the preset
 * runner. Every read() asks the services again (nothing is kept between reads): the DATC manager
 * is looked up through VehicleInfoManager each time, and each value is its own getter call, guarded
 * on its own, so one that throws or reports -1 (or -1.0) is unknown and the others still count.
 * Without the DATC service every climate value is unknown; the steering wheel and eco still come
 * from the seat service (they need only that one). It only reads: nothing here sends. Main thread
 * only (the runner runs there).
 *
 * Where the values come from (the stock service, firmware 260507): every getVRClimateStatus flag
 * read here but 2 comes from one status cache in the DATC service (the bytes the car last sent:
 * operating state, auto, A/C, airflow, fan, sync, the passenger's raw temperature), as does
 * getDriverOnlyValue; the driver temperature (flag 2) and getTemperatureUnit come from its second,
 * converted cache. Each getter is its own binder call, so one read() is not one snapshot: a car
 * update can land between two getters.
 */
final class CabinStateReader implements CabinReader {
    // getVRClimateStatus flags (DATCFlag names in the comments)
    /** TEMP: the driver's temperature in the display unit (°C or °F), 0 when not shown. */
    private static final int VR_DRIVER_TEMP = 2;
    /** BLOWER_LEVEL: 0 off, 1 auto, 2..9 speeds 1..8. */
    private static final int VR_FAN = 5;
    /** BLOWER_MODE: 0 auto, 1..4 airflow. */
    private static final int VR_AIRFLOW = 6;
    /** AUTO: 1 on, 0 off. */
    private static final int VR_AUTO = 8;
    /** AC: 1 on, 0 off. */
    private static final int VR_AC = 9;
    /** OP_STATE: 1 on, 2 off. */
    private static final int VR_OP_STATE = 12;
    /** SYNC: 1 on, 0 off. */
    private static final int VR_SYNC = 22;
    /** TEMP_PS_C / TEMP_PS_F: the passenger's raw display byte, 0 when not shown. */
    private static final int VR_PASSENGER_RAW_C = 29;
    private static final int VR_PASSENGER_RAW_F = 31;

    /** A temperature above this is not a display value (the service stores 100000 for invalid). */
    private static final float MAX_TEMP = 200f;
    /** Raw display byte to °F: byte + 56 (15 = 71 °F); to °C: byte / 2 + 14. */
    private static final int RAW_F_OFFSET = 56;
    private static final int RAW_C_OFFSET = 14;

    private static final int U = CabinState.UNKNOWN;

    private final Context context;
    private final UsmSeatCalls usm;

    CabinStateReader(Context context, UsmSeatCalls usm) {
        this.context = context;
        this.usm = usm;
    }

    @Override
    public CabinState read() {
        try {
            return readNow();
        } catch (Throwable t) {
            return CabinState.NONE;
        }
    }

    private CabinState readNow() {
        int wheel = wheelState();
        int eco = ecoState();
        DATCManager m = datc();
        if (m == null) {
            return CabinState.of(U, U, U, U, U, U, U, U, Float.NaN, Float.NaN, wheel, eco);
        }
        int unit = unit(m);
        int opState = state(vr(m, VR_OP_STATE));
        int auto = state(vr(m, VR_AUTO));
        int ac = state(vr(m, VR_AC));
        int airflow = state(vr(m, VR_AIRFLOW));
        int fanRaw = state(vr(m, VR_FAN));
        int sync = state(vr(m, VR_SYNC));
        int driverOnly = driverOnly(m);
        float driverTemp = temperature(vr(m, VR_DRIVER_TEMP));
        float passengerTemp = passenger(m, unit);
        return CabinState.of(unit, opState, auto, ac, airflow, fanRaw, sync, driverOnly,
                driverTemp, passengerTemp, wheel, eco);
    }

    /** The DATC manager the stock services hold now; null when there is none yet. */
    private DATCManager datc() {
        try {
            Object service = VehicleInfoManager.getInstance(context).getService(VehicleInfoManager.DATC_SERVICE);
            return service instanceof DATCManager ? (DATCManager) service : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private int wheelState() {
        try {
            return onOff(usm.wheelState());
        } catch (Throwable t) {
            return U;
        }
    }

    private int ecoState() {
        try {
            return onOff(usm.ecoState());
        } catch (Throwable t) {
            return U;
        }
    }

    /** One getVRClimateStatus; -1.0 (the stock "unknown") when it throws. */
    private static float vr(DATCManager m, int flag) {
        try {
            return m.getVRClimateStatus(flag);
        } catch (Throwable t) {
            return -1f;
        }
    }

    private static int unit(DATCManager m) {
        try {
            int u = m.getTemperatureUnit();
            return u == CabinState.UNIT_CELSIUS || u == CabinState.UNIT_FAHRENHEIT ? u : U;
        } catch (Throwable t) {
            return U;
        }
    }

    private static int driverOnly(DATCManager m) {
        try {
            int v = m.getDriverOnlyValue();
            return v < 0 ? U : v;
        } catch (Throwable t) {
            return U;
        }
    }

    /**
     * The passenger's temperature in the display unit from its raw byte (the flag for that unit):
     * 0 when the car does not show it, NaN when unknown.
     */
    private static float passenger(DATCManager m, int unit) {
        if (unit == CabinState.UNIT_FAHRENHEIT) {
            int raw = state(vr(m, VR_PASSENGER_RAW_F));
            if (raw == U) return Float.NaN;
            return raw == 0 ? 0f : raw + RAW_F_OFFSET;
        }
        if (unit == CabinState.UNIT_CELSIUS) {
            int raw = state(vr(m, VR_PASSENGER_RAW_C));
            if (raw == U) return Float.NaN;
            return raw == 0 ? 0f : raw / 2f + RAW_C_OFFSET;
        }
        return Float.NaN;
    }

    /** A status value as an int: negative (the stock -1.0) or NaN is unknown. */
    static int state(float v) {
        if (Float.isNaN(v) || v < 0) return U;
        return Math.round(v);
    }

    /** A temperature as shown: negative, NaN or not a display value is NaN; 0 means not shown. */
    static float temperature(float v) {
        if (Float.isNaN(v) || v < 0 || v > MAX_TEMP) return Float.NaN;
        return v;
    }

    private static int onOff(int v) {
        return v == 0 || v == 1 ? v : U;
    }
}
