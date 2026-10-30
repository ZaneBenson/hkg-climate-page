// Compile-only declaration of the names of a stock class that this project calls. Written for
// this project, not copied from the stock code. It is on the javac class path only and is never
// packaged; on the unit the real class is used.
package com.hkmc.bcm;

public class BCMVehicleStatusListener {
    public void onDrvSeatHeaterStatusChanged(int state) { }

    public void onAstSeatHeaterStatusChanged(int state) { }
}
