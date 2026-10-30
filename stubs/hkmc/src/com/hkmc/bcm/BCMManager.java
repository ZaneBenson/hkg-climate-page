// Compile-only declaration of the names of a stock class that this project calls. Written for
// this project, not copied from the stock code. It is on the javac class path only and is never
// packaged; on the unit the real class is used.
package com.hkmc.bcm;

import android.content.Context;

public final class BCMManager {
    public BCMManager(Context context) { throw new RuntimeException("stub"); }

    public int getDrvSeatHeatState() { throw new RuntimeException("stub"); }

    public int getAstSeatHeatState() { throw new RuntimeException("stub"); }

    public int getDrvSeatHeatOption() { throw new RuntimeException("stub"); }

    public int getAstSeatHeatOption() { throw new RuntimeException("stub"); }

    public void addVehicleStatusListener(BCMVehicleStatusListener listener) { throw new RuntimeException("stub"); }

    public void removeVehicleStatusListener(BCMVehicleStatusListener listener) { throw new RuntimeException("stub"); }
}
