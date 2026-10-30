// Compile-only declaration of the names of a stock class that this project calls. Written for
// this project, not copied from the stock code. It is on the javac class path only and is never
// packaged; on the unit the real class is used.
package com.hkmc;

import android.content.Context;

public class VehicleInfoManager {
    // a method call, so javac emits a field read instead of inlining a placeholder string
    public static final String DATC_SERVICE = stub();

    private VehicleInfoManager() { }

    private static String stub() { throw new RuntimeException("stub"); }

    public static VehicleInfoManager getInstance(Context context) { throw new RuntimeException("stub"); }

    public Object getService(String name) { throw new RuntimeException("stub"); }
}
