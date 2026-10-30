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
        /** When set, the car's seat state reports this value while the call is still running. */
        SendWatch stateDuringCall;

        public void driver(int value) throws Exception {
            calls.add("driver " + value);
            if (stateDuringCall != null) stateDuringCall.onState(Seat.DRIVER, value);
            throwIfSet();
        }

        public void passenger(int value) throws Exception {
            calls.add("passenger " + value);
            if (stateDuringCall != null) stateDuringCall.onState(Seat.PASSENGER, value);
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
    public void aStateMatchingTheValueDuringTheCallClearsTheSend() {
        // a fast seat callback can arrive before set() returns: the send must already be recorded
        calls.stateDuringCall = watch;
        sender.set(Seat.DRIVER, 8);
        sender.set(Seat.PASSENGER, 3);
        assertEquals(2, calls.calls.size());
        assertEquals("sent setFrontSeatHeatAstStep 3", sender.lastResult());
        assertNull(watch.overdue(1000000));
    }

    @Test
    public void aFailedSendLeavesNothingPending() {
        sender.set(Seat.DRIVER, 8);          // pending, then replaced by the failing send
        calls.toThrow = new SecurityException("denied");
        sender.set(Seat.DRIVER, 7);
        sender.set(Seat.PASSENGER, 3);
        assertEquals(3, calls.calls.size());
        assertNull(watch.overdue(1000000));
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

    @Test
    public void aNullSeatIsRefusedWithNoCall() {
        sender.set(null, 3);
        assertTrue(calls.calls.isEmpty());
        assertEquals(1, log.lines.size());
        assertEquals("send refused <unknown seat> 3", log.lines.get(0));
        assertEquals("send refused <unknown seat> 3", sender.lastResult());
        assertNull(watch.overdue(1000000));
    }

    static class ThrowingLog implements SendLog {
        int lines;
        public void line(String text) {
            lines++;
            throw new IllegalStateException("log broken");
        }
    }

    @Test
    public void aThrowingLogDoesNotEscapeOrRepeatTheCall() {
        ThrowingLog broken = new ThrowingLog();
        CheckedSeatSender s = new CheckedSeatSender(calls, broken, watch, new Clock() {
            public long now() { return time; }
        });
        s.set(Seat.DRIVER, 8);
        assertEquals(1, calls.calls.size());
        assertEquals(1, broken.lines);
        assertEquals("sent setFrontSeatHeatDrvStep 8", s.lastResult());
        // still watched: the broken log did not cut the send short
        assertEquals("no change after 3 s: setFrontSeatHeatDrvStep 8",
                watch.overdue(1000 + SendWatch.WAIT_MS));

        // the refused and failed paths too
        s.set(Seat.DRIVER, 9);
        assertEquals(1, calls.calls.size());
        assertEquals("send refused setFrontSeatHeatDrvStep 9", s.lastResult());
        s.set(null, 8);
        assertEquals(1, calls.calls.size());
        calls.toThrow = new SecurityException("denied");
        s.set(Seat.PASSENGER, 3);
        assertEquals(2, calls.calls.size());
        assertEquals("send failed setFrontSeatHeatAstStep 3: java.lang.SecurityException: denied",
                s.lastResult());
    }

    @Test
    public void aThrowingClockDoesNotEscapeOrRepeatTheCall() {
        CheckedSeatSender s = new CheckedSeatSender(calls, log, watch, new Clock() {
            public long now() { throw new IllegalStateException("clock broken"); }
        });
        s.set(Seat.PASSENGER, 5);
        assertEquals(1, calls.calls.size());
        assertEquals("sent setFrontSeatHeatAstStep 5", s.lastResult());
        assertEquals("sent setFrontSeatHeatAstStep 5", log.lines.get(0));
    }

    @Test
    public void aThrowableWhoseTextThrowsDoesNotEscapeOrRepeatTheCall() {
        calls.toThrow = new RuntimeException() {
            @Override public String toString() { throw new IllegalStateException("no text"); }
        };
        sender.set(Seat.DRIVER, 4);
        assertEquals(1, calls.calls.size());
        assertTrue(sender.lastResult(), sender.lastResult()
                .startsWith("send failed setFrontSeatHeatDrvStep 4: hkgclimate.core.CheckedSeatSenderTest$"));
    }
}
