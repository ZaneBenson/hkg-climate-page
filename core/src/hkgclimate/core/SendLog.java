package hkgclimate.core;

/** Where send results are written; the Android side puts each line in the log. */
public interface SendLog {
    void line(String text);
}
