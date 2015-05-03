package com.blueheronsresistance.stattracker;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

/**
 * Singleton for Android Volley requests
 */
public class MyVolleySingleton {
    private static MyVolleySingleton mInstance;
    private RequestQueue mRequestQueue;
    private static Context mCtx;


    private MyVolleySingleton(Context context) {
        mCtx = context;
        mRequestQueue = getRequestQueue();
    }

    public static synchronized MyVolleySingleton getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MyVolleySingleton(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            //CookieManager manager = new CookieManager();
            //CookieHandler.setDefault(manager);
            //mRequestQueue = Volley.newRequestQueue(mCtx.getApplicationContext());
            mRequestQueue = Volley.newRequestQueue(mCtx.getApplicationContext(), new HurlStack(null, pinnedSSLSocketFactory.getSocketFactory(mCtx)));
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }
}
