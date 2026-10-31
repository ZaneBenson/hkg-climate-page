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
import hkgclimate.core.CheckedCabinSender;
import hkgclimate.core.CheckedSeatSender;
import hkgclimate.core.ClimateCore;
import hkgclimate.core.Clock;
import hkgclimate.core.Preset;
import hkgclimate.core.PresetRunner;
import hkgclimate.core.Scheduler;
import hkgclimate.core.Seat;
import hkgclimate.core.SeatCommander;
import hkgclimate.core.SendLog;
import hkgclimate.core.SendSummary;
import hkgclimate.core.SendWatch;
import java.util.ArrayList;
import java.util.List;

/**
 * The climate service the page binds to. It owns the core, reads the car through CarLink and
 * serves the page state through PageLinkBinder. A seat tap goes to the CheckedSeatSender (the
 * stock seat service, through UsmSeatCalls). A preset tap does its seats the same way, then starts
 * the PresetRunner, which reads the car through CabinStateReader and sends the steering-wheel, eco
 * and climate commands through the CheckedCabinSender (StockCabinCalls: UsmSeatCalls and
 * DatcClimateCalls), one step at a time on the main looper. Nothing here sends on its own: no send
 * at start, on a settings change or as a retry. A seat send the car does not follow is reported
 * (SendWatch), not resent; the runner reports its own items.
 */
public class ClimateService extends Service {
    private static final String TAG = CarLink.TAG;
    /** Added to the last-command line when a tapped preset's seats were skipped for that. */
    static final String SEATS_NOT_READY = "seats not ready";

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
    private PresetRunner runner;
    // main thread: the presets as given to the core, to look up a tapped slot
    private List<Preset> presets = new ArrayList<Preset>();
    // main thread: the sender's last result or the latest "no change" text, whichever came last
    private String lastSend;
    // main thread: the last tap started the preset runner, so the last-command line shows its result
    private boolean runShown;
    // main thread: SEATS_NOT_READY when the last tap was a preset whose seats were skipped for that,
    // or "<name>: seats not ready" for a seat-only preset that sent nothing for that reason
    private String seatNote;
    // main thread: the sender's result for each command of the tap being handled
    private final ArrayList<String> tapResults = new ArrayList<String>();
    private boolean destroyed;
    // held here: SharedPreferences keeps only a weak reference to its listeners
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (core != null && (key == null || Prefs.KEY_SWITCH_TEMP_F.equals(key))) {
                        try {
                            core.setSwitchTempF(prefs.getSwitchTempF());
                        } catch (Throwable t) {
                            Log.w(TAG, "reloading the switch temperature failed", t);
                        }
                    }
                    if (core != null && (key == null || Prefs.KEY_PRESETS.equals(key))) {
                        try {
                            loadPresets();
                        } catch (Throwable t) {
                            Log.w(TAG, "reloading the presets failed", t);
                        }
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
            // the stock seat service is not touched here: UsmSeatCalls makes its manager on the first
            // call (a send, or the runner's first read); seats, wheel and eco share that one manager
            UsmSeatCalls usm = new UsmSeatCalls(this);
            sender = new CheckedSeatSender(usm, new SendLog() {
                @Override public void line(String text) { logSend(text); }
            }, watch, new Clock() {
                @Override public long now() { return SystemClock.elapsedRealtime(); }
            });
            SeatCommander send = new SeatCommander() {
                @Override
                public void set(Seat seat, int value) {
                    sender.set(seat, value);
                    tapResults.add(sender.lastResult());
                }
            };
            // the runner's sends and its result update the last-command line as they happen
            SendLog runLog = new SendLog() {
                @Override public void line(String text) {
                    logSend(text);
                    debugChanged();
                }
            };
            CheckedCabinSender cabin = new CheckedCabinSender(
                    new StockCabinCalls(new DatcClimateCalls(this), usm), runLog);
            runner = new PresetRunner(cabin, new CabinStateReader(this, usm), new MainScheduler(), runLog);
            core = new ClimateCore(send, prefs.getSwitchTempF(), runner, new Clock() {
                @Override public long now() { return SystemClock.elapsedRealtime(); }
            });
            loadPresets();
            car = new CarLink(this, core, watch);
            binder = new PageLinkBinder(core, new PageLinkBinder.DebugSource() {
                @Override public Bundle debug() {
                    Bundle d = car.debug();
                    PageListKeeper k = keeper;
                    d.putString(PageStates.D_PAGE_LIST_ERROR, k == null ? null : k.lastError());
                    d.putString(PageStates.D_LAST_SEND, lastCommand());
                    return d;
                }
            }, new PageLinkBinder.AfterSend() {
                @Override public void afterSend(int presetIndex, int sent) {
                    ClimateService.this.afterSend(presetIndex, sent);
                }

                @Override public void presetNotSent(int presetIndex) {
                    ClimateService.this.presetNotSent(presetIndex);
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

    /**
     * Main thread, once after each tap that sent a command (PageLinkBinder calls it): record the worst
     * seat result of the tap (a refusal or failure over a send) and schedule one watch check. The
     * check is scheduled whenever the tap sent anything, so a send that went out is watched even when
     * the tap's other command failed. For a preset tap (presetIndex >= 0): the runner started when
     * the core counted more than the seat sends, and its seats were skipped for not being ready when
     * the preset has seat items and the seat service is not ready.
     */
    private void afterSend(int presetIndex, int sent) {
        try {
            int seatSends = tapResults.size();
            lastSend = SendSummary.worst(tapResults);
            tapResults.clear();
            runShown = presetIndex >= 0 && sent > seatSends;
            seatNote = null;
            if (presetIndex >= 0) {
                Preset p = presetAt(presetIndex);
                if (p != null && p.hasSeatItems() && !seatsReady()) seatNote = SEATS_NOT_READY;
            }
            if (!destroyed) handler.postDelayed(watchCheck, SendWatch.WAIT_MS);
            debugChanged();
        } catch (Throwable t) {
            Log.w(TAG, "recording the send result failed", t);
        }
    }

    /**
     * Main thread, after a preset tap the core sent nothing for: when that was because the seats were
     * not ready (a valid seat-only preset, no preset running, seat service not ready), the
     * last-command line adds "<name>: seats not ready". Nothing else changes: no watch check, the
     * seat result and the runner's line stay as they were. (The page greys such a slot, so this is
     * the race where the seats drop out just before a tap.)
     */
    private void presetNotSent(int presetIndex) {
        try {
            if (runner != null && runner.busy()) return; // ignored because a preset is running
            Preset p = presetAt(presetIndex);
            if (p == null || !p.hasSeatItems() || p.hasRunnerItems() || Preset.check(p) != null) return;
            if (seatsReady()) return;
            seatNote = p.name + ": " + SEATS_NOT_READY;
            debugChanged();
        } catch (Throwable t) {
            Log.w(TAG, "recording the preset result failed", t);
        }
    }

    /**
     * Main thread, WAIT_MS after a tap's sends: report every send the car has not followed, each one
     * logged (never resent); the last-send line shows them all, separated by "; ".
     */
    private void checkWatch() {
        try {
            List<String> late = watch.allOverdue(SystemClock.elapsedRealtime());
            if (late.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (String line : late) {
                logSend(line);
                if (sb.length() > 0) sb.append("; ");
                sb.append(line);
            }
            lastSend = sb.toString();
            debugChanged();
        } catch (Throwable t) {
            Log.w(TAG, "checking the send failed", t);
        }
    }

    private void debugChanged() {
        PageLinkBinder b = binder;
        if (b != null) b.onDebugChanged();
    }

    /** Main thread: the presets from the preferences, to the core and kept for slot lookups. */
    private void loadPresets() {
        List<Preset> list = prefs.getPresets();
        presets = list == null ? new ArrayList<Preset>() : list;
        core.setPresets(list);
    }

    /** The preset in slot index as the core numbers them (it skips nulls); null when there is none. */
    private Preset presetAt(int index) {
        int n = 0;
        for (Preset p : presets) {
            if (p == null) continue;
            if (n == index) return p;
            n++;
        }
        return null;
    }

    private boolean seatsReady() {
        CarLink c = car;
        return c != null && c.debug().getBoolean(PageStates.D_SEATS_READY);
    }

    /**
     * The settings screen's last-command line: the seat result (or the watch's "no change" text);
     * after a preset tap that started the runner, also the runner's result ("<name>: applying"
     * while it runs); and "seats not ready" when the preset's seats were skipped for that.
     */
    private String lastCommand() {
        if (!runShown && seatNote == null) return lastSend;
        StringBuilder sb = new StringBuilder();
        append(sb, lastSend);
        if (runShown) append(sb, runner.lastResult());
        append(sb, seatNote);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null) return;
        if (sb.length() > 0) sb.append("; ");
        sb.append(part);
    }

    /**
     * The runner's scheduler: every task is posted to the main looper, never run inline (the
     * steering-wheel release relies on that). Refused once the service is destroyed or the looper
     * is gone; tasks already posted still run, so a pending release still follows its press.
     */
    private final class MainScheduler implements Scheduler {
        @Override
        public void after(long delayMs, Runnable r) {
            if (destroyed) throw new IllegalStateException("climate service stopped");
            if (!handler.postDelayed(r, delayMs)) throw new IllegalStateException("main looper gone");
        }
    }

    /** The send log: a refusal, a failure, a send the car ignored or an unfinished preset is a warning. */
    static void logSend(String line) {
        if (line == null) return;
        if (line.startsWith("send refused") || line.startsWith("send failed") || line.startsWith("no change")
                || line.contains("not done: ")) {
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
