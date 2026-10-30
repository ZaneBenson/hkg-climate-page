package hkgclimate.page;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.mobis.splitscreen.right.k;

/**
 * The one entry point the panel's registration code calls (from a smali hook, on the panel's
 * worker thread). A Throwable must never reach the panel: it would kill the right-hand screen.
 */
public final class ClimatePageFactory {
    private ClimatePageFactory() { }

    private static final Object LOCK = new Object();
    // guarded by LOCK
    private static ClimatePage current;

    /** The new page, or null if it could not be made. Lets go of the previous page first. */
    public static k create(Context context, Handler handler) {
        try {
            synchronized (LOCK) {
                if (current != null) {
                    try {
                        current.release();
                    } catch (Throwable t) {
                        Log.e(ClimatePage.TAG, "releasing the previous page failed", t);
                    }
                    current = null;
                }
                ClimatePage page = new ClimatePage(context, handler);
                current = page;
                return page;
            }
        } catch (Throwable t) {
            Log.e(ClimatePage.TAG, "creating the climate page failed", t);
            return null;
        }
    }
}
