package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class CheckedCabinSenderTest {
    static final class Calls implements CabinCalls {
        final List<String> calls = new ArrayList<String>();
        Throwable toThrow;
        boolean failWheelOne;
        boolean failWheelZero;

        public void climate(int flag, int value) throws Exception {
            calls.add("climate " + flag + " " + value);
            throwIfSet();
        }

        public void wheel(int value) throws Exception {
            calls.add("wheel " + value);
            if (failWheelOne && value == 1) throw new IllegalStateException("gone");
            if (failWheelZero && value == 0) throw new IllegalStateException("gone");
            throwIfSet();
        }

        public void eco(int value) throws Exception {
            calls.add("eco " + value);
            throwIfSet();
        }

        private void throwIfSet() throws Exception {
            if (toThrow instanceof Exception) throw (Exception) toThrow;
            if (toThrow instanceof Error) throw (Error) toThrow;
        }
    }

    private final Calls calls = new Calls();
    private final CheckedSeatSenderTest.ListLog log = new CheckedSeatSenderTest.ListLog();
    private final PresetRunnerTest.ManualScheduler sched = new PresetRunnerTest.ManualScheduler();
    private final CheckedCabinSender sender = new CheckedCabinSender(calls, log);

    private static final int[][] ALLOWED = {
        {50, 1}, {50, 2}, {49, 1}, {49, 2}, {4, 1}, {4, 2}, {51, 1}, {8, 1}, {9, 0}, {9, 1},
        {6, 1}, {6, 2}, {6, 3}, {6, 4}, {22, 1}, {22, 2},
    };

    @Test
    public void everyAllowedPairIsSent() {
        for (int[] p : ALLOWED) {
            assertTrue(p[0] + " " + p[1], sender.climate(p[0], p[1]));
        }
        assertEquals(ALLOWED.length, calls.calls.size());
        for (int i = 0; i < ALLOWED.length; i++) {
            assertEquals("climate " + ALLOWED[i][0] + " " + ALLOWED[i][1], calls.calls.get(i));
            assertEquals("sent setFrontClimateControl " + ALLOWED[i][0] + " " + ALLOWED[i][1],
                    log.lines.get(i));
        }
        assertTrue(sender.eco(2));
        assertTrue(sender.eco(1));
        assertEquals(Arrays.asList("eco 2", "eco 1"), calls.calls.subList(ALLOWED.length, ALLOWED.length + 2));
        assertEquals("sent setClimateControlECOMode 2", log.lines.get(ALLOWED.length));
    }

    @Test
    public void everyOtherPairIsRefusedWithNoCall() {
        int[] flags = {32, 24, 48, 5, 28, 29, 30, 31, 0, 1, 2, 3, 7, 10, 12, 52, -1, 256};
        int[] values = {-1, 0, 1, 2, 3, 4};
        for (int f : flags) {
            for (int v : values) assertFalse(f + " " + v, sender.climate(f, v));
        }
        int[][] bad = {
            {9, 3}, {9, 2}, {9, -1}, {6, 0}, {6, 5}, {6, -1}, {51, 0}, {51, 2}, {8, 0}, {8, 2},
            {50, 0}, {50, 3}, {49, 0}, {49, 3}, {4, 0}, {4, 3}, {22, 0}, {22, 3},
            {50, Integer.MAX_VALUE}, {6, Integer.MIN_VALUE},
        };
        for (int[] p : bad) assertFalse(p[0] + " " + p[1], sender.climate(p[0], p[1]));
        int[] badEco = {0, 3, -1, 100};
        for (int v : badEco) assertFalse("eco " + v, sender.eco(v));
        assertTrue(calls.calls.isEmpty());
        assertEquals(flags.length * values.length + bad.length + badEco.length, log.lines.size());
        assertEquals("send refused setFrontClimateControl 32 -1", log.lines.get(0));
        assertTrue(log.lines.contains("send refused setFrontClimateControl 9 3"));
        assertEquals("send refused setClimateControlECOMode 0",
                log.lines.get(log.lines.size() - badEco.length));
    }

    @Test
    public void aThrowingCallIsLoggedAndReturnsFalseOnce() {
        calls.toThrow = new IllegalStateException("no service");
        assertFalse(sender.climate(50, 2));
        assertFalse(sender.eco(2));
        calls.toThrow = new LinkageError("bad");
        assertFalse(sender.climate(9, 1));
        assertEquals(Arrays.asList("climate 50 2", "eco 2", "climate 9 1"), calls.calls);
        assertEquals(Arrays.asList(
                "send failed setFrontClimateControl 50 2: java.lang.IllegalStateException: no service",
                "send failed setClimateControlECOMode 2: java.lang.IllegalStateException: no service",
                "send failed setFrontClimateControl 9 1: java.lang.LinkageError: bad"), log.lines);
    }

    @Test
    public void aBrokenLogDoesNotStopTheSendOrReachTheCaller() {
        CheckedCabinSender s = new CheckedCabinSender(calls, new SendLog() {
            public void line(String text) { throw new RuntimeException("log"); }
        });
        assertTrue(s.climate(8, 1));
        assertFalse(s.climate(32, 1));
        assertTrue(s.wheelPress(sched));
        sched.advance(CheckedCabinSender.WHEEL_RELEASE_MS);
        assertEquals(Arrays.asList("climate 8 1", "wheel 1", "wheel 0"), calls.calls);
    }

    @Test
    public void wheelPressSendsOneThenZeroAfterTheReleaseTime() {
        assertEquals(200, CheckedCabinSender.WHEEL_RELEASE_MS);
        assertTrue(sender.wheelPress(sched));
        assertEquals(Arrays.asList("wheel 1"), calls.calls);
        assertTrue(sender.wheelPending());
        sched.advance(199);
        assertEquals(Arrays.asList("wheel 1"), calls.calls);
        sched.advance(1);
        assertEquals(Arrays.asList("wheel 1", "wheel 0"), calls.calls);
        assertFalse(sender.wheelPending());
        assertEquals(Arrays.asList("sent setSWRC_SteeringwheelWarmerSet 1",
                "sent setSWRC_SteeringwheelWarmerSet 0"), log.lines);
    }

    @Test
    public void aFailingOneSendsNoZero() {
        calls.failWheelOne = true;
        assertFalse(sender.wheelPress(sched));
        assertFalse(sender.wheelPending());
        sched.advance(5000);
        assertEquals(Arrays.asList("wheel 1"), calls.calls);
        assertEquals(Arrays.asList(
                "send failed setSWRC_SteeringwheelWarmerSet 1: java.lang.IllegalStateException: gone"),
                log.lines);
        // a later press works, and the earlier press's release still sends nothing
        calls.failWheelOne = false;
        assertTrue(sender.wheelPress(sched));
        sched.advance(5000);
        assertEquals(Arrays.asList("wheel 1", "wheel 1", "wheel 0"), calls.calls);
    }

    @Test
    public void aFailedOneFollowedQuicklyByAPressStillSendsOnlyOneZero() {
        calls.failWheelOne = true;
        assertFalse(sender.wheelPress(sched));
        sched.advance(100);
        calls.failWheelOne = false;
        assertTrue(sender.wheelPress(sched));
        // the failed press's release time comes first: it must not release the new press early
        sched.advance(100);
        assertEquals(Arrays.asList("wheel 1", "wheel 1"), calls.calls);
        sched.advance(100);
        assertEquals(Arrays.asList("wheel 1", "wheel 1", "wheel 0"), calls.calls);
        sched.advance(5000);
        assertEquals(3, calls.calls.size());
    }

    @Test
    public void aSecondPressWhilePendingIsRefused() {
        assertTrue(sender.wheelPress(sched));
        assertFalse(sender.wheelPress(sched));
        assertEquals("send refused setSWRC_SteeringwheelWarmerSet 1: a press is pending", log.lines.get(1));
        sched.advance(200);
        assertEquals(Arrays.asList("wheel 1", "wheel 0"), calls.calls);
        assertTrue(sender.wheelPress(sched));
        sched.advance(200);
        assertEquals(Arrays.asList("wheel 1", "wheel 0", "wheel 1", "wheel 0"), calls.calls);
    }

    @Test
    public void aFailingZeroIsLoggedAndEndsThePress() {
        calls.failWheelZero = true;
        assertTrue(sender.wheelPress(sched));
        sched.advance(200);
        assertFalse(sender.wheelPending());
        assertEquals("send failed setSWRC_SteeringwheelWarmerSet 0: java.lang.IllegalStateException: gone",
                log.lines.get(1));
        sched.advance(5000);
        assertEquals(Arrays.asList("wheel 1", "wheel 0"), calls.calls);
    }

    @Test
    public void aPressThatCannotScheduleItsReleaseSendsNothing() {
        assertFalse(sender.wheelPress(new Scheduler() {
            public void after(long delayMs, Runnable r) { throw new IllegalStateException("looper gone"); }
        }));
        assertFalse(sender.wheelPress(null));
        assertTrue(calls.calls.isEmpty());
        assertFalse(sender.wheelPending());
        assertTrue(log.lines.get(0).startsWith("send refused setSWRC_SteeringwheelWarmerSet 1: "));
    }
}
