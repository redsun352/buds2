package com.yigit.buds2blescanner;

import android.app.Application;

public final class BudsApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashGuard.install(this);
    }
}
