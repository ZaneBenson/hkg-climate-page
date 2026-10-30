package hkgclimate.app;

import android.util.Log;
import hkgclimate.core.Seat;
import hkgclimate.core.SeatCommander;

/** The test build's commander: it writes one log line and does nothing else. */
final class TestModeCommander implements SeatCommander {
    @Override
    public void set(Seat seat, int value) {
        Log.i(CarLink.TAG, "would set "
                + (seat == Seat.DRIVER ? "setFrontSeatHeatDrvStep " : "setFrontSeatHeatAstStep ") + value);
    }
}
