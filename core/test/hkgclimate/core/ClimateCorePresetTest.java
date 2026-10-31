package hkgclimate.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ClimateCorePresetTest {
    private static final class Recorder implements SeatCommander {
        final List<String> sent = new ArrayList<String>();
        public void set(Seat seat, int value) { sent.add(seat + "=" + value); }
    }

    private final Recorder rec = new Recorder();
    private final ClimateCore core = new ClimateCore(rec, 60, null);
    private int changes;

    private void ready(int driver, int passenger) {
        core.onSeatsReady(true);
        core.onSeatOption(Seat.DRIVER, 6);
        core.onSeatOption(Seat.PASSENGER, 6);
        core.onSeatState(Seat.DRIVER, driver);
        core.onSeatState(Seat.PASSENGER, passenger);
    }

    private static List<Preset> one(int d, int p) {
        return Arrays.asList(new Preset("A", d, p));
    }

    @Test
    public void setsDriverThenPassenger() {
        ready(2, 2);
        core.setPresets(one(8, 5));
        assertEquals(2, core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("DRIVER=8", "PASSENGER=5"), rec.sent);
    }

    @Test
    public void seatAlreadyAtTargetIsSkipped() {
        ready(8, 2);
        core.setPresets(one(8, 5));
        assertEquals(1, core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("PASSENGER=5"), rec.sent);
    }

    @Test
    public void bothAtTargetSendsNothing() {
        ready(8, 5);
        core.setPresets(one(8, 5));
        assertEquals(0, core.onPresetTap(0, 1));
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void leaveSeatGetsNoCommand() {
        ready(2, 2);
        core.setPresets(one(Preset.LEAVE, 7));
        assertEquals(1, core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("PASSENGER=7"), rec.sent);
        core.setPresets(one(6, Preset.LEAVE));
        assertEquals(1, core.onPresetTap(0, 2));
        assertEquals("DRIVER=6", rec.sent.get(1));
    }

    @Test
    public void coolTargetSkippedOnHeatOnlySeatButOtherSeatStillSet() {
        ready(2, 2);
        core.onSeatOption(Seat.PASSENGER, 0);
        core.setPresets(one(8, 5));
        assertEquals(1, core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("DRIVER=8"), rec.sent);
    }

    @Test
    public void coolTargetSkippedForEveryCoolValueWhenOptionIsNotSix() {
        for (int v = 3; v <= 5; v++) {
            Recorder r = new Recorder();
            ClimateCore c = new ClimateCore(r, 60, null);
            c.onSeatsReady(true);
            c.onSeatOption(Seat.DRIVER, 5);
            c.onSeatState(Seat.DRIVER, 2);
            c.onSeatState(Seat.PASSENGER, 2);
            c.setPresets(one(v, Preset.LEAVE));
            assertEquals(0, c.onPresetTap(0, 1));
            assertTrue(r.sent.isEmpty());
        }
    }

    @Test
    public void heatTargetIsSentOnHeatOnlySeat() {
        ready(2, 2);
        core.onSeatOption(Seat.DRIVER, 0);
        core.setPresets(one(6, 2));
        assertEquals(1, core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("DRIVER=6"), rec.sent);
    }

    @Test
    public void notReadySendsNothing() {
        ready(2, 2);
        core.onSeatsReady(false);
        core.setPresets(one(8, 8));
        assertEquals(0, core.onPresetTap(0, 1));
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void unknownSeatStateSkipsThatSeat() {
        ready(2, 2);
        core.onSeatState(Seat.DRIVER, 9);
        core.setPresets(one(8, 8));
        assertEquals(1, core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("PASSENGER=8"), rec.sent);
        core.onSeatState(Seat.PASSENGER, -1);
        assertEquals(0, core.onPresetTap(0, 2));
        assertEquals(1, rec.sent.size());
    }

    @Test
    public void badIndexOrEmptySlotSendsNothing() {
        ready(2, 2);
        core.setPresets(one(8, 8));
        assertEquals(0, core.onPresetTap(4, 1));
        assertEquals(0, core.onPresetTap(-1, 2));
        assertEquals(0, core.onPresetTap(1, 3));
        core.setPresets(new ArrayList<Preset>());
        assertEquals(0, core.onPresetTap(0, 4));
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void uncheckedPresetsSendNothing() {
        // seats where a passenger value of 2 (or a driver value of 1) would otherwise be sent
        ready(8, 8);
        core.setPresets(Arrays.asList(new Preset("x", 9, 2), new Preset("y", 1, 0),
                new Preset("z", 8, 9), new Preset("", 8, 2)));
        assertEquals(0, core.onPresetTap(0, 1));
        assertEquals(0, core.onPresetTap(1, 2));
        assertEquals(0, core.onPresetTap(2, 3));
        assertEquals(0, core.onPresetTap(3, 4));
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void repeatedRequestIdSendsOnce() {
        ready(2, 2);
        core.setPresets(one(8, 8));
        assertEquals(2, core.onPresetTap(0, 9));
        assertEquals(0, core.onPresetTap(0, 9));
        assertEquals(2, rec.sent.size());
    }

    @Test
    public void requestIdsAreSharedWithSeatTaps() {
        ready(2, 2);
        core.onOutside(OutsideTemp.of(9f, 0xffff, 48f, 0xffff));
        core.setPresets(one(8, 8));
        assertTrue(core.onTap(Seat.DRIVER, 5));
        assertEquals(0, core.onPresetTap(0, 5));
        assertEquals(2, core.onPresetTap(0, 6));
        assertFalse(core.onTap(Seat.DRIVER, 6));
    }

    @Test
    public void settingPresetsAndFeedingStateNeverSends() {
        ready(2, 2);
        core.setPresets(one(8, 8));
        core.onSeatState(Seat.DRIVER, 7);
        core.onSeatsReady(true);
        core.setPresets(null);
        assertTrue(rec.sent.isEmpty());
    }

    @Test
    public void setPresetsKeepsADefensiveCopyOfAtMostFour() {
        ready(2, 2);
        List<Preset> in = new ArrayList<Preset>();
        for (int i = 0; i < 6; i++) in.add(new Preset("p" + i, 8, 8));
        core.setPresets(in);
        in.clear();
        PageState s = core.pageState();
        assertArrayEquals(new String[] {"p0", "p1", "p2", "p3"}, s.presetNames);
        assertEquals(0, core.onPresetTap(4, 1));
    }

    @Test
    public void setPresetsNullIsEmpty() {
        core.setPresets(one(8, 8));
        core.setPresets(null);
        assertArrayEquals(new String[4], core.pageState().presetNames);
    }

    @Test
    public void pageStateNamesAndEnabledForZeroTwoAndFourPresets() {
        ready(2, 2);
        PageState s = core.pageState();
        assertEquals(4, s.presetNames.length);
        assertEquals(4, s.presetEnabled.length);
        assertArrayEquals(new String[4], s.presetNames);
        assertArrayEquals(new boolean[4], s.presetEnabled);

        core.setPresets(Arrays.asList(new Preset("A", 8, 8), new Preset("B", 2, 2)));
        s = core.pageState();
        assertArrayEquals(new String[] {"A", "B", null, null}, s.presetNames);
        assertArrayEquals(new boolean[] {true, true, false, false}, s.presetEnabled);

        core.setPresets(Arrays.asList(new Preset("A", 8, 8), new Preset("B", 2, 2),
                new Preset("C", 3, 3), new Preset("D", 7, -1)));
        s = core.pageState();
        assertArrayEquals(new String[] {"A", "B", "C", "D"}, s.presetNames);
        assertArrayEquals(new boolean[] {true, true, true, true}, s.presetEnabled);
    }

    @Test
    public void presetsAreNotEnabledWhenSeatsNotReady() {
        core.setPresets(Arrays.asList(new Preset("A", 8, 8), new Preset("B", 2, 2)));
        PageState s = core.pageState();
        assertArrayEquals(new String[] {"A", "B", null, null}, s.presetNames);
        assertArrayEquals(new boolean[4], s.presetEnabled);
        core.onSeatsReady(true);
        assertArrayEquals(new boolean[] {true, true, false, false}, core.pageState().presetEnabled);
        core.onSeatsReady(false);
        assertArrayEquals(new boolean[4], core.pageState().presetEnabled);
    }

    @Test
    public void listenerFiresWhenPresetsChange() {
        core.setListener(new Runnable() { public void run() { changes++; } });
        core.setPresets(one(8, 8));
        assertEquals(1, changes);
        core.setPresets(one(8, 8));
        assertEquals(1, changes);
        core.setPresets(null);
        assertEquals(2, changes);
    }

    /** A core with a real runner on a fake car; seat sends go into the car's call list too. */
    private static final class WithRunner {
        final PresetRunnerTest.FakeCar car = new PresetRunnerTest.FakeCar();
        final PresetRunnerTest.ManualScheduler sched = new PresetRunnerTest.ManualScheduler();
        final CheckedSeatSenderTest.ListLog log = new CheckedSeatSenderTest.ListLog();
        final PresetRunner runner =
                new PresetRunner(new CheckedCabinSender(car, log), car, sched, log);
        final ClimateCore core = new ClimateCore(new SeatCommander() {
            public void set(Seat seat, int value) { car.calls.add(seat + "=" + value); }
        }, 60, runner);

        void ready(int driver, int passenger) {
            core.onSeatsReady(true);
            core.onSeatOption(Seat.DRIVER, 6);
            core.onSeatOption(Seat.PASSENGER, 6);
            core.onSeatState(Seat.DRIVER, driver);
            core.onSeatState(Seat.PASSENGER, passenger);
        }
    }

    private static final int L = Preset.LEAVE;

    @Test
    public void presetTapAppliesSeatsThenStartsTheRunner() {
        WithRunner w = new WithRunner();
        w.ready(2, 2);
        w.core.setPresets(Arrays.asList(new Preset("Cold", 8, 8, 1, L, L, L, L, L, L, L)));
        assertEquals(3, w.core.onPresetTap(0, 1));
        // the seats were just sent: the runner's first command waits 400 ms
        assertEquals(Arrays.asList("DRIVER=8", "PASSENGER=8"), w.car.calls);
        assertTrue(w.runner.busy());
        w.sched.advance(399);
        assertEquals(Arrays.asList("DRIVER=8", "PASSENGER=8"), w.car.calls);
        w.sched.advance(1);
        assertEquals(Arrays.asList("DRIVER=8", "PASSENGER=8", "wheel 1"), w.car.calls);
        w.sched.runAll();
        assertEquals(Arrays.asList("DRIVER=8", "PASSENGER=8", "wheel 1", "wheel 0"), w.car.calls);
        assertEquals("Cold: wheel on", w.runner.lastResult());
    }

    @Test
    public void seatAndPresetTapsAreRefusedWhileARunIsBusy() {
        WithRunner w = new WithRunner();
        w.ready(2, 2);
        w.core.onOutside(OutsideTemp.of(9f, 0xffff, 48f, 0xffff));
        w.core.setPresets(Arrays.asList(new Preset("T", L, L, L, L, L, 70, L, L, L, L),
                new Preset("S", 8, 8)));
        assertEquals(1, w.core.onPresetTap(0, 1));
        assertTrue(w.runner.busy());
        assertFalse(w.core.onTap(Seat.DRIVER, 2));
        assertEquals(0, w.core.onPresetTap(1, 3));
        assertEquals(0, w.core.onPresetTap(0, 4));
        assertEquals(Arrays.asList("climate 50 2"), w.car.calls);
        w.sched.runAll();
        assertFalse(w.runner.busy());
        assertTrue(w.core.onTap(Seat.DRIVER, 5));
        assertEquals(2, w.core.onPresetTap(1, 6));
    }

    @Test
    public void runnerItemsRunWhenTheSeatsAreNotReady() {
        WithRunner w = new WithRunner();
        w.core.setPresets(Arrays.asList(new Preset("Cold", 8, 8, 1, L, L, L, L, L, L, L),
                new Preset("S", 8, 8)));
        assertArrayEquals(new boolean[] {true, false, false, false}, w.core.pageState().presetEnabled);
        assertEquals(1, w.core.onPresetTap(0, 1));
        assertEquals(Arrays.asList("wheel 1"), w.car.calls);
        w.sched.runAll();
        assertEquals(0, w.core.onPresetTap(1, 2));
    }

    @Test
    public void withoutARunnerOnlySeatItemsRunAndRunnerOnlyPresetsAreDisabled() {
        ready(2, 2);
        core.setPresets(Arrays.asList(new Preset("W", L, L, 1, L, L, L, L, L, L, L),
                new Preset("Cold", 8, L, 1, L, L, L, L, L, L, L)));
        assertArrayEquals(new boolean[] {false, true, false, false}, core.pageState().presetEnabled);
        assertEquals(0, core.onPresetTap(0, 1));
        assertEquals(1, core.onPresetTap(1, 2));
        assertEquals(Arrays.asList("DRIVER=8"), rec.sent);
    }

    @Test
    public void aRunnerPresetWithNothingToChangeStillCountsAsStarted() {
        WithRunner w = new WithRunner();
        w.car.wheel = 1;
        w.core.setPresets(Arrays.asList(new Preset("W", L, L, 1, L, L, L, L, L, L, L)));
        assertEquals(1, w.core.onPresetTap(0, 1));
        assertFalse(w.runner.busy());
        assertTrue(w.car.calls.isEmpty());
    }

    @Test
    public void pageStateEqualityCoversPresets() {
        core.setPresets(one(8, 8));
        PageState a = core.pageState();
        core.setPresets(Arrays.asList(new Preset("B", 8, 8)));
        PageState b = core.pageState();
        assertFalse(a.equals(b));
        core.setPresets(one(8, 8));
        assertEquals(a, core.pageState());
        assertEquals(a.hashCode(), core.pageState().hashCode());
        assertNull(core.pageState().presetNames[1]);
    }
}
