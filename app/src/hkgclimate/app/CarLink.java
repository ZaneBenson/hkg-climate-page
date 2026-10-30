package hkgclimate.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hkmc.VehicleInfoManager;
import com.hkmc.bcm.BCMManager;
import com.hkmc.bcm.BCMVehicleStatusListener;
import com.hkmc.datc.DATCManager;
import com.hkmc.datc.DATCTemperatureListener;
import hkgclimate.core.ClimateCore;
import hkgclimate.core.OutsideTemp;
import hkgclimate.core.Seat;
import hkgclimate.core.SendWatch;

/**
 * Reads the seat heaters and the outside temperature from the stock services and feeds them to
 * the core, and the seat states to the send watch. Everything runs on the main looper (start, stop
 * and the stock callbacks). It only reads: nothing here sends a command. Each car part (seats,
 * temperature) is set up on its own; one that fails is logged and tried again every 10 seconds
 * until stop().
 */
final class CarLink implements PageLinkBinder.DebugSource {
    static final String TAG = "HkgClimate";
    private static final long RETRY_MS = 10000;
    private static final int UNSET = -1;

    private final Context context;
    private final ClimateCore core;
    private final SendWatch watch;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retry = new Runnable() {
        @Override public void run() { attempt(); }
    };

    private Runnable onDebugChange;
    private boolean running;
    // the full stack trace is logged on a part's first failure only; reset when the part comes up
    private boolean seatFailureLogged;
    private boolean tempFailureLogged;
    private BCMManager bcm;
    private BCMVehicleStatusListener seatListener;
    private boolean seatsReady;
    private DATCManager datc;
    private DATCTemperatureListener tempListener;
    private boolean tempReady;

    // the raw values, for the settings screen
    private int drvState = UNSET;
    private int astState = UNSET;
    private int drvOption = UNSET;
    private int astOption = UNSET;
    private float tempC;
    private int stateC = UNSET;
    private float tempF;
    private int stateF = UNSET;
    private int unit = UNSET;

    CarLink(Context context, ClimateCore core, SendWatch watch) {
        this.context = context;
        this.core = core;
        this.watch = watch;
    }

    /** Run (on the main thread) after a ready flag or a raw debug value changes. */
    void setOnDebugChange(Runnable r) {
        onDebugChange = r;
    }

    /** Main thread. Sets up what it can now and retries the rest every 10 s. */
    void start() {
        running = true;
        attempt();
    }

    /** Main thread. Removes both listeners and cancels the retries. */
    void stop() {
        running = false;
        handler.removeCallbacks(retry);
        closeSeats();
        closeTemperature();
        bcm = null;
    }

    @Override
    public Bundle debug() {
        Bundle d = new Bundle();
        d.putInt(PageStates.D_DRV_STATE, drvState);
        d.putInt(PageStates.D_AST_STATE, astState);
        d.putInt(PageStates.D_DRV_OPTION, drvOption);
        d.putInt(PageStates.D_AST_OPTION, astOption);
        d.putFloat(PageStates.D_TEMP_C, tempC);
        d.putInt(PageStates.D_STATE_C, stateC);
        d.putFloat(PageStates.D_TEMP_F, tempF);
        d.putInt(PageStates.D_STATE_F, stateF);
        d.putInt(PageStates.D_UNIT, unit);
        d.putBoolean(PageStates.D_SEATS_READY, seatsReady);
        d.putBoolean(PageStates.D_TEMP_READY, tempReady);
        return d;
    }

    private void attempt() {
        if (!running) return;
        if (!seatsReady) {
            try {
                openSeats();
            } catch (Throwable t) {
                seatFailureLogged = logFailure("seat service not available, will retry", t, seatFailureLogged);
                closeSeats();
            }
        }
        if (!tempReady) {
            try {
                openTemperature();
            } catch (Throwable t) {
                tempFailureLogged = logFailure("temperature service not available, will retry", t,
                        tempFailureLogged);
                closeTemperature();
            }
        }
        if (!seatsReady || !tempReady) handler.postDelayed(retry, RETRY_MS);
    }

    /** Full stack trace the first time, one line after; returns the new "already logged" flag. */
    private static boolean logFailure(String what, Throwable t, boolean logged) {
        if (logged) Log.w(TAG, what + ": " + t);
        else Log.w(TAG, what, t);
        return true;
    }

    private void debugChanged() {
        Runnable r = onDebugChange;
        if (r == null) return;
        try {
            r.run();
        } catch (Throwable t) {
            Log.w(TAG, "debug change callback failed", t);
        }
    }

    private void openSeats() {
        // one manager for the life of the link: a failing getter must not make a new one every retry
        if (bcm == null) bcm = new BCMManager(context);
        BCMManager m = bcm;
        drvOption = m.getDrvSeatHeatOption();
        astOption = m.getAstSeatHeatOption();
        core.onSeatOption(Seat.DRIVER, drvOption);
        core.onSeatOption(Seat.PASSENGER, astOption);
        drvState = m.getDrvSeatHeatState();
        astState = m.getAstSeatHeatState();
        // the watch first: a core failure must not leave a followed send looking ignored
        watchState(Seat.DRIVER, drvState);
        watchState(Seat.PASSENGER, astState);
        core.onSeatState(Seat.DRIVER, drvState);
        core.onSeatState(Seat.PASSENGER, astState);
        seatListener = new BCMVehicleStatusListener() {
            @Override
            public void onDrvSeatHeaterStatusChanged(int state) {
                watchState(Seat.DRIVER, state);
                try {
                    boolean changed = drvState != state;
                    drvState = state;
                    core.onSeatState(Seat.DRIVER, state);
                    if (changed) debugChanged();
                } catch (Throwable t) {
                    Log.w(TAG, "driver seat callback failed", t);
                }
            }

            @Override
            public void onAstSeatHeaterStatusChanged(int state) {
                watchState(Seat.PASSENGER, state);
                try {
                    boolean changed = astState != state;
                    astState = state;
                    core.onSeatState(Seat.PASSENGER, state);
                    if (changed) debugChanged();
                } catch (Throwable t) {
                    Log.w(TAG, "passenger seat callback failed", t);
                }
            }
        };
        m.addVehicleStatusListener(seatListener);
        seatsReady = true;
        seatFailureLogged = false;
        core.onSeatsReady(true);
        debugChanged();
    }

    /** Tells the send watch a seat state, in its own try so nothing else can skip it. */
    private void watchState(Seat seat, int state) {
        try {
            watch.onState(seat, state);
        } catch (Throwable t) {
            Log.w(TAG, "send watch update failed", t);
        }
    }

    private void closeSeats() {
        BCMManager m = bcm;
        BCMVehicleStatusListener l = seatListener;
        seatListener = null;
        boolean was = seatsReady;
        seatsReady = false;
        if (m != null && l != null) {
            try {
                m.removeVehicleStatusListener(l);
            } catch (Throwable t) {
                Log.w(TAG, "removing the seat listener failed", t);
            }
        }
        if (was) {
            core.onSeatsReady(false);
            debugChanged();
        }
    }

    private void openTemperature() {
        Object service = VehicleInfoManager.getInstance(context).getService(VehicleInfoManager.DATC_SERVICE);
        if (service == null) throw new IllegalStateException("no DATC service yet");
        DATCManager m = (DATCManager) service;
        datc = m;
        tempListener = new DATCTemperatureListener() {
            @Override
            public void onAllTemperatureChanged(float a, int b, float c, int d, float e, int f, float g, int h,
                    float amTempC, int amStateC, float amTempF, int amStateF, float m13, int n, float o, int p,
                    int q) {
                try {
                    boolean changed = tempC != amTempC || stateC != amStateC || tempF != amTempF
                            || stateF != amStateF;
                    tempC = amTempC;
                    stateC = amStateC;
                    tempF = amTempF;
                    stateF = amStateF;
                    core.onOutside(OutsideTemp.of(amTempC, amStateC, amTempF, amStateF));
                    changed |= readUnit();
                    if (changed) debugChanged();
                } catch (Throwable t) {
                    Log.w(TAG, "temperature callback failed", t);
                }
            }
        };
        m.addTemperatureListener(tempListener);
        readUnit();
        tempReady = true;
        tempFailureLogged = false;
        debugChanged();
    }

    /** Reads the display unit into the core; true when the raw unit differs from the last one. */
    private boolean readUnit() {
        DATCManager m = datc;
        if (m == null) return false;
        int now = m.getTemperatureUnit();
        boolean changed = now != unit;
        unit = now;
        core.onDisplayUnit(now);
        return changed;
    }

    private void closeTemperature() {
        DATCManager m = datc;
        DATCTemperatureListener l = tempListener;
        datc = null;
        tempListener = null;
        boolean was = tempReady;
        tempReady = false;
        if (m != null && l != null) {
            try {
                m.removeTemperatureListener(l);
            } catch (Throwable t) {
                Log.w(TAG, "removing the temperature listener failed", t);
            }
        }
        if (was) debugChanged();
    }
}
