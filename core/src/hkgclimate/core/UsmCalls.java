package hkgclimate.core;

/** The two stock seat-service calls. The Android side makes them; core only decides when. */
public interface UsmCalls {
    void driver(int value) throws Exception;

    void passenger(int value) throws Exception;
}
