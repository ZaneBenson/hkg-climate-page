package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CheckedSeatSenderTest {
    static class FakeCalls implements UsmCalls {
        final List<String> calls = new ArrayList<String>();
        Throwable toThrow;

        public void driver(int value) throws Exception {
            calls.add("driver " + value);
            throwIfSet();
        }

        public void passenger(int value) throws Exception {
            calls.add("passenger " + value);
            throwIfSet();
        }

        private void throwIfSet() throws Exception {
            if (toThrow instanceof Exception) throw (Exception) toThrow;
            if (toThrow instanceof Error) throw (Error) toThrow;
        }
    }

    static class ListLog implements SendLog {
        final List<String> lines = new ArrayList<String>();
        public void line(String text) { lines.add(text); }
    }

    private final FakeCalls calls = new FakeCalls();
    private final ListLog log = new ListLog();
    private final SendWatch watch = new SendWatch();
    private long time = 1000;
    private final CheckedSeatSender sender =
            new CheckedSeatSender(calls, log, watch, new Clock() {
                public long now() { return time; }
            });

    @Test
    public void unknownValuesAreRefusedWithNoCall() {
        int[] bad = {0, 1, 9, -1, 100, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int v : bad) {
            sender.set(Seat.DRIVER, v);
            sender.set(Seat.PASSENGER, v);
        }
        assertTrue(calls.calls.isEmpty());
        assertEquals(bad.length * 2, log.lines.size());
        assertEquals("send refused setFrontSeatHeatDrvStep 0", log.lines.get(0));
        assertEquals("send refused setFrontSeatHeatAstStep 0", log.lines.get(1));
        assertEquals("send refused setFrontSeatHeatDrvStep 9", log.lines.get(4));
        assertEquals("send refused setFrontSeatHeatAstStep -1", log.lines.get(7));
        assertEquals(log.lines.get(log.lines.size() - 1), sender.lastResult());
        // a refused value is never watched
        assertNull(watch.overdue(1000000));
    }

    @Test
    public void knownValuesMakeExactlyOneCallToTheRightSeat() {
        for (int v = 2; v <= 8; v++) {
            calls.calls.clear();
            log.lines.clear();
            sender.set(Seat.DRIVER, v);
            assertEquals("driver " + v, calls.calls.get(0));
            assertEquals(1, calls.calls.size());
            assertEquals(1, log.lines.size());
            assertEquals("sent setFrontSeatHeatDrvStep " + v, log.lines.get(0));

            calls.calls.clear();
            log.lines.clear();
            sender.set(Seat.PASSENGER, v);
            assertEquals("passenger " + v, calls.calls.get(0));
            assertEquals(1, calls.calls.size());
            assertEquals("sent setFrontSeatHeatAstStep " + v, log.lines.get(0));
            assertEquals("sent setFrontSeatHeatAstStep " + v, sender.lastResult());
        }
    }

    @Test
    public void lastResultIsNullBeforeAnySend() {
        assertNull(sender.lastResult());
    }

    @Test
    public void aSendIsHandedToTheWatchWithTheClockTime() {
        sender.set(Seat.DRIVER, 8);
        assertNull(watch.overdue(1000 + SendWatch.WAIT_MS - 1));
        assertEquals("no change after 3 s: setFrontSeatHeatDrvStep 8",
                watch.overdue(1000 + SendWatch.WAIT_MS));
    }

    @Test
    public void aFailureIsLoggedAndRecordedAndDoesNotEscape() {
        calls.toThrow = new SecurityException("denied");
        sender.set(Seat.DRIVER, 8);
        String expected =
                "send failed setFrontSeatHeatDrvStep 8: java.lang.SecurityException: denied";
        assertEquals(1, calls.calls.size());
        assertEquals(expected, log.lines.get(0));
        assertEquals(1, log.lines.size());
        assertEquals(expected, sender.lastResult());
        // a failed send is not watched
        assertNull(watch.overdue(1000000));

        sender.set(Seat.DRIVER, 8);
        assertEquals(2, calls.calls.size());
    }

    @Test
    public void anErrorFromTheCallIsAlsoCaught() {
        calls.toThrow = new NoSuchMethodError("setFrontSeatHeatAstStep");
        sender.set(Seat.PASSENGER, 3);
        assertEquals("send failed setFrontSeatHeatAstStep 3: java.lang.NoSuchMethodError: "
                + "setFrontSeatHeatAstStep", sender.lastResult());
    }
}
