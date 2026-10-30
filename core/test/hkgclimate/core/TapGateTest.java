package hkgclimate.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TapGateTest {
    @Test
    public void acceptsEachIdOnceWhileItIsAmongTheLastSixtyFour() {
        TapGate gate = new TapGate();
        for (long id = 1; id <= 64; id++) assertTrue("id " + id, gate.accept(id));
        assertFalse(gate.accept(1));
        assertFalse(gate.accept(64));
    }

    @Test
    public void refusedIdsAreNotRecordedSoTheyDoNotPushOthersOut() {
        TapGate gate = new TapGate();
        for (long id = 1; id <= 64; id++) gate.accept(id);
        for (int i = 0; i < 100; i++) assertFalse(gate.accept(1));
        // id 1 is still the oldest, so one new id pushes it out and nothing else
        assertTrue(gate.accept(100));
        assertTrue(gate.accept(1));
        assertFalse(gate.accept(3));
    }

    @Test
    public void anIdIsAcceptedAgainOnceSixtyFiveNewIdsHaveCome() {
        TapGate gate = new TapGate();
        for (long id = 1; id <= 64; id++) gate.accept(id);
        for (long id = 101; id <= 165; id++) assertTrue("id " + id, gate.accept(id));
        assertTrue(gate.accept(1));
    }

    @Test
    public void zeroAndNegativeIdsAreOrdinaryIds() {
        TapGate gate = new TapGate();
        assertTrue(gate.accept(0));
        assertFalse(gate.accept(0));
        assertTrue(gate.accept(-1));
        assertFalse(gate.accept(-1));
    }
}
