package hkgclimate.core;

/** Our own clock (Android 4.4 has no java.util.function). Milliseconds from any fixed start. */
public interface Clock {
    long now();
}
