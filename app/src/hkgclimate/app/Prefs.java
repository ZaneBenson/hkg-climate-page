package hkgclimate.app;

import android.content.Context;
import android.content.SharedPreferences;
import hkgclimate.core.Preset;
import hkgclimate.core.PresetList;
import hkgclimate.core.SwitchTemp;
import java.util.List;

/** Our settings, in the app's private preferences. */
final class Prefs {
    static final String FILE = "hkgclimate";
    static final String KEY_SWITCH_TEMP_F = "switch_temp_f";
    static final String KEY_PAGE_ENABLED = "page_enabled";
    /** The presets as PresetList text. */
    static final String KEY_PRESETS = "presets";

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

    /** The saved presets: the valid lines of the stored text, at most PresetList.MAX. */
    List<Preset> getPresets() { return PresetList.decode(prefs.getString(KEY_PRESETS, "")); }

    /** Stores the presets; PresetList.encode leaves out any that do not pass Preset.check. */
    void setPresets(List<Preset> presets) {
        prefs.edit().putString(KEY_PRESETS, PresetList.encode(presets)).apply();
    }

    /** SharedPreferences keeps only a weak reference: the caller must hold the listener. */
    void register(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.registerOnSharedPreferenceChangeListener(l);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.unregisterOnSharedPreferenceChangeListener(l);
    }
}
