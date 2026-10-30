package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SendWatchTest {
    private static final String DRV8 = "no change after 3 s: setFrontSeatHeatDrvStep 8";
    private static final String AST4 = "no change after 3 s: setFrontSeatHeatAstStep 4";

    @Test
    public void waitIsThreeSeconds() {
        assertEquals(3000, SendWatch.WAIT_MS);
    }

    @Test
    public void overdueOnlyAtThreeSecondsAndThenCleared() {
        SendWatch w = new SendWatch();
        w.sent(Seat.DRIVER, 8, 0);
        assertNull(w.overdue(2999));
        assertEquals(DRV8, w.overdue(3000));
        assertNull(w.overdue(3000));
        assertNull(w.overdue(99999));
    }

    @Test
    public void aMatchingStateClearsThePendingSend() {
        SendWatch w = new SendWatch();
        w.sent(Seat.DRIVER, 8, 0);
        w.onState(Seat.DRIVER, 8);
        assertNull(w.overdue(5000));
    }

    @Test
    public void aNonMatchingStateDoesNotClearThePendingSend() {
        SendWatch w = new SendWatch();
        w.sent(Seat.DRIVER, 8, 0);
        w.onState(Seat.DRIVER, 2);
        assertEquals(DRV8, w.overdue(3000));
    }

    @Test
    public void seatsAreIndependent() {
        SendWatch w = new SendWatch();
        w.sent(Seat.DRIVER, 8, 0);
        w.sent(Seat.PASSENGER, 4, 0);
        w.onState(Seat.PASSENGER, 8);   // wrong seat for the match
        w.onState(Seat.DRIVER, 4);      // wrong seat for the match
        w.onState(Seat.PASSENGER, 4);   // clears passenger only
        assertEquals(DRV8, w.overdue(3000));
        assertNull(w.overdue(3000));
    }

    @Test
    public void aNewSendForTheSameSeatReplacesTheEarlierOne() {
        SendWatch w = new SendWatch();
        w.sent(Seat.DRIVER, 8, 0);
        w.sent(Seat.DRIVER, 7, 2000);
        assertNull(w.overdue(3000));   // the 8 is gone; the 7 is only 1 s old
        assertEquals("no change after 3 s: setFrontSeatHeatDrvStep 7", w.overdue(5000));
        // the state for the replaced value no longer matters
        w.sent(Seat.DRIVER, 7, 6000);
        w.onState(Seat.DRIVER, 8);
        assertEquals("no change after 3 s: setFrontSeatHeatDrvStep 7", w.overdue(9000));
    }

    @Test
    public void severalOverdueSendsComeOutOldestFirstOnePerCall() {
        SendWatch w = new SendWatch();
        w.sent(Seat.PASSENGER, 4, 100);
        w.sent(Seat.DRIVER, 8, 500);
        assertEquals(AST4, w.overdue(10000));
        assertEquals(DRV8, w.overdue(10000));
        assertNull(w.overdue(10000));

        // oldest-first holds the other way round too
        w.sent(Seat.DRIVER, 8, 100);
        w.sent(Seat.PASSENGER, 4, 500);
        assertEquals(DRV8, w.overdue(10000));
        assertEquals(AST4, w.overdue(10000));
    }

    @Test
    public void onlyTheOverdueOneIsReturnedWhenTheOtherIsStillYoung() {
        SendWatch w = new SendWatch();
        w.sent(Seat.DRIVER, 8, 0);
        w.sent(Seat.PASSENGER, 4, 2000);
        assertEquals(DRV8, w.overdue(3500));
        assertNull(w.overdue(3500));
        assertEquals(AST4, w.overdue(5000));
    }
}
