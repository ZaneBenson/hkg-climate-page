package hkgclimate.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
    // held here: SharedPreferences keeps only a weak reference to its listeners
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (core != null && (key == null || Prefs.KEY_SWITCH_TEMP_F.equals(key))) {
                        core.setSwitchTempF(prefs.getSwitchTempF());
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            prefs = new Prefs(this);
            core = new ClimateCore(new TestModeCommander(), prefs.getSwitchTempF());
            car = new CarLink(this, core);
            binder = new PageLinkBinder(core, car);
            car.setOnDebugChange(new Runnable() {
                @Override public void run() { binder.onDebugChanged(); }
            });
            prefs.register(prefListener);
            // Task 6: create the PageListKeeper here (it reads prefs.isPageEnabled()).
            car.start();
        } catch (Throwable t) {
            Log.e(TAG, "climate service failed to start", t);
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
            if (binder != null) binder.close();
            if (car != null) car.stop();
        } catch (Throwable t) {
            Log.w(TAG, "climate service stop failed", t);
        }
        super.onDestroy();
    }
}
