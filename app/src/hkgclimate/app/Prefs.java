package hkgclimate.app;

import android.content.Context;
import android.content.SharedPreferences;
import hkgclimate.core.SwitchTemp;

/** Our settings, in the app's private preferences. */
final class Prefs {
    static final String FILE = "hkgclimate";
    static final String KEY_SWITCH_TEMP_F = "switch_temp_f";
    static final String KEY_PAGE_ENABLED = "page_enabled";

    private final SharedPreferences prefs;

    Prefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The outside temperature, in whole °F, at which seats switch from heat to cool. */
    int getSwitchTempF() { return prefs.getInt(KEY_SWITCH_TEMP_F, SwitchTemp.DEFAULT_F); }

    void setSwitchTempF(int f) { prefs.edit().putInt(KEY_SWITCH_TEMP_F, f).apply(); }

    boolean isPageEnabled() { return prefs.getBoolean(KEY_PAGE_ENABLED, true); }

    void setPageEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_PAGE_ENABLED, enabled).apply(); }

    /** SharedPreferences keeps only a weak reference: the caller must hold the listener. */
    void register(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.registerOnSharedPreferenceChangeListener(l);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.unregisterOnSharedPreferenceChangeListener(l);
    }
}
