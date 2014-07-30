package com.android.systemui;

import android.app.SystemWriteManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.WindowManagerPolicy;
import android.util.Log;

/**
 * Performs adjustment of HDMI output based on the highest supported TV resolution
 */
public class HdmiStateChangeReceiver extends BroadcastReceiver {

    private static final String TAG = HdmiStateChangeReceiver.class.getSimpleName();

    private HdmiManager mHdmiManager;

    @Override
    public void onReceive(final Context context, Intent intent) {
         String action = intent.getAction();
         mHdmiManager = new HdmiManager(context);

         if (action.equals(WindowManagerPolicy.ACTION_HDMI_HW_PLUGGED)) {
             boolean plugged = intent.getBooleanExtra(WindowManagerPolicy.EXTRA_HDMI_HW_PLUGGED_STATE, false);
             handleHdmiAdjustment(plugged);
         }
    }

    private void handleHdmiAdjustment(boolean plugged) {
        Log.d(TAG, "Handling HDMI state change");
        if (plugged) {
            mHdmiManager.hdmiPlugged();
        } else {
            mHdmiManager.hdmiUnplugged();
        }
    }
}
