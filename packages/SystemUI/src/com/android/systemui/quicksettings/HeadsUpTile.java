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
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class HeadsUpTile extends QuickSettingsTile {

    public static final String SETTINGS_APP = "com.android.settings";
    public static final String HEADSUP_SETTINGS =
            "com.android.settings.Settings$HeadsUpSettingsActivity";

    public HeadsUpTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        onClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.HEADS_UP_NOTIFICATION,
                        isEnabled() ? 0 : 1);
                updateResources();
                mQsc.mBar.collapseAllPanels(true);
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                Intent headsupSettings = new Intent();
                headsupSettings.setComponent(new ComponentName(SETTINGS_APP, HEADSUP_SETTINGS));
                startSettingsActivity(headsupSettings);
                return true;
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HEADS_UP_NOTIFICATION), false, mSettingsObserver);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateResources();
        }
    };

    private void updateTile() {
        if (isEnabled()) {
            mDrawable = R.drawable.ic_qs_headsup_on;
            mLabel = mContext.getString(R.string.quick_settings_headsup_on);
        } else {
            mDrawable = R.drawable.ic_qs_headsup_off;
            mLabel = mContext.getString(R.string.quick_settings_headsup_off);
        }
    }

    private boolean isEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HEADS_UP_NOTIFICATION, 0) != 0;
    }
}
