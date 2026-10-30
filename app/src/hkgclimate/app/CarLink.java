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

/**
 * Reads the seat heaters and the outside temperature from the stock services and feeds them to
 * the core. Everything runs on the main looper (start, stop and the stock callbacks). It only
 * reads: nothing here sends a command. Each car part (seats, temperature) is set up on its own;
 * one that fails is logged and tried again every 10 seconds until stop().
 */
final class CarLink implements PageLinkBinder.DebugSource {
    static final String TAG = "HkgClimate";
    private static final long RETRY_MS = 10000;
    private static final int UNSET = -1;

    private final Context context;
    private final ClimateCore core;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retry = new Runnable() {
        @Override public void run() { attempt(); }
    };

    private boolean running;
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

    CarLink(Context context, ClimateCore core) {
        this.context = context;
        this.core = core;
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
                Log.w(TAG, "seat service not available, will retry", t);
                closeSeats();
            }
        }
        if (!tempReady) {
            try {
                openTemperature();
            } catch (Throwable t) {
                Log.w(TAG, "temperature service not available, will retry", t);
                closeTemperature();
            }
        }
        if (!seatsReady || !tempReady) handler.postDelayed(retry, RETRY_MS);
    }

    private void openSeats() {
        BCMManager m = new BCMManager(context);
        bcm = m;
        drvOption = m.getDrvSeatHeatOption();
        astOption = m.getAstSeatHeatOption();
        core.onSeatOption(Seat.DRIVER, drvOption);
        core.onSeatOption(Seat.PASSENGER, astOption);
        drvState = m.getDrvSeatHeatState();
        astState = m.getAstSeatHeatState();
        core.onSeatState(Seat.DRIVER, drvState);
        core.onSeatState(Seat.PASSENGER, astState);
        seatListener = new BCMVehicleStatusListener() {
            @Override
            public void onDrvSeatHeaterStatusChanged(int state) {
                try {
                    drvState = state;
                    core.onSeatState(Seat.DRIVER, state);
                } catch (Throwable t) {
                    Log.w(TAG, "driver seat callback failed", t);
                }
            }

            @Override
            public void onAstSeatHeaterStatusChanged(int state) {
                try {
                    astState = state;
                    core.onSeatState(Seat.PASSENGER, state);
                } catch (Throwable t) {
                    Log.w(TAG, "passenger seat callback failed", t);
                }
            }
        };
        m.addVehicleStatusListener(seatListener);
        seatsReady = true;
        core.onSeatsReady(true);
    }

    private void closeSeats() {
        BCMManager m = bcm;
        BCMVehicleStatusListener l = seatListener;
        bcm = null;
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
        if (was) core.onSeatsReady(false);
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
                    tempC = amTempC;
                    stateC = amStateC;
                    tempF = amTempF;
                    stateF = amStateF;
                    core.onOutside(OutsideTemp.of(amTempC, amStateC, amTempF, amStateF));
                    readUnit();
                } catch (Throwable t) {
                    Log.w(TAG, "temperature callback failed", t);
                }
            }
        };
        m.addTemperatureListener(tempListener);
        readUnit();
        tempReady = true;
    }

    private void readUnit() {
        DATCManager m = datc;
        if (m == null) return;
        unit = m.getTemperatureUnit();
        core.onDisplayUnit(unit);
    }

    private void closeTemperature() {
        DATCManager m = datc;
        DATCTemperatureListener l = tempListener;
        datc = null;
        tempListener = null;
        tempReady = false;
        if (m != null && l != null) {
            try {
                m.removeTemperatureListener(l);
            } catch (Throwable t) {
                Log.w(TAG, "removing the temperature listener failed", t);
            }
        }
    }
}
