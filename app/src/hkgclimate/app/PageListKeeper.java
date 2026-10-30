package hkgclimate.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import hkgclimate.core.PageList;
import hkgclimate.core.ReAddLimiter;

/**
 * Keeps our page (id 4) in the split screen's page lists, which live in three system settings.
 * While enabled it puts the page back if something takes it out, at most once per key per 10
 * seconds. Main thread only: setEnabled, close and the observers all run on the main looper.
 */
final class PageListKeeper {
    private static final String TAG = CarLink.TAG;
    static final String[] KEYS = { "ss_setting", "ss_setting_dr1", "ss_setting_dr2" };
    static final String ACTION_CHANGED = "com.android.settings.splitscreen_settings.changed";
    static final long RE_ADD_WINDOW_MS = 10000;

    private final Context context;
    private final ContentResolver resolver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ContentObserver[] observers = new ContentObserver[KEYS.length];
    private final ReAddLimiter limiter = new ReAddLimiter(RE_ADD_WINDOW_MS);
    // one deferred re-add per key at most, for a removal the window refused
    private final Runnable[] pending = new Runnable[KEYS.length];
    // the current write failure of each key; null when its last write went through
    private final String[] errors = new String[KEYS.length];
    private boolean enabled;
    private Runnable onChange;

    PageListKeeper(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    /** Run (on the main thread) after the lists were changed or a write failed. */
    void setOnChange(Runnable r) {
        onChange = r;
    }

    /** The current write failures of the three lists, or null when there are none. */
    String lastError() {
        StringBuilder sb = null;
        for (String e : errors) {
            if (e == null) continue;
            if (sb == null) sb = new StringBuilder(); else sb.append("; ");
            sb.append(e);
        }
        return sb == null ? null : sb.toString();
    }

    /**
     * Puts our page into the three lists (enabled) or takes it out (disabled), writing only the
     * keys that change. The changed broadcast goes out once, and only if a write went through.
     */
    void setEnabled(boolean on) {
        enabled = on;
        String errorsBefore = lastError();
        if (on) {
            observe();
        } else {
            unobserve();
            cancelPending();
            limiter.clear();
        }
        boolean wrote = false;
        boolean attempted = false;
        for (int i = 0; i < KEYS.length; i++) {
            String now = Settings.System.getString(resolver, KEYS[i]);
            String wanted = on ? PageList.withPage(now) : PageList.withoutPage(now);
            errors[i] = null;
            if (wanted == null || wanted.equals(now)) continue;
            attempted = true;
            wrote |= write(i, now, wanted);
        }
        if (wrote) broadcastChanged();
        String after = lastError();
        if (attempted || (errorsBefore == null ? after != null : !errorsBefore.equals(after))) changed();
    }

    /** Stops watching the lists. The lists are left as they are. */
    void close() {
        enabled = false;
        unobserve();
        cancelPending();
    }

    private void cancelPending() {
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] != null) handler.removeCallbacks(pending[i]);
            pending[i] = null;
        }
    }

    private void observe() {
        for (int i = 0; i < KEYS.length; i++) {
            if (observers[i] != null) continue;
            final String key = KEYS[i];
            observers[i] = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    try {
                        reAdd(key);
                    } catch (Throwable t) {
                        Log.w(TAG, "page list check failed for " + key, t);
                    }
                }
            };
            resolver.registerContentObserver(Settings.System.getUriFor(key), false, observers[i]);
        }
    }

    private void unobserve() {
        for (int i = 0; i < observers.length; i++) {
            if (observers[i] == null) continue;
            try {
                resolver.unregisterContentObserver(observers[i]);
            } catch (Throwable t) {
                Log.w(TAG, "unregistering the page list observer failed", t);
            }
            observers[i] = null;
        }
    }

    /**
     * A list changed: put our page back if it is gone. At most one re-add per key per window; a
     * removal the window refuses gets exactly one deferred re-add for the time that is left.
     */
    private void reAdd(String key) {
        if (!enabled) return;
        final int i = indexOf(key);
        String now = Settings.System.getString(resolver, key);
        String wanted = PageList.withPage(now);
        if (wanted == null || wanted.equals(now)) return;
        long wait = limiter.acquire(key, SystemClock.elapsedRealtime());
        if (wait > 0) {
            if (pending[i] != null) return;
            Log.w(TAG, key + " lost the climate page again within 10 s; putting it back in " + wait + " ms");
            pending[i] = new Runnable() {
                @Override public void run() {
                    pending[i] = null;
                    try {
                        reAdd(KEYS[i]);
                    } catch (Throwable t) {
                        Log.w(TAG, "deferred page list check failed for " + KEYS[i], t);
                    }
                }
            };
            handler.postDelayed(pending[i], wait);
            return;
        }
        errors[i] = null;
        boolean wrote = write(i, now, wanted);
        if (wrote) broadcastChanged();
        changed();
    }

    private static int indexOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return i;
        }
        throw new IllegalArgumentException(key);
    }

    /** Writes the new value of KEYS[i]; a failure is logged and kept as that key's error. */
    private boolean write(int i, String now, String wanted) {
        String key = KEYS[i];
        try {
            if (Settings.System.putString(resolver, key, wanted)) {
                Log.i(TAG, "page list " + key + ": " + now + " -> " + wanted);
                return true;
            }
            errors[i] = "could not write " + key + ": the write was refused";
        } catch (RuntimeException e) {
            Log.w(TAG, "writing the page list " + key + " failed", e);
            errors[i] = "could not write " + key + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return false;
    }

    private void broadcastChanged() {
        try {
            context.sendBroadcast(new Intent(ACTION_CHANGED));
        } catch (Throwable t) {
            Log.w(TAG, "page list broadcast failed", t);
        }
    }

    private void changed() {
        Runnable r = onChange;
        if (r == null) return;
        try {
            r.run();
        } catch (Throwable t) {
            Log.w(TAG, "page list change callback failed", t);
        }
    }
}
