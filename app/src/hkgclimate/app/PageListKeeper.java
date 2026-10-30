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
import java.util.HashMap;
import java.util.Map;

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
    // key -> elapsedRealtime of its last re-add by the observer
    private final Map<String, Long> lastReAdd = new HashMap<String, Long>();
    private boolean enabled;
    private String lastError;
    private Runnable onChange;

    PageListKeeper(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    /** Run (on the main thread) after the lists were changed or a write failed. */
    void setOnChange(Runnable r) {
        onChange = r;
    }

    /** The last write failure, or null when the last batch of writes went through. */
    String lastError() {
        return lastError;
    }

    /**
     * Puts our page into the three lists (enabled) or takes it out (disabled), writing only the
     * keys that change, then sends the changed broadcast once.
     */
    void setEnabled(boolean on) {
        enabled = on;
        lastError = null;
        if (on) {
            observe();
        } else {
            unobserve();
            lastReAdd.clear();
        }
        for (String key : KEYS) {
            String now = Settings.System.getString(resolver, key);
            write(key, now, on ? PageList.withPage(now) : PageList.withoutPage(now));
        }
        broadcastChanged();
        changed();
    }

    /** Stops watching the lists. The lists are left as they are. */
    void close() {
        enabled = false;
        unobserve();
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

    /** A list changed: put our page back if it is gone, at most once per key per window. */
    private void reAdd(String key) {
        if (!enabled) return;
        String now = Settings.System.getString(resolver, key);
        String wanted = PageList.withPage(now);
        if (wanted == null || wanted.equals(now)) return;
        long t = SystemClock.elapsedRealtime();
        Long last = lastReAdd.get(key);
        if (last != null && t - last < RE_ADD_WINDOW_MS) {
            Log.w(TAG, key + " lost the climate page again within 10 s; not putting it back yet");
            return;
        }
        lastReAdd.put(key, t);
        lastError = null;
        write(key, now, wanted);
        broadcastChanged();
        changed();
    }

    /** Writes the key when the value differs; a failure is logged and kept in lastError. */
    private void write(String key, String now, String wanted) {
        if (wanted == null || wanted.equals(now)) return;
        try {
            if (Settings.System.putString(resolver, key, wanted)) {
                Log.i(TAG, "page list " + key + ": " + now + " -> " + wanted);
            } else {
                fail(key, "the write was refused");
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "writing the page list " + key + " failed", e);
            fail(key, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void fail(String key, String why) {
        String msg = "could not write " + key + ": " + why;
        lastError = lastError == null ? msg : lastError + "; " + msg;
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
