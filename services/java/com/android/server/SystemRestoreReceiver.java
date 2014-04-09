package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RecoverySystem;
import android.util.Log;
import android.util.Slog;

import java.io.IOException;

public class SystemRestoreReceiver extends BroadcastReceiver {
    private static final String TAG = "SystemRestoreReceiver";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Slog.w(TAG, "!!! SYSTEM RESTORE !!!");
        // The reboot call is blocking, so we need to do it on another thread.
        Thread thr = new Thread("Reboot") {
            @Override
            public void run() {
                try {
                    RecoverySystem.rebootRestoreSystem(context);
                    Log.wtf(TAG, "Still running after system restore?!");
                } catch (IOException e) {
                    Slog.e(TAG, "Can't perform system restore reset", e);
                }
            }
        };
        thr.start();
    }
}
