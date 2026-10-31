package hkgclimate.core;

/**
 * The stock climate, steering-wheel and eco calls. The Android side makes them; core only decides
 * when, and only CheckedCabinSender calls them.
 */
public interface CabinCalls {
    /** DATCManager.setFrontClimateControl(flag, value). */
    void climate(int flag, int value) throws Exception;

    /** USMManager.setSWRC_SteeringwheelWarmerSet(value). */
    void wheel(int value) throws Exception;

    /** USMManager.setClimateControlECOMode(value). */
    void eco(int value) throws Exception;
}
