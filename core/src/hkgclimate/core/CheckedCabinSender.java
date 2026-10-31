package hkgclimate.core;

/**
 * Sends the climate, steering-wheel and eco commands through the stock services, but only the
 * flag and value pairs the stock controls were seen sending on the owner's car. Anything else is
 * refused with no call. Failures are logged, never thrown, and never retried. This is the safety
 * boundary for these commands: nothing it calls (the log, the scheduler) can make a send throw or
 * make a stock call twice, and a steering-wheel 0 is only ever sent as the release of our own 1.
 * Single-threaded: call it, and run the scheduler's tasks, on one thread.
 */
public final class CheckedCabinSender {
    /** Time between the steering-wheel press (1) and its release (0), as the stock button. */
    public static final long WHEEL_RELEASE_MS = 200;

    public static final int FLAG_PASSENGER_TEMP = 49;
    public static final int FLAG_DRIVER_TEMP = 50;
    public static final int FLAG_FAN = 4;
    public static final int FLAG_CLIMATE_OFF = 51;
    public static final int FLAG_AUTO = 8;
    public static final int FLAG_AC = 9;
    public static final int FLAG_AIRFLOW = 6;
    public static final int FLAG_SYNC = 22;
    /** The step value for one press up, and for one press down (temperature and fan flags). */
    public static final int UP = 1;
    public static final int DOWN = 2;
    /** Eco mode as sent (the getter reports 1 on, 0 off). */
    public static final int ECO_SEND_ON = 2;
    public static final int ECO_SEND_OFF = 1;

    private static final String CLIMATE = "setFrontClimateControl";
    private static final String WHEEL = "setSWRC_SteeringwheelWarmerSet";
    private static final String ECO = "setClimateControlECOMode";

    private final CabinCalls calls;
    private final SendLog log;
    /** The press whose release is due; null when none. Each press is its own object. */
    private Object pendingPress;

    public CheckedCabinSender(CabinCalls calls, SendLog log) {
        this.calls = calls;
        this.log = log;
    }

    /**
     * True for exactly the pairs the stock climate screen was seen sending: flag 50 or 49 with 1
     * or 2; 4 with 1 or 2; 51 with 1; 8 with 1; 9 with 0 or 1; 6 with 1..4; 22 with 1 or 2.
     */
    public static boolean allowed(int flag, int value) {
        switch (flag) {
            case FLAG_DRIVER_TEMP:
            case FLAG_PASSENGER_TEMP:
            case FLAG_FAN:
            case FLAG_SYNC:
                return value == 1 || value == 2;
            case FLAG_CLIMATE_OFF:
            case FLAG_AUTO:
                return value == 1;
            case FLAG_AC:
                return value == 0 || value == 1;
            case FLAG_AIRFLOW:
                return value >= 1 && value <= 4;
            default:
                return false;
        }
    }

    /** One setFrontClimateControl call if the pair is allowed. True only when the call returned. */
    public boolean climate(int flag, int value) {
        String text = CLIMATE + " " + flag + " " + value;
        if (!allowed(flag, value)) {
            record("send refused " + text);
            return false;
        }
        try {
            calls.climate(flag, value);
        } catch (Throwable t) {
            record("send failed " + text + ": " + describe(t));
            return false;
        }
        record("sent " + text);
        return true;
    }

    /** One setClimateControlECOMode call with 2 (on) or 1 (off). True only when the call returned. */
    public boolean eco(int value) {
        String text = ECO + " " + value;
        if (value != ECO_SEND_ON && value != ECO_SEND_OFF) {
            record("send refused " + text);
            return false;
        }
        try {
            calls.eco(value);
        } catch (Throwable t) {
            record("send failed " + text + ": " + describe(t));
            return false;
        }
        record("sent " + text);
        return true;
    }

    /**
     * One steering-wheel press: sends 1 now and 0 WHEEL_RELEASE_MS later on the scheduler. Refused
     * while an earlier press is still pending, or when the release can't be scheduled (it is
     * scheduled before the 1 is sent). If the 1 fails, no 0 is sent. True when the 1 was sent.
     */
    public boolean wheelPress(Scheduler scheduler) {
        String text = WHEEL + " 1";
        if (pendingPress != null) {
            record("send refused " + text + ": a press is pending");
            return false;
        }
        final Object press = new Object();
        try {
            scheduler.after(WHEEL_RELEASE_MS, new Runnable() {
                @Override public void run() { release(press); }
            });
        } catch (Throwable t) {
            record("send refused " + text + ": no release: " + describe(t));
            return false;
        }
        pendingPress = press;
        try {
            calls.wheel(1);
        } catch (Throwable t) {
            // the scheduled release sees a different (or no) pending press and sends nothing
            pendingPress = null;
            record("send failed " + text + ": " + describe(t));
            return false;
        }
        record("sent " + text);
        return true;
    }

    /** True while a steering-wheel press is waiting for its release. */
    public boolean wheelPending() { return pendingPress != null; }

    /** Sends the 0 for this press, only if it is still the pending one (its 1 was sent). */
    private void release(Object press) {
        try {
            if (pendingPress != press) return;
            pendingPress = null;
            String text = WHEEL + " 0";
            try {
                calls.wheel(0);
            } catch (Throwable t) {
                record("send failed " + text + ": " + describe(t));
                return;
            }
            record("sent " + text);
        } catch (Throwable t) {
            // nothing here may reach the scheduler's thread, and the 0 is never sent twice
        }
    }

    /** The throwable's text, or its class name when even that throws. */
    private static String describe(Throwable t) {
        try {
            return String.valueOf(t);
        } catch (Throwable e) {
            return t.getClass().getName();
        }
    }

    /** Logs the text; a failing log is ignored. */
    private void record(String text) {
        try {
            log.line(text);
        } catch (Throwable t) {
            // a broken log must not stop a send or reach the caller
        }
    }
}
