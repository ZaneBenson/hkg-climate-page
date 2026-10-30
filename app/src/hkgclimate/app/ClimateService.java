package hkgclimate.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import hkgclimate.core.ClimateCore;

/**
 * The climate service the page binds to. It owns the core, reads the car through CarLink and
 * serves the page state through PageLinkBinder. This build only logs taps (TestModeCommander).
 */
public class ClimateService extends Service {
    private static final String TAG = CarLink.TAG;

    private Prefs prefs;
    private ClimateCore core;
    private CarLink car;
    private PageLinkBinder binder;
    private PageListKeeper keeper;
    // held here: SharedPreferences keeps only a weak reference to its listeners
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (core != null && (key == null || Prefs.KEY_SWITCH_TEMP_F.equals(key))) {
                        core.setSwitchTempF(prefs.getSwitchTempF());
                    }
                    if (keeper != null && (key == null || Prefs.KEY_PAGE_ENABLED.equals(key))) {
                        try {
                            keeper.setEnabled(prefs.isPageEnabled());
                        } catch (Throwable t) {
                            Log.w(TAG, "updating the page lists failed", t);
                        }
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        // start ourselves: a service that is only bound dies with its last bind (the panel unbinds
        // when our page leaves its list), and START_STICKY only keeps a started service alive
        try {
            startService(new Intent(this, ClimateService.class));
        } catch (Throwable t) {
            Log.w(TAG, "climate service could not start itself", t);
        }
        try {
            prefs = new Prefs(this);
            core = new ClimateCore(new TestModeCommander(), new TestModeCommander(),
                    prefs.getSwitchTempF());
            car = new CarLink(this, core);
            binder = new PageLinkBinder(core, new PageLinkBinder.DebugSource() {
                @Override public Bundle debug() {
                    Bundle d = car.debug();
                    PageListKeeper k = keeper;
                    d.putString(PageStates.D_PAGE_LIST_ERROR, k == null ? null : k.lastError());
                    return d;
                }
            });
            car.setOnDebugChange(new Runnable() {
                @Override public void run() { binder.onDebugChanged(); }
            });
            prefs.register(prefListener);
            car.start();
        } catch (Throwable t) {
            Log.e(TAG, "climate service failed to start", t);
        }
        // its own try: the page lists must not depend on the car link coming up, nor the reverse
        try {
            keeper = new PageListKeeper(this);
            keeper.setOnChange(new Runnable() {
                @Override public void run() {
                    if (binder != null) binder.onDebugChanged();
                }
            });
            keeper.setEnabled(prefs.isPageEnabled());
        } catch (Throwable t) {
            Log.e(TAG, "page list keeper failed to start", t);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (prefs != null) prefs.unregister(prefListener);
            if (keeper != null) keeper.close();
            if (binder != null) binder.close();
            if (car != null) car.stop();
        } catch (Throwable t) {
            Log.w(TAG, "climate service stop failed", t);
        }
        super.onDestroy();
    }
}
