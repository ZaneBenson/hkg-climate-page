package hkgclimate.core;

/** Reads the car's current cabin, steering-wheel and eco state. */
public interface CabinReader {
    /** The state now; never throws and never null: what can't be read is unknown in the result. */
    CabinState read();
}
