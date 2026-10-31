package hkgclimate.core;

/** Runs a task later on the thread that owns the core (the main looper on the head unit). */
public interface Scheduler {
    void after(long delayMs, Runnable r);
}
