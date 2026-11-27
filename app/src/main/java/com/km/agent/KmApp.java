package com.km.agent;

import android.app.Application;
import android.content.Context;

public class KmApp extends Application {
    private static volatile Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context context() {
        if (appContext == null) throw new IllegalStateException("KM application context unavailable");
        return appContext;
    }
}
