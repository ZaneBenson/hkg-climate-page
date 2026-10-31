package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ClimateCoreTest {
    private static final OutsideTemp F48 = OutsideTemp.of(9f, 0xffff, 48f, 0xffff);
    private static final OutsideTemp F65 = OutsideTemp.of(18.3f, 0xffff, 65f, 0xffff);
    private static final OutsideTemp F70 = OutsideTemp.of(21f, 0xffff, 70f, 0xffff);
    private static final int D = Seat.DRIVER.ordinal();
    private static final int P = Seat.PASSENGER.ordinal();

    private static final class Recorder implements SeatCommander {
        final List<String> sent = new ArrayList<String>();
        public void set(Seat seat, int value) { sent.add(seat + "=" + value); }
    }

    private final Recorder rec = new Recorder();
    /** The time the core's clock reports. */
    private long now;
    private final Clock clock = new Clock() {
        public long now() { return now; }
    };
    private final ClimateCore core = new ClimateCore(rec, 60, null, clock);

    private void ready(int driver, int passenger, OutsideTemp outside) {
        core.onSeatsReady(true);
        core.onSeatOption(Seat.DRIVER, 6);
        core.onSeatOption(Seat.PASSENGER, 6);
        core.onSeatState(Seat.DRIVER, driver);
        core.onSeatState(Seat.PASSENGER, passenger);
        core.onOutside(outside);
    }

    @Test
    public void coldOutsideTurnsDriverOnToHeatHigh() {
        ready(2, 2, F48);
        assertTrue(core.onTap(Seat.DRIVER, 1));
        assertEquals(1, rec.sent.size());
        assertEquals("DRIVER=8", rec.sent.get(0));
    }

    @Test
    public void warmOutsideTurnsPassengerOnToCoolHigh() {
        ready(2, 2, F70);
        assertTrue(core.onTap(Seat.PASSENGER, 2));
        assertEquals("PASSENGER=5", rec.sent.get(0));
    }

    @Test
    public void sameRequestIdTwiceSendsOneCommand() {
        ready(2, 2, F48);
        assertTrue(core.onTap(Seat.DRIVER, 7));
        assertFalse(core.onTap(Seat.DRIVER, 7));
        assertEquals(1, rec.sent.size());
    }

    @Test
    public void unknownSeatValueMakesTapDoNothingAndSeatUnavailable() {
        int[] bad = {0, 9, -1};
        for (int v : bad) {
            Recorder r = new Recorder();
            ClimateCore c = new ClimateCore(r, 60, null, clock);
            c.onSeatsReady(true);
            c.onSeatOption(Seat.DRIVER, 6);
            c.onSeatState(Seat.DRIVER, v);
            c.onOutside(F48);
            assertFalse("value " + v, c.onTap(Seat.DRIVER, 1));
            assertTrue(r.sent.isEmpty());
            PageState s = c.pageState();
            assertEquals("Driver · not available", s.seatText[D]);
            assertFalse(s.seatEnabled[D]);
            assertEquals(0, s.seatLevel[D]);
            assertEquals(0, s.seatKind[D]);
        }
    }

    @Test
    public void notReadyTapDoesNothingAndBothSeatsAreDisabled() {
        ready(2, 2, F48);
        core.onSeatsReady(false);
        assertFalse(core.onTap(Seat.DRIVER, 1));
        assertFalse(core.onTap(Seat.PASSENGER, 2));
        assertTrue(rec.sent.isEmpty());
        PageState s = core.pageState();
        assertFalse(s.seatEnabled[D]);
        assertFalse(s.seatEnabled[P]);
        assertEquals("Driver · not available", s.seatText[D]);
        assertEquals("Passenger · not available", s.seatText[P]);
        assertEquals(0, s.seatLevel[D]);
        assertEquals(0, s.seatKind[P]);
    }

    @Test
    public void neverReadyIsNotReady() {
        core.onSeatState(Seat.DRIVER, 2);
        assertFalse(core.onTap(Seat.DRIVER, 1));
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void stateUpdatesAloneNeverSendACommand() {
        core.onSeatsReady(true);
        core.onSeatOption(Seat.DRIVER, 6);
        core.onSeatOption(Seat.PASSENGER, 6);
        for (int v = 0; v < 10; v++) {
            core.onSeatState(v % 2 == 0 ? Seat.DRIVER : Seat.PASSENGER, v);
        }
        core.onOutside(F70);
        core.onDisplayUnit(1);
        core.setSwitchTempF(50);
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void everyTapComputesFromTheCurrentState() {
        ready(2, 2, F48);
        assertTrue(core.onTap(Seat.DRIVER, 1));
        // the car did not change yet, state stays 2
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=8, DRIVER=8]", rec.sent.toString());
        core.onSeatState(Seat.DRIVER, 8);
        assertTrue(core.onTap(Seat.DRIVER, 3));
        assertEquals("DRIVER=7", rec.sent.get(2));
    }

    @Test
    public void seatOptionOtherThanSixMeansHeatOnly() {
        ready(2, 2, F70);
        core.onSeatOption(Seat.PASSENGER, 3);
        assertTrue(core.onTap(Seat.PASSENGER, 1));
        assertEquals("PASSENGER=8", rec.sent.get(0));
    }

    @Test
    public void switchTempChangeMovesTheChoice() {
        ready(2, 2, F65);
        core.setSwitchTempF(70);
        assertTrue(core.onTap(Seat.DRIVER, 1));
        assertEquals("DRIVER=8", rec.sent.get(0));
        core.setSwitchTempF(60);
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("DRIVER=5", rec.sent.get(1));
    }

    @Test
    public void unknownOutsideTapsHeat() {
        ready(2, 2, OutsideTemp.UNKNOWN);
        assertTrue(core.onTap(Seat.DRIVER, 1));
        assertEquals("DRIVER=8", rec.sent.get(0));
    }

    @Test
    public void topLineTexts() {
        ClimateCore c = new ClimateCore(rec, 60, null, clock);
        // nothing received yet
        assertEquals("Outside temperature unknown", c.pageState().top);

        ready(2, 2, F65);
        assertEquals("Outside 65 \u00b0F", core.pageState().top);
        core.onOutside(F48);
        assertEquals("Outside 48 \u00b0F", core.pageState().top);
        core.onOutside(OutsideTemp.of(0f, 0, 0f, 0));
        assertEquals("Outside temperature unknown", core.pageState().top);
    }

    @Test
    public void topLineFollowsDisplayUnit() {
        ready(2, 2, F65);
        core.onDisplayUnit(1);
        assertEquals("Outside 18 \u00b0C", core.pageState().top);
        core.onDisplayUnit(7);
        assertEquals("Outside 65 \u00b0F", core.pageState().top);
        core.onDisplayUnit(1);
        core.onOutside(OutsideTemp.of(0f, 0xff, 65f, 0xffff));
        assertEquals("Outside temperature unknown", core.pageState().top);
    }

    private int tapKind(Seat s) { return core.pageState().seatTapKind[s.ordinal()]; }

    @Test
    public void onCoolSeatTapKindCoolsWhateverTheOutsideSays() {
        // Review Focus 1: the passenger at 5 is on (cool), whatever the outside says
        ready(2, 5, F48);
        assertEquals(2, tapKind(Seat.PASSENGER));
        assertEquals(1, tapKind(Seat.DRIVER));
    }

    @Test
    public void offSeatWithUnknownOutsideTapKindHeats() {
        // Review Focus 2
        ready(2, 2, OutsideTemp.UNKNOWN);
        assertEquals(1, tapKind(Seat.DRIVER));
        assertEquals(1, tapKind(Seat.PASSENGER));
    }

    @Test
    public void offSeatTapKindFollowsTheOutsideTemperatureAndOption() {
        ready(2, 2, F70);
        assertEquals(2, tapKind(Seat.DRIVER));
        assertEquals(2, tapKind(Seat.PASSENGER));
        core.onOutside(F48);
        assertEquals(1, tapKind(Seat.DRIVER));
        assertEquals(1, tapKind(Seat.PASSENGER));
        core.onOutside(F70);
        core.onSeatOption(Seat.DRIVER, 0);
        assertEquals(1, tapKind(Seat.DRIVER));
        assertEquals(2, tapKind(Seat.PASSENGER));
    }

    @Test
    public void notReadyOrUnknownValueTapKindIsZero() {
        // Review Focus 3
        ready(2, 2, F48);
        core.onSeatsReady(false);
        assertEquals("[0, 0]", java.util.Arrays.toString(core.pageState().seatTapKind));
        core.onSeatsReady(true);
        core.onSeatState(Seat.DRIVER, 9);
        PageState s = core.pageState();
        assertEquals(0, s.seatTapKind[D]);
        assertFalse(s.seatEnabled[D]);
        assertEquals(1, s.seatTapKind[P]);
        core.onSeatState(Seat.DRIVER, -1);
        assertEquals(0, tapKind(Seat.DRIVER));
    }

    @Test
    public void tapKindAgreesWithTheModeOfTheNextTap() {
        OutsideTemp[] temps = {F48, F70};
        // option 6 is heat and cool (all states); option 0 is heat only (off or heat states)
        int[] options = {6, 0};
        for (int option : options) {
            for (Seat seat : Seat.values()) {
                for (OutsideTemp t : temps) {
                    for (int v = 2; v <= 8; v++) {
                        if (option == 0 && v > 2 && v < 6) {
                            continue;
                        }
                        Recorder r = new Recorder();
                        ClimateCore c = new ClimateCore(r, 60, null, clock);
                        c.onSeatsReady(true);
                        c.onSeatOption(Seat.DRIVER, option);
                        c.onSeatOption(Seat.PASSENGER, option);
                        c.onSeatState(Seat.DRIVER, v);
                        c.onSeatState(Seat.PASSENGER, v);
                        c.onOutside(t);
                        int kind = c.pageState().seatTapKind[seat.ordinal()];
                        assertTrue(c.onTap(seat, 1));
                        String sent = r.sent.get(0);
                        int value = Integer.parseInt(sent.substring(sent.indexOf('=') + 1));
                        // a seat stepping down to off keeps the mode it is in
                        Mode m = SeatValues.modeOf(value == SeatValues.OFF ? v : value);
                        int sentKind = m == Mode.HEAT ? 1 : m == Mode.COOL ? 2 : 0;
                        String why = "option " + option + " " + seat + " v" + v + " " + sent;
                        assertEquals(why, kind, sentKind);
                    }
                }
            }
        }
    }

    @Test
    public void aTapCallsTheSenderOnceAndStateUpdatesNever() {
        ready(2, 2, F48);
        assertTrue(rec.sent.isEmpty());
        assertTrue(core.onTap(Seat.DRIVER, 4));
        assertFalse(core.onTap(Seat.DRIVER, 4));
        assertEquals(1, rec.sent.size());
    }

    @Test
    public void seatTextsLevelAndKind() {
        ready(7, 2, F65);
        PageState s = core.pageState();
        assertEquals("Driver · heat mid", s.seatText[D]);
        assertEquals(2, s.seatLevel[D]);
        assertEquals(1, s.seatKind[D]);
        assertTrue(s.seatEnabled[D]);
        assertEquals("Passenger · off", s.seatText[P]);
        assertEquals(0, s.seatLevel[P]);
        assertEquals(0, s.seatKind[P]);
        assertTrue(s.seatEnabled[P]);

        core.onSeatState(Seat.PASSENGER, 4);
        s = core.pageState();
        assertEquals("Passenger · cool mid", s.seatText[P]);
        assertEquals(2, s.seatLevel[P]);
        assertEquals(2, s.seatKind[P]);
    }

    @Test
    public void listenerRunsOncePerChangeThatAltersTheState() {
        final int[] calls = {0};
        core.setListener(new Runnable() { public void run() { calls[0]++; } });
        assertEquals(0, calls[0]);

        core.onSeatsReady(true);       // no known seat value yet: no visible change
        assertEquals(0, calls[0]);
        core.onSeatState(Seat.DRIVER, 8);
        assertEquals(1, calls[0]);
        core.onSeatState(Seat.DRIVER, 8); // same again
        assertEquals(1, calls[0]);
        core.onSeatState(Seat.DRIVER, 7);
        assertEquals(2, calls[0]);
        core.onOutside(F65);
        assertEquals(3, calls[0]);
        core.onOutside(F65);
        assertEquals(3, calls[0]);
        core.onSeatOption(Seat.PASSENGER, 3); // the passenger has no known value: no visible change
        assertEquals(3, calls[0]);
        core.onDisplayUnit(2);          // already the default
        assertEquals(3, calls[0]);
        core.setSwitchTempF(61);        // 65 still above: no change
        assertEquals(3, calls[0]);
        core.onSeatState(Seat.PASSENGER, 2);
        assertEquals(4, calls[0]);      // the passenger seat shows now (off, heat-only option)
        core.onSeatOption(Seat.PASSENGER, 6);
        assertEquals(5, calls[0]);      // its next tap will cool now
        core.onTap(Seat.DRIVER, 1);     // a tap alone changes nothing shown
        assertEquals(5, calls[0]);
    }

    @Test
    public void pageStateEqualityIsByContent() {
        ready(7, 2, F65);
        PageState a = core.pageState();
        PageState b = core.pageState();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        core.onSeatState(Seat.DRIVER, 6);
        assertFalse(a.equals(core.pageState()));
    }

    @Test
    public void celsiusUnitWithInvalidCelsiusReadingMeansUnknownAndHeat() {
        ready(2, 2, OutsideTemp.of(0f, 0xff, 75f, 0xffff));
        core.onDisplayUnit(1);
        assertEquals("Outside temperature unknown", core.pageState().top);
        assertTrue(core.onTap(Seat.DRIVER, 1));
        assertEquals("DRIVER=8", rec.sent.get(0));
        // in Fahrenheit the same reading is usable and warm
        core.onDisplayUnit(2);
        assertEquals("Outside 75 \u00b0F", core.pageState().top);
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("DRIVER=5", rec.sent.get(1));
    }

    @Test
    public void seatValueIsTheLastReportedStateOrMinusOne() {
        assertEquals(-1, core.seatValue(Seat.DRIVER));
        assertEquals(-1, core.seatValue(Seat.PASSENGER));
        core.onSeatState(Seat.DRIVER, 8);
        assertEquals(8, core.seatValue(Seat.DRIVER));
        assertEquals(-1, core.seatValue(Seat.PASSENGER));
        core.onSeatState(Seat.DRIVER, 9);
        assertEquals(9, core.seatValue(Seat.DRIVER));
    }


    // swap and ×

    private boolean swap(Seat s) { return core.pageState().seatSwap[s.ordinal()]; }

    private boolean off(Seat s) { return core.pageState().seatOff[s.ordinal()]; }

    @Test
    public void swapOnAHeatOnlySeatChangesNothingAndSendsNothing() {
        // Review Focus 5
        ready(2, 2, F48);
        core.onSeatOption(Seat.DRIVER, 0);
        PageState before = core.pageState();
        final int[] calls = {0};
        core.setListener(new Runnable() { public void run() { calls[0]++; } });
        assertFalse(core.onSeatSwap(Seat.DRIVER, 1));
        assertEquals(before, core.pageState());
        assertEquals(0, calls[0]);
        assertFalse(swap(Seat.DRIVER));
        assertTrue(rec.sent.isEmpty());
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=8]", rec.sent.toString());
    }

    @Test
    public void swapOnAnOnSeatChangesNothingAndSendsNothing() {
        // Review Focus 5
        ready(7, 4, F48);
        PageState before = core.pageState();
        assertFalse(core.onSeatSwap(Seat.DRIVER, 1));
        assertFalse(core.onSeatSwap(Seat.PASSENGER, 2));
        assertEquals(before, core.pageState());
        assertTrue(rec.sent.isEmpty());
        // the override did not stick: off again, the automatic choice (heat at 48) holds
        core.onSeatState(Seat.DRIVER, 2);
        assertEquals(1, tapKind(Seat.DRIVER));
    }

    @Test
    public void swapOnAnUnknownOrNotReadySeatChangesNothing() {
        ready(9, 2, F48);
        assertFalse(core.onSeatSwap(Seat.DRIVER, 1));
        core.onSeatsReady(false);
        assertFalse(core.onSeatSwap(Seat.PASSENGER, 2));
        core.onSeatsReady(true);
        assertEquals(1, tapKind(Seat.PASSENGER));
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void swapFlipsTheTapKindAndTheNextTapUsesIt() {
        ready(2, 2, F48);
        assertEquals(1, tapKind(Seat.DRIVER));
        final int[] calls = {0};
        core.setListener(new Runnable() { public void run() { calls[0]++; } });
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1));
        assertEquals(1, calls[0]);
        assertEquals(2, tapKind(Seat.DRIVER));
        assertEquals(1, tapKind(Seat.PASSENGER));
        assertTrue(rec.sent.isEmpty());
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=5]", rec.sent.toString());
        // swapping back returns to the automatic choice
        assertTrue(core.onSeatSwap(Seat.DRIVER, 3));
        assertEquals(1, tapKind(Seat.DRIVER));
        assertTrue(core.onTap(Seat.DRIVER, 4));
        assertEquals("DRIVER=8", rec.sent.get(1));
    }

    @Test
    public void swapOnAWarmDayTurnsTheNextTapToHeat() {
        ready(2, 2, F70);
        assertTrue(core.onSeatSwap(Seat.PASSENGER, 1));
        assertEquals(1, tapKind(Seat.PASSENGER));
        assertTrue(core.onTap(Seat.PASSENGER, 2));
        assertEquals("[PASSENGER=8]", rec.sent.toString());
    }

    @Test
    public void theSwapEndsWhenTheSeatTurnsOn() {
        ready(2, 2, F48);
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1));
        assertEquals(2, tapKind(Seat.DRIVER));
        core.onSeatState(Seat.DRIVER, 5);
        assertEquals(2, tapKind(Seat.DRIVER));
        core.onSeatState(Seat.DRIVER, 2);
        // off again: the automatic choice (heat at 48) is back
        assertEquals(1, tapKind(Seat.DRIVER));
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=8]", rec.sent.toString());
    }

    @Test
    public void aPendingSwapHoldsWhenTheSwitchTemperatureChanges() {
        ready(2, 2, F48);
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1));
        assertEquals(2, tapKind(Seat.DRIVER)); // cool, against the automatic heat at 48
        core.setSwitchTempF(40); // the automatic choice at 48 is now cool
        assertEquals(2, tapKind(Seat.PASSENGER)); // not swapped: cool is now its automatic choice
        assertEquals(2, tapKind(Seat.DRIVER)); // the swap chose cool and holds (relative, it would flip to heat)
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=5]", rec.sent.toString());
    }

    @Test
    public void aPendingSwapHoldsWhenTheOutsideReadingCrossesTheSwitchTemperature() {
        ready(2, 2, F48);
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1)); // cool
        core.onOutside(F70); // automatic is cool now
        assertEquals(2, tapKind(Seat.DRIVER));
        core.onOutside(F48);
        assertEquals(2, tapKind(Seat.DRIVER));
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=5]", rec.sent.toString());
    }

    @Test
    public void anUnknownSeatReadingDoesNotEndAPendingSwap() {
        ready(2, 2, F48);
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1));
        core.onSeatState(Seat.DRIVER, 9); // an unknown reading
        core.onSeatState(Seat.DRIVER, 2);
        assertEquals(2, tapKind(Seat.DRIVER));
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=5]", rec.sent.toString());
    }

    @Test
    public void aSwapIsIgnoredIfTheSeatLosesItsCoolOption() {
        ready(2, 2, F48);
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1));
        core.onSeatOption(Seat.DRIVER, 0);
        assertEquals(1, tapKind(Seat.DRIVER));
        assertFalse(swap(Seat.DRIVER));
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertEquals("[DRIVER=8]", rec.sent.toString());
    }

    @Test
    public void swapRequestIdsAreSharedWithTaps() {
        ready(2, 2, F48);
        assertTrue(core.onSeatSwap(Seat.DRIVER, 1));
        assertFalse(core.onSeatSwap(Seat.DRIVER, 1));
        assertEquals(2, tapKind(Seat.DRIVER));
        assertFalse(core.onTap(Seat.DRIVER, 1));
        assertTrue(core.onTap(Seat.DRIVER, 2));
        assertFalse(core.onSeatSwap(Seat.PASSENGER, 2));
        assertEquals(1, tapKind(Seat.PASSENGER));
        assertEquals("[DRIVER=5]", rec.sent.toString());
    }

    @Test
    public void offSendsExactlyOneTwoForAnOnSeat() {
        ready(7, 4, F48);
        assertTrue(core.onSeatOff(Seat.DRIVER, 1));
        assertEquals("[DRIVER=2]", rec.sent.toString());
        assertFalse(core.onSeatOff(Seat.DRIVER, 1));
        assertEquals(1, rec.sent.size());
        assertTrue(core.onSeatOff(Seat.PASSENGER, 2));
        assertEquals("[DRIVER=2, PASSENGER=2]", rec.sent.toString());
    }

    @Test
    public void offSendsNothingForAnOffUnknownOrNotReadySeat() {
        ready(2, 9, F48);
        assertFalse(core.onSeatOff(Seat.DRIVER, 1));
        assertFalse(core.onSeatOff(Seat.PASSENGER, 2));
        core.onSeatState(Seat.PASSENGER, 8);
        core.onSeatsReady(false);
        assertFalse(core.onSeatOff(Seat.PASSENGER, 3));
        assertTrue(rec.sent.isEmpty());
        core.onSeatsReady(true);
        assertTrue(core.onSeatOff(Seat.PASSENGER, 4));
        assertEquals("[PASSENGER=2]", rec.sent.toString());
    }

    @Test
    public void offRequestIdsAreSharedWithTaps() {
        ready(7, 2, F48);
        assertTrue(core.onTap(Seat.DRIVER, 1));
        assertFalse(core.onSeatOff(Seat.DRIVER, 1));
        assertEquals("[DRIVER=6]", rec.sent.toString());
    }

    @Test
    public void subButtonsShowSwapForAnOffCoolCapableSeatAndOffForAnOnSeat() {
        ready(2, 7, F48);
        assertTrue(swap(Seat.DRIVER));
        assertFalse(off(Seat.DRIVER));
        assertFalse(swap(Seat.PASSENGER));
        assertTrue(off(Seat.PASSENGER));
        core.onSeatOption(Seat.DRIVER, 0);
        assertFalse(swap(Seat.DRIVER));
        assertFalse(off(Seat.DRIVER));
        // a heat-only seat that is on still offers off
        core.onSeatOption(Seat.PASSENGER, 0);
        assertTrue(off(Seat.PASSENGER));
        core.onSeatState(Seat.DRIVER, 9);
        assertFalse(swap(Seat.DRIVER));
        assertFalse(off(Seat.DRIVER));
        core.onSeatsReady(false);
        assertFalse(off(Seat.PASSENGER));
        assertEquals(2, core.pageState().seatSwap.length);
        assertEquals(2, core.pageState().seatOff.length);
    }

    @Test
    public void pageStateEqualityCoversSwapOffAndHighlight() {
        ready(2, 2, F48);
        PageState a = core.pageState();
        core.onSeatSwap(Seat.DRIVER, 1);
        PageState b = core.pageState();
        assertFalse(a.equals(b));
        String[] noNames = new String[6];
        boolean[] noPresets = new boolean[6];
        PageState x = new PageState("t", new String[2], new int[2], new int[2], new int[2],
                new boolean[2], noNames, noPresets, -1, new boolean[2], new boolean[2]);
        PageState y = new PageState("t", new String[2], new int[2], new int[2], new int[2],
                new boolean[2], noNames, noPresets, -1, new boolean[2], new boolean[2]);
        assertEquals(x, y);
        assertEquals(x.hashCode(), y.hashCode());
        assertFalse(x.equals(new PageState("t", new String[2], new int[2], new int[2], new int[2],
                new boolean[2], noNames, noPresets, 0, new boolean[2], new boolean[2])));
        assertFalse(x.equals(new PageState("t", new String[2], new int[2], new int[2], new int[2],
                new boolean[2], noNames, noPresets, -1, new boolean[] {true, false}, new boolean[2])));
        assertFalse(x.equals(new PageState("t", new String[2], new int[2], new int[2], new int[2],
                new boolean[2], noNames, noPresets, -1, new boolean[2], new boolean[] {false, true})));
    }
}
