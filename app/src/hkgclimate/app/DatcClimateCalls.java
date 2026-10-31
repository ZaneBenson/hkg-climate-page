package hkgclimate.app;

import android.content.Context;
import com.hkmc.VehicleInfoManager;
import com.hkmc.datc.DATCManager;

/**
 * The only code that sends to the stock climate service: setFrontClimateControl, one call per send,
 * for StockCabinCalls only (CheckedCabinSender checks the flag and value first). The manager is
 * looked up through VehicleInfoManager on every send, never made here (a DATCManager built with
 * its constructor has no service in a fresh process). It catches nothing: CheckedCabinSender does.
 * (The stock setter catches and logs its own binder errors, so most failures show up as a send the
 * car does not follow.) Main thread only.
 */
final class DatcClimateCalls {
    private final Context context;

    DatcClimateCalls(Context context) {
        this.context = context;
    }

    /** One setFrontClimateControl(flag, value); throws when the service isn't there yet. */
    void send(int flag, int value) {
        Object service = VehicleInfoManager.getInstance(context).getService(VehicleInfoManager.DATC_SERVICE);
        if (service == null) throw new IllegalStateException("no DATC service yet");
        ((DATCManager) service).setFrontClimateControl(flag, value);
    }
}
