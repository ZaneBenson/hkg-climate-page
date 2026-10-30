// Compile-only declaration of the names of a stock class that this project calls. Written for
// this project, not copied from the stock code. It is on the javac class path only and is never
// packaged; on the unit the real class is used.
package com.hkmc.datc;

public final class DATCManager {
    // Never "new DATCManager(Context)": that constructor leaves the service null in a fresh
    // process. Get it from VehicleInfoManager.getService(DATC_SERVICE).
    private DATCManager() { }

    public void addTemperatureListener(DATCTemperatureListener listener) { throw new RuntimeException("stub"); }

    public void removeTemperatureListener(DATCTemperatureListener listener) { throw new RuntimeException("stub"); }

    public int getTemperatureUnit() { throw new RuntimeException("stub"); }
}
