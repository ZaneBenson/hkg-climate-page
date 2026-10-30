package hkgclimate.app;

import android.content.Context;
import com.hkmc.usm.USMManager;
import hkgclimate.core.UsmCalls;

/**
 * The only code that touches the stock seat service: the two approved setters, one call each.
 * The manager is made on the first send, never earlier, and kept; if making it throws, or a setter
 * throws, it is dropped and the next tap makes a new one (still one call per tap, never a retry).
 * It catches nothing: CheckedSeatSender does. (The stock setters catch and log their own binder
 * errors, so most failures show up as a send the seat does not follow, not as an exception.)
 * Main thread only (taps are handled there).
 */
final class UsmSeatCalls implements UsmCalls {
    private final Context context;
    private USMManager usm;

    UsmSeatCalls(Context context) {
        this.context = context;
    }

    @Override
    public void driver(int value) {
        USMManager m = manager();
        boolean ok = false;
        try {
            m.setFrontSeatHeatDrvStep(value);
            ok = true;
        } finally {
            if (!ok) usm = null;
        }
    }

    @Override
    public void passenger(int value) {
        USMManager m = manager();
        boolean ok = false;
        try {
            m.setFrontSeatHeatAstStep(value);
            ok = true;
        } finally {
            if (!ok) usm = null;
        }
    }

    private USMManager manager() {
        USMManager m = usm;
        if (m == null) {
            m = new USMManager(context);
            usm = m;
        }
        return m;
    }
}
