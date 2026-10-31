package hkgclimate.page;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import hkgclimate.link.PageLink;
import java.util.Random;

/**
 * The page's side of the PageLink protocol: binds our app's service, subscribes with a callback
 * Binder, and sends seat and preset taps. State is touched on the main thread only; start() and stop() may
 * be called from any thread and hop to the main thread. Nothing here blocks: every call to the
 * service is oneway.
 */
final class LinkClient {
    private static final String TAG = ClimatePage.TAG;
    private static final long NOT_INSTALLED_RETRY_MS = 30000;
    private static final long REBIND_MS = 5000;

    static final String NOTICE_NOT_INSTALLED = "Climate app not installed";
    static final String NOTICE_NOT_RUNNING = "Climate app not running";

    /** Receives what the link has to show; called on the main thread. */
    interface Sink {
        void onState(Bundle state);
        void onNotice(String text);
    }

    private final Context context;
    private final Handler main;
    private final Sink sink;
    private final Intent bindIntent;

    // main thread only
    private boolean started;
    private boolean bound;
    private IBinder service;
    // request ids for seat taps and preset taps alike, so they stay unique across both
    private long nextTapId = new Random().nextLong();
    // our app's uid, looked up on the main thread before subscribing; -1 if unknown (then every
    // callback is refused). Read on binder threads.
    private volatile int appUid = -1;
    private volatile boolean refusalLogged;

    private final Binder callback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                if (code != PageLink.CB_STATE) return super.onTransact(code, data, reply, flags);
                // only our own app may push state to the page
                int caller = Binder.getCallingUid();
                if (caller != appUid) {
                    if (!refusalLogged) {
                        refusalLogged = true;
                        Log.w(TAG, "state callback refused from uid " + caller);
                    }
                    return false;
                }
                data.enforceInterface(PageLink.CALLBACK_DESCRIPTOR);
                // binder thread: take our own copy of the Bundle, then render on the main thread
                Bundle read = data.readBundle();
                final Bundle state = read == null ? new Bundle() : new Bundle(read);
                // size() unparcels the copy here, on the binder thread, so the main thread never
                // reads from a Parcel that has been recycled
                state.size();
                main.post(new Runnable() {
                    @Override public void run() { deliver(state); }
                });
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "state callback failed", t);
                return false;
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                connected(binder);
            } catch (Throwable t) {
                Log.e(TAG, "onServiceConnected failed", t);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                disconnected();
            } catch (Throwable t) {
                Log.e(TAG, "onServiceDisconnected failed", t);
            }
        }
    };

    private final Runnable bindAgain = new Runnable() {
        @Override
        public void run() {
            try {
                bind();
            } catch (Throwable t) {
                Log.e(TAG, "binding failed", t);
            }
        }
    };

    LinkClient(Context context, Handler main, Sink sink) {
        this.context = context;
        this.main = main;
        this.sink = sink;
        this.bindIntent = new Intent().setComponent(new ComponentName(PageLink.PACKAGE, PageLink.SERVICE));
    }

    /** Bind and subscribe (if not already). Any thread. */
    void start() {
        onMain(new Runnable() {
            @Override
            public void run() {
                try {
                    if (started) return;
                    started = true;
                    bind();
                } catch (Throwable t) {
                    Log.e(TAG, "start failed", t);
                }
            }
        });
    }

    /** Unsubscribe, unbind and cancel any retry. Any thread. */
    void stop() {
        onMain(new Runnable() {
            @Override
            public void run() {
                try {
                    started = false;
                    main.removeCallbacks(bindAgain);
                    unbind();
                } catch (Throwable t) {
                    Log.e(TAG, "stop failed", t);
                }
            }
        });
    }

    /** Send a tap for seat 0 (driver) or 1 (passenger), oneway, with a new request id. */
    void tap(int seat) {
        try {
            IBinder s = service;
            if (s == null) return;
            long id = nextTapId++;
            Parcel p = Parcel.obtain();
            try {
                p.writeInterfaceToken(PageLink.DESCRIPTOR);
                p.writeInt(seat);
                p.writeLong(id);
                s.transact(PageLink.TX_TAP, p, null, IBinder.FLAG_ONEWAY);
            } finally {
                p.recycle();
            }
        } catch (Throwable t) {
            Log.e(TAG, "tap failed", t);
        }
    }

    /** Send a tap for preset slot 0 to 3, oneway, with a new request id. */
    void preset(int index) {
        try {
            IBinder s = service;
            if (s == null) return;
            long id = nextTapId++;
            Parcel p = Parcel.obtain();
            try {
                p.writeInterfaceToken(PageLink.DESCRIPTOR);
                p.writeInt(index);
                p.writeLong(id);
                s.transact(PageLink.TX_PRESET, p, null, IBinder.FLAG_ONEWAY);
            } finally {
                p.recycle();
            }
        } catch (Throwable t) {
            Log.e(TAG, "preset tap failed", t);
        }
    }

    private void onMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else main.post(r);
    }

    // everything below: main thread

    private void bind() {
        if (!started || bound) return;
        boolean ok;
        try {
            ok = context.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            Log.e(TAG, "bindService failed", t);
            ok = false;
        }
        if (ok) {
            bound = true;
            return;
        }
        // the system wants an unbind even when the bind was refused
        try {
            context.unbindService(connection);
        } catch (Throwable t) {
            // not bound: nothing to undo
        }
        sink.onNotice(NOTICE_NOT_INSTALLED);
        main.removeCallbacks(bindAgain);
        main.postDelayed(bindAgain, NOT_INSTALLED_RETRY_MS);
    }

    private void unbind() {
        IBinder s = service;
        service = null;
        if (s != null) {
            send(s, PageLink.TX_UNSUBSCRIBE);
        }
        if (bound) {
            bound = false;
            try {
                context.unbindService(connection);
            } catch (Throwable t) {
                Log.e(TAG, "unbindService failed", t);
            }
        }
    }

    private void connected(IBinder binder) {
        if (!started || !bound) return;
        appUid = lookUpAppUid();
        service = binder;
        if (!send(binder, PageLink.TX_SUBSCRIBE)) {
            service = null;
        }
    }

    /** The uid of our app's package, or -1 (logged) if it can't be found. */
    private int lookUpAppUid() {
        try {
            return context.getPackageManager().getApplicationInfo(PageLink.PACKAGE, 0).uid;
        } catch (Throwable t) {
            Log.e(TAG, "looking up the uid of " + PageLink.PACKAGE + " failed", t);
            return -1;
        }
    }

    private void disconnected() {
        if (!started) return;
        service = null;
        sink.onNotice(NOTICE_NOT_RUNNING);
        // let go of the dead connection and bind afresh in a few seconds
        if (bound) {
            bound = false;
            try {
                context.unbindService(connection);
            } catch (Throwable t) {
                Log.e(TAG, "unbindService failed", t);
            }
        }
        main.removeCallbacks(bindAgain);
        main.postDelayed(bindAgain, REBIND_MS);
    }

    private void deliver(Bundle state) {
        try {
            if (!started || service == null) return;
            sink.onState(state);
        } catch (Throwable t) {
            Log.e(TAG, "showing the state failed", t);
        }
    }

    /** A oneway subscribe or unsubscribe carrying our callback; false if the call failed. */
    private boolean send(IBinder target, int code) {
        Parcel p = Parcel.obtain();
        try {
            p.writeInterfaceToken(PageLink.DESCRIPTOR);
            p.writeStrongBinder(callback);
            target.transact(code, p, null, IBinder.FLAG_ONEWAY);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "call " + code + " to the climate app failed", t);
            return false;
        } finally {
            p.recycle();
        }
    }
}
