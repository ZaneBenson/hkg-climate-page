// Compile-only declaration of the names of the stock split-screen panel's page base class
// (com.mobis.splitscreen.right.k) that our page uses. Written for this project from the
// method and field names we observed, not copied from the stock app. It is only on javac's and
// d8's class path (build/panel-stub) and never appears in the output dex: on the car the stock
// class is the real one.
package com.mobis.splitscreen.right;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.view.View;

public abstract class k {
    protected Context mContext;
    protected Handler mHandler;
    public volatile View mView;

    public k() { }

    public abstract View getView();

    public void f(boolean b) { }

    public void a(boolean b, Configuration c) { }

    public void H(boolean b) { }

    public void e(int i, boolean b) { }

    public synchronized void bh(int id) { }
}
