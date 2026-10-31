package hkgclimate.page;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import com.mobis.splitscreen.right.k;
import hkgclimate.link.PageLink;

/**
 * The climate page in the stock split-screen panel. It draws what our app sends and forwards
 * taps to it; it holds no car logic and sends nothing to the car. The panel runs us in a system
 * process with no error handling of its own, so every entry point catches Throwable.
 */
public final class ClimatePage extends k {
    static final String TAG = "HkgClimatePage";

    // our own handler: the panel's mHandler has a looper we don't know
    private final Handler main = new Handler(Looper.getMainLooper());
    // written on the main thread (getView), read from any thread
    private volatile LinkClient link;
    // set once by release(): a released page never binds again
    private volatile boolean released;
    // what the panel last told us through f(); true until told otherwise, so getView starts the link
    private volatile boolean enabled = true;
    // main thread only
    private boolean buildFailed;

    /** Worker thread: only stores the context and handler. No views, no binding. */
    ClimatePage(Context context, Handler handler) {
        super();
        mContext = context;
        mHandler = handler;
    }

    @Override
    public View getView() {
        try {
            View existing = mView;
            if (existing != null) return existing;
            if (released || buildFailed) return null;
            Context context = mContext;
            boolean night = isNight(context.getResources().getConfiguration());
            final PageView v = new PageView(context, night);
            final LinkClient l = new LinkClient(context, main, new LinkClient.Sink() {
                @Override public void onState(Bundle state) { v.render(state); }
                @Override public void onNotice(String text) { v.showNotice(text); }
            });
            v.setListener(new PageView.Listener() {
                @Override public void onSeatTap(int seat) { l.tap(seat); }
                @Override public void onPresetTap(int index) { l.preset(index); }
                @Override public void onSettings() { openSettings(); }
            });
            link = l;
            mView = v;
            if (enabled) l.start();
            return v;
        } catch (Throwable t) {
            buildFailed = true;
            Log.e(TAG, "building the climate page failed", t);
            return null;
        }
    }

    /** The panel enables (true) or disables (false) the page. */
    @Override
    public void f(boolean enable) {
        try {
            enabled = enable;
            LinkClient l = link;
            if (l == null) return;
            if (enable) {
                if (!released) l.start();
            } else {
                l.stop();
            }
        } catch (Throwable t) {
            Log.e(TAG, "f failed", t);
        }
    }

    /** Day/night (or language) change; only called once mView is set. */
    @Override
    public void a(boolean b, Configuration configuration) {
        try {
            final boolean night = configuration != null && isNight(configuration);
            main.post(new Runnable() {
                @Override public void run() {
                    try {
                        View v = mView;
                        if (v instanceof PageView) ((PageView) v).setNight(night);
                    } catch (Throwable t) {
                        Log.e(TAG, "setting day/night failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "a failed", t);
        }
    }

    /** Stop talking to our app. Safe from any thread and to call more than once. */
    void release() {
        try {
            released = true;
            LinkClient l = link;
            if (l != null) l.stop();
        } catch (Throwable t) {
            Log.e(TAG, "release failed", t);
        }
    }

    private static boolean isNight(Configuration c) {
        return (c.uiMode & 0x30) == 0x20;
    }

    private void openSettings() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(PageLink.PACKAGE, PageLink.SETTINGS));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(i);
        } catch (Throwable t) {
            Log.e(TAG, "opening the settings failed", t);
        }
    }
}
