package hkgclimate.app;

import android.content.Context;
import android.content.SharedPreferences;
import hkgclimate.core.SwitchTemp;

/** Our settings, in the app's private preferences. */
final class Prefs {
    static final String FILE = "hkgclimate";
    static final String KEY_SWITCH_TEMP_F = "switch_temp_f";
    static final String KEY_PAGE_ENABLED = "page_enabled";
    /** The owner's approval to send the seat command; off by default (and after a reinstall). */
    static final String KEY_SEAT_APPROVED = "seat_command_approved";

    private final SharedPreferences prefs;

    Prefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The outside temperature, in whole °F, at which seats switch from heat to cool; kept within MIN_F..MAX_F. */
    int getSwitchTempF() {
        int f = prefs.getInt(KEY_SWITCH_TEMP_F, SwitchTemp.DEFAULT_F);
        return Math.max(SwitchTemp.MIN_F, Math.min(SwitchTemp.MAX_F, f));
    }

    void setSwitchTempF(int f) { prefs.edit().putInt(KEY_SWITCH_TEMP_F, f).apply(); }

    boolean isPageEnabled() { return prefs.getBoolean(KEY_PAGE_ENABLED, true); }

    void setPageEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_PAGE_ENABLED, enabled).apply(); }

    boolean isSeatApproved() { return prefs.getBoolean(KEY_SEAT_APPROVED, false); }

    void setSeatApproved(boolean approved) { prefs.edit().putBoolean(KEY_SEAT_APPROVED, approved).apply(); }

    /** SharedPreferences keeps only a weak reference: the caller must hold the listener. */
    void register(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.registerOnSharedPreferenceChangeListener(l);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.unregisterOnSharedPreferenceChangeListener(l);
    }
}
