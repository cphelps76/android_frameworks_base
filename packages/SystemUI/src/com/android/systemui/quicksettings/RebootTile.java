/*
 * Copyright (C) 2014 Matricom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class RebootTile extends QuickSettingsTile {

    private Handler mHandler;

    public RebootTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mHandler = handler;
        mLabel = mContext.getString(R.string.quick_settings_reboot);
        mDrawable = R.drawable.ic_qs_reboot;

        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                mHandler.postDelayed(mRunnable, 500);
            }
        };
        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_POWERMENU);
                mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT_OR_SELF);
                mQsc.mBar.collapseAllPanels(true);
                return true;
            }
        };
    }

    private Runnable mRunnable = new Runnable() {
        public void run() {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            pm.reboot("");
        }
    };
}
