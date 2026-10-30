package hkgclimate.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import hkgclimate.core.CheckedSeatSender;
import hkgclimate.core.ClimateCore;
import hkgclimate.core.Clock;
import hkgclimate.core.Seat;
import hkgclimate.core.SeatCommander;
import hkgclimate.core.SendLog;
import hkgclimate.core.SendWatch;

/**
 * The climate service the page binds to. It owns the core, reads the car through CarLink and
 * serves the page state through PageLinkBinder. A tap goes to the CheckedSeatSender (the stock
 * seat service, through UsmSeatCalls). Nothing here sends on its own: no send at start, on a
 * settings change or as a retry. A send the car does not follow is reported (SendWatch), not resent.
 */
public class ClimateService extends Service {
    private static final String TAG = CarLink.TAG;

    private Prefs prefs;
    private ClimateCore core;
    private CarLink car;
    private PageLinkBinder binder;
    private PageListKeeper keeper;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SendWatch watch = new SendWatch();
    private final Runnable watchCheck = new Runnable() {
        @Override public void run() { checkWatch(); }
    };
    private CheckedSeatSender sender;
    // main thread: the sender's last result or the latest "no change" text, whichever came last
    private String lastSend;
    private boolean destroyed;
    // held here: SharedPreferences keeps only a weak reference to its listeners
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (core != null && (key == null || Prefs.KEY_SWITCH_TEMP_F.equals(key))) {
                        core.setSwitchTempF(prefs.getSwitchTempF());
                    }
                    if (keeper != null && (key == null || Prefs.KEY_PAGE_ENABLED.equals(key))) {
                        try {
                            keeper.setEnabled(prefs.isPageEnabled());
                        } catch (Throwable t) {
                            Log.w(TAG, "updating the page lists failed", t);
                        }
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        // start ourselves: a service that is only bound dies with its last bind (the panel unbinds
        // when our page leaves its list), and START_STICKY only keeps a started service alive
        try {
            startService(new Intent(this, ClimateService.class));
        } catch (Throwable t) {
            Log.w(TAG, "climate service could not start itself", t);
        }
        try {
            prefs = new Prefs(this);
            // the stock seat service is not touched here: UsmSeatCalls makes its manager on the first send
            sender = new CheckedSeatSender(new UsmSeatCalls(this), new SendLog() {
                @Override public void line(String text) { logSend(text); }
            }, watch, new Clock() {
                @Override public long now() { return SystemClock.elapsedRealtime(); }
            });
            SeatCommander send = new SeatCommander() {
                @Override
                public void set(Seat seat, int value) {
                    sender.set(seat, value);
                    afterSend();
                }
            };
            core = new ClimateCore(send, prefs.getSwitchTempF());
            car = new CarLink(this, core, watch);
            binder = new PageLinkBinder(core, new PageLinkBinder.DebugSource() {
                @Override public Bundle debug() {
                    Bundle d = car.debug();
                    PageListKeeper k = keeper;
                    d.putString(PageStates.D_PAGE_LIST_ERROR, k == null ? null : k.lastError());
                    d.putBoolean(PageStates.D_APPROVED, prefs.isSeatApproved());
                    d.putString(PageStates.D_LAST_SEND, lastSend);
                    return d;
                }
            });
            car.setOnDebugChange(new Runnable() {
                @Override public void run() { binder.onDebugChanged(); }
            });
            prefs.register(prefListener);
            car.start();
        } catch (Throwable t) {
            Log.e(TAG, "climate service failed to start", t);
        }
        // its own try: the page lists must not depend on the car link coming up, nor the reverse
        try {
            keeper = new PageListKeeper(this);
            keeper.setOnChange(new Runnable() {
                @Override public void run() {
                    if (binder != null) binder.onDebugChanged();
                }
            });
            keeper.setEnabled(prefs.isPageEnabled());
        } catch (Throwable t) {
            Log.e(TAG, "page list keeper failed to start", t);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /** Main thread, after each tap the sender handled: record its result and watch a send. */
    private void afterSend() {
        try {
            String result = sender.lastResult();
            lastSend = result;
            if (!destroyed && result != null && result.startsWith("sent ")) {
                handler.postDelayed(watchCheck, SendWatch.WAIT_MS);
            }
            debugChanged();
        } catch (Throwable t) {
            Log.w(TAG, "recording the send result failed", t);
        }
    }

    /** Main thread, WAIT_MS after a send: report a send the car has not followed (never resend). */
    private void checkWatch() {
        try {
            String late = watch.overdue(SystemClock.elapsedRealtime());
            if (late == null) return;
            logSend(late);
            lastSend = late;
            debugChanged();
        } catch (Throwable t) {
            Log.w(TAG, "checking the send failed", t);
        }
    }

    private void debugChanged() {
        PageLinkBinder b = binder;
        if (b != null) b.onDebugChanged();
    }

    /** The send log: a refusal, a failure or a send the car ignored is a warning. */
    static void logSend(String line) {
        if (line == null) return;
        if (line.startsWith("send refused") || line.startsWith("send failed") || line.startsWith("no change")) {
            Log.w(TAG, line);
        } else {
            Log.i(TAG, line);
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(watchCheck);
        try {
            if (prefs != null) prefs.unregister(prefListener);
            if (keeper != null) keeper.close();
            if (binder != null) binder.close();
            if (car != null) car.stop();
        } catch (Throwable t) {
            Log.w(TAG, "climate service stop failed", t);
        }
        super.onDestroy();
    }
}
