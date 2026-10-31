package hkgclimate.app;

import hkgclimate.core.CabinCalls;

/**
 * The stock calls behind CheckedCabinSender: the climate command goes to DatcClimateCalls, the
 * steering-wheel and eco commands to UsmSeatCalls (the seat service's one manager). It only
 * forwards, one call each, and catches nothing: CheckedCabinSender, the only caller, does.
 */
final class StockCabinCalls implements CabinCalls {
    private final DatcClimateCalls datc;
    private final UsmSeatCalls usm;

    StockCabinCalls(DatcClimateCalls datc, UsmSeatCalls usm) {
        this.datc = datc;
        this.usm = usm;
    }

    @Override
    public void climate(int flag, int value) {
        datc.send(flag, value);
    }

    @Override
    public void wheel(int value) {
        usm.wheel(value);
    }

    @Override
    public void eco(int value) {
        usm.eco(value);
    }
}
