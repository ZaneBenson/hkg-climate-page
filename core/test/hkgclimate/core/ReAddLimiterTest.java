package hkgclimate.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReAddLimiterTest {
    @Test
    public void firstIsAllowedAndSecondWithinTheWindowWaitsTheRest() {
        ReAddLimiter l = new ReAddLimiter(10000);
        assertEquals(0, l.acquire("a", 5000));
        assertEquals(10000, l.acquire("a", 5000));
        assertEquals(6000, l.acquire("a", 9000));
        assertEquals(1, l.acquire("a", 14999));
    }

    @Test
    public void allowedAgainAfterTheWindow() {
        ReAddLimiter l = new ReAddLimiter(10000);
        assertEquals(0, l.acquire("a", 0));
        assertEquals(0, l.acquire("a", 10000));
        // the window restarts from the allowed action, not from the refused ones
        assertEquals(4000, l.acquire("a", 16000));
        assertEquals(0, l.acquire("a", 20000));
    }

    @Test
    public void keysAreIndependent() {
        ReAddLimiter l = new ReAddLimiter(10000);
        assertEquals(0, l.acquire("a", 0));
        assertEquals(0, l.acquire("b", 1000));
        assertEquals(9000, l.acquire("a", 1000));
        assertEquals(10000, l.acquire("b", 1000));
    }

    @Test
    public void clearForgetsEverything() {
        ReAddLimiter l = new ReAddLimiter(10000);
        assertEquals(0, l.acquire("a", 0));
        l.clear();
        assertEquals(0, l.acquire("a", 1));
    }
}
