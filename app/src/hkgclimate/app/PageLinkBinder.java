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

    /** The bookkeeping after a tap that sent at least one command (the last-command line, the watch). */
    interface AfterSend {
        /**
         * presetIndex: the tapped preset slot, or -1 for a seat tap. sent: what the core counted
         * (for a preset, its seat sends plus 1 when the preset runner started).
         */
        void afterSend(int presetIndex, int sent);
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
    private final AfterSend afterSend;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // main thread only
    private final ArrayList<Subscriber> subscribers = new ArrayList<Subscriber>();
    private boolean closed;
    // binder threads; guarded by itself
    private final Set<Integer> refusedUids = new HashSet<Integer>();

    /**
     * afterSend runs on the main thread once for each tap that sent at least one command (the
     * last-command line and the watch check); it may be null.
     */
    PageLinkBinder(ClimateCore core, DebugSource debugSource, AfterSend afterSend) {
        this.core = core;
        this.debugSource = debugSource;
        this.afterSend = afterSend;
        attachInterface(null, PageLink.DESCRIPTOR);
        core.setListener(new Runnable() {
            @Override public void run() { broadcast(); }
        });
    }

    /** Main thread: stop listening to the core and let go of every subscriber. */
    void close() {
        closed = true;
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
                case PageLink.TX_PRESET: {
                    data.enforceInterface(PageLink.DESCRIPTOR);
                    final int index = data.readInt();
                    final long id = data.readLong();
                    handler.post(new Runnable() {
                        @Override public void run() { presetTap(index, id); }
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
            if (core.onTap(seat, id)) sent(-1, 1);
        } catch (Throwable t) {
            Log.w(TAG, "tap failed", t);
        }
    }

    /** Main thread: one preset tap. The core ignores a bad slot or an id it has seen. */
    private void presetTap(int index, long id) {
        try {
            int sent = core.onPresetTap(index, id);
            if (sent > 0) sent(index, sent);
        } catch (Throwable t) {
            Log.w(TAG, "preset tap failed", t);
        }
    }

    /** Main thread: a tap sent a command; one round of bookkeeping however many it sent. */
    private void sent(int presetIndex, int count) {
        AfterSend r = afterSend;
        if (r != null) r.afterSend(presetIndex, count);
    }

    private void subscribe(IBinder cb) {
        if (closed) return;
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

    /** Main thread: the raw car values changed; send to everyone even if PageState is the same. */
    void onDebugChanged() {
        broadcast();
    }

    private void broadcast() {
        if (closed || subscribers.isEmpty()) return;
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
