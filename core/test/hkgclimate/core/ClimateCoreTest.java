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
    private final ClimateCore core = new ClimateCore(rec, 60);

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
            ClimateCore c = new ClimateCore(r, 60);
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
        // test mode: the car did not change, state stays 2
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
        ClimateCore c = new ClimateCore(rec, 60);
        // nothing received yet
        assertEquals("Outside temperature unknown · taps will heat", c.pageState().top);

        ready(2, 2, F65);
        assertEquals("Outside 65 °F · taps will cool", core.pageState().top);
        core.onOutside(F48);
        assertEquals("Outside 48 °F · taps will heat", core.pageState().top);
        core.onOutside(OutsideTemp.of(0f, 0, 0f, 0));
        assertEquals("Outside temperature unknown · taps will heat", core.pageState().top);
    }

    @Test
    public void topLineFollowsDisplayUnit() {
        ready(2, 2, F65);
        core.onDisplayUnit(1);
        assertEquals("Outside 18 °C · taps will cool", core.pageState().top);
        core.onDisplayUnit(7);
        assertEquals("Outside 65 °F · taps will cool", core.pageState().top);
    }

    @Test
    public void topLineUsesTheDriversOption() {
        ready(2, 2, F70);
        core.onSeatOption(Seat.DRIVER, 1);
        assertEquals("Outside 70 °F · taps will heat", core.pageState().top);
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
        assertTrue(s.testMode);

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
        core.onSeatOption(Seat.PASSENGER, 3); // top line follows the driver's option only
        assertEquals(3, calls[0]);
        core.onDisplayUnit(2);          // already the default
        assertEquals(3, calls[0]);
        core.setSwitchTempF(61);        // 65 still above: no change
        assertEquals(3, calls[0]);
        core.onSeatOption(Seat.DRIVER, 6);
        assertEquals(4, calls[0]);      // taps will cool now
        core.onTap(Seat.DRIVER, 1);     // a tap changes nothing in test mode
        assertEquals(4, calls[0]);
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
}
