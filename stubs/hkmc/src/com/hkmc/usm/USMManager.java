// Compile-only declaration of the names of a stock class that this project calls. Written for
// this project, not copied from the stock code. It is on the javac class path only and is never
// packaged; on the unit the real class is used.
package com.hkmc.usm;

import android.content.Context;

public class USMManager {
    public USMManager(Context context) { throw new RuntimeException("stub"); }

    public void setFrontSeatHeatDrvStep(int step) { throw new RuntimeException("stub"); }

    public void setFrontSeatHeatAstStep(int step) { throw new RuntimeException("stub"); }
}
