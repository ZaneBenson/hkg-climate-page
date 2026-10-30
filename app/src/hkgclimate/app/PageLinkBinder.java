package hkgclimate.app;

import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import hkgclimate.core.ClimateCore;
import hkgclimate.core.Seat;
import hkgclimate.link.PageLink;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * The service side of the page protocol (see PageLink). Binder calls arrive on binder threads and
 * are posted to the main looper, where the core and the subscriber list live. Only the panel app
 * (uid 1000) and our own uid may call.
 */
final class PageLinkBinder extends Binder {
    /** The raw car values for the settings screen. */
    interface DebugSource {
        Bundle debug();
    }

    private static final String TAG = CarLink.TAG;

    private static final class Subscriber implements DeathRecipient {
        final IBinder callback;
        final PageLinkBinder owner;

        Subscriber(IBinder callback, PageLinkBinder owner) {
            this.callback = callback;
            this.owner = owner;
        }

        @Override
        public void binderDied() {
            owner.handler.post(new Runnable() {
                @Override public void run() { owner.drop(Subscriber.this); }
            });
        }
    }

    private final ClimateCore core;
    private final DebugSource debugSource;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // main thread only
    private final ArrayList<Subscriber> subscribers = new ArrayList<Subscriber>();
    // binder threads; guarded by itself
    private final Set<Integer> refusedUids = new HashSet<Integer>();

    PageLinkBinder(ClimateCore core, DebugSource debugSource) {
        this.core = core;
        this.debugSource = debugSource;
        attachInterface(null, PageLink.DESCRIPTOR);
        core.setListener(new Runnable() {
            @Override public void run() { broadcast(); }
        });
    }

    /** Main thread: stop listening to the core and let go of every subscriber. */
    void close() {
        core.setListener(null);
        for (Subscriber s : new ArrayList<Subscriber>(subscribers)) drop(s);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        try {
            if (!callerAllowed()) return false;
            switch (code) {
                case PageLink.TX_SUBSCRIBE: {
                    data.enforceInterface(PageLink.DESCRIPTOR);
                    final IBinder cb = data.readStrongBinder();
                    if (cb != null) {
                        handler.post(new Runnable() {
                            @Override public void run() { subscribe(cb); }
                        });
                    }
                    done(reply, flags);
                    return true;
                }
                case PageLink.TX_UNSUBSCRIBE: {
                    data.enforceInterface(PageLink.DESCRIPTOR);
                    final IBinder cb = data.readStrongBinder();
                    if (cb != null) {
                        handler.post(new Runnable() {
                            @Override public void run() { unsubscribe(cb); }
                        });
                    }
                    done(reply, flags);
                    return true;
                }
                case PageLink.TX_TAP: {
                    data.enforceInterface(PageLink.DESCRIPTOR);
                    int seatIndex = data.readInt();
                    final long id = data.readLong();
                    Seat[] seats = Seat.values();
                    if (seatIndex < 0 || seatIndex >= seats.length) {
                        Log.w(TAG, "tap for an unknown seat " + seatIndex + " ignored");
                        done(reply, flags);
                        return true;
                    }
                    final Seat seat = seats[seatIndex];
                    handler.post(new Runnable() {
                        @Override public void run() { tap(seat, id); }
                    });
                    done(reply, flags);
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        } catch (Throwable t) {
            Log.w(TAG, "page link call " + code + " failed", t);
            return false;
        }
    }

    private static void done(Parcel reply, int flags) {
        if (reply != null && (flags & FLAG_ONEWAY) == 0) reply.writeNoException();
    }

    private boolean callerAllowed() {
        int uid = Binder.getCallingUid();
        if (uid == Process.SYSTEM_UID || uid == Process.myUid()) return true;
        boolean first;
        synchronized (refusedUids) {
            first = refusedUids.add(uid);
        }
        if (first) Log.w(TAG, "page link call refused from uid " + uid);
        return false;
    }

    private void tap(Seat seat, long id) {
        try {
            core.onTap(seat, id);
        } catch (Throwable t) {
            Log.w(TAG, "tap failed", t);
        }
    }

    private void subscribe(IBinder cb) {
        try {
            for (Subscriber s : subscribers) {
                if (s.callback.equals(cb)) {
                    send(s, currentState());
                    return;
                }
            }
            Subscriber s = new Subscriber(cb, this);
            cb.linkToDeath(s, 0);
            subscribers.add(s);
            send(s, currentState());
        } catch (Throwable t) {
            Log.w(TAG, "subscribe failed", t);
        }
    }

    private void unsubscribe(IBinder cb) {
        for (Subscriber s : new ArrayList<Subscriber>(subscribers)) {
            if (s.callback.equals(cb)) drop(s);
        }
    }

    private void drop(Subscriber s) {
        if (!subscribers.remove(s)) return;
        try {
            s.callback.unlinkToDeath(s, 0);
        } catch (Throwable t) {
            // already dead: nothing to unlink
        }
    }

    private Bundle currentState() {
        return PageStates.toBundle(core.pageState(), debugSource.debug());
    }

    private void broadcast() {
        if (subscribers.isEmpty()) return;
        try {
            Bundle state = currentState();
            for (Subscriber s : new ArrayList<Subscriber>(subscribers)) send(s, state);
        } catch (Throwable t) {
            Log.w(TAG, "sending the page state failed", t);
        }
    }

    /** One oneway CB_STATE; a subscriber whose process is gone is dropped. */
    private void send(Subscriber s, Bundle state) {
        Parcel p = Parcel.obtain();
        try {
            p.writeInterfaceToken(PageLink.CALLBACK_DESCRIPTOR);
            p.writeBundle(state);
            s.callback.transact(PageLink.CB_STATE, p, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException e) {
            drop(s);
        } catch (Throwable t) {
            Log.w(TAG, "sending the page state failed", t);
        } finally {
            p.recycle();
        }
    }
}
