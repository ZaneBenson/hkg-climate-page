package hkgclimate.app;

import android.content.Context;
import com.hkmc.usm.USMManager;
import hkgclimate.core.UsmCalls;

/**
 * The only code that touches the stock seat service: the two approved seat setters, the
 * steering-wheel and eco setters (for StockCabinCalls only), one call each, and the steering-wheel
 * and eco getters (for CabinStateReader). The manager is made on the first call, never earlier, and
 * kept; if making it throws, or a call throws, it is dropped and the next call makes a new one
 * (still one call per request, never a retry). It catches nothing: CheckedSeatSender,
 * CheckedCabinSender and CabinStateReader do. (The stock setters catch and log their own binder
 * errors, so most failures show up as a send the car does not follow, not as an exception; the
 * stock getters return -1 then.) Main thread only (taps and preset runs are handled there).
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

    /** setSWRC_SteeringwheelWarmerSet(value): StockCabinCalls only (CheckedCabinSender's press). */
    void wheel(int value) {
        USMManager m = manager();
        boolean ok = false;
        try {
            m.setSWRC_SteeringwheelWarmerSet(value);
            ok = true;
        } finally {
            if (!ok) usm = null;
        }
    }

    /** setClimateControlECOMode(value): StockCabinCalls only (CheckedCabinSender checks the value). */
    void eco(int value) {
        USMManager m = manager();
        boolean ok = false;
        try {
            m.setClimateControlECOMode(value);
            ok = true;
        } finally {
            if (!ok) usm = null;
        }
    }

    /** getSWRC_SteeringwheelHeatIndStep2(): 1 on, 0 off, -1 unknown. */
    int wheelState() {
        USMManager m = manager();
        boolean ok = false;
        try {
            int v = m.getSWRC_SteeringwheelHeatIndStep2();
            ok = true;
            return v;
        } finally {
            if (!ok) usm = null;
        }
    }

    /** getClimateControlECOMode(): 1 on, 0 off, -1 unknown (not the values sent). */
    int ecoState() {
        USMManager m = manager();
        boolean ok = false;
        try {
            int v = m.getClimateControlECOMode();
            ok = true;
            return v;
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
