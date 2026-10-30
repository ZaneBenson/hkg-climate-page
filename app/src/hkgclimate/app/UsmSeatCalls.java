package hkgclimate.app;

import android.content.Context;
import com.hkmc.usm.USMManager;
import hkgclimate.core.UsmCalls;

/**
 * The only code that touches the stock seat service: the two approved setters, one call each.
 * The manager is made on the first send, never earlier, and kept; if making it throws, nothing is
 * kept and the next send tries again. It catches nothing: CheckedSeatSender does.
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
        manager().setFrontSeatHeatDrvStep(value);
    }

    @Override
    public void passenger(int value) {
        manager().setFrontSeatHeatAstStep(value);
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
