package hkgclimate.core;

/** Where a seat command goes. In the test build it only logs; nothing here talks to the car. */
public interface SeatCommander {
    void set(Seat seat, int value);
}
