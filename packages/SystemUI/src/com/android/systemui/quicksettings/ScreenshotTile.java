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
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class ScreenshotTile extends QuickSettingsTile {

    private Handler mHandler;

    public ScreenshotTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mLabel = mContext.getString(R.string.quick_settings_screenshot);
        mDrawable = R.drawable.ic_qs_screenshot;

        mHandler = handler;

        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            mQsc.mBar.collapseAllPanels(true);
            mHandler.postDelayed(mRunnable, 850);
            }
        };
    }

    private Runnable mRunnable = new Runnable() {
        public void run() {
            Intent intent = new Intent(Intent.ACTION_SCREENSHOT);
            mContext.sendBroadcast(intent);
        }
    };
}
