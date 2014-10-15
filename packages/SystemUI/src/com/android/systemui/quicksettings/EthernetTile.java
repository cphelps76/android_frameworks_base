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
import android.content.IntentFilter;
import android.net.ethernet.EthernetManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

import static android.net.ethernet.EthernetManager.ETH_STATE_ENABLED;

public class EthernetTile extends QuickSettingsTile {

    public static final String SETTINGS_APP = "com.android.settings";
    public static final String ETHERNET_SETTINGS =
            "com.android.settings.Settings$EthernetSettingsActivityAML";

    private EthernetManager mEthernetManager;

    public EthernetTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mEthernetManager = (EthernetManager) mContext.getSystemService(Context.ETH_SERVICE);

        onClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    mQsc.mBar.collapseAllPanels(true);
                    mEthernetManager.setEthEnabled(!isEthEnabled());
                } catch (Exception e) {
                }
                updateResources();
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                Intent ethernetSettings = new Intent();
                ethernetSettings.setComponent(new ComponentName(SETTINGS_APP, ETHERNET_SETTINGS));
                startSettingsActivity(ethernetSettings);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                updateResources();
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(mEthernetManager.ETH_STATE_CHANGED_ACTION);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    private boolean isEthEnabled() {
        if(mEthernetManager.getEthState( ) == ETH_STATE_ENABLED) {
            return true;
        } else {
            return false;
        }
    }

    public void updateResources() {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putBoolean(resolver, Settings.System.ETHERNET_STATE, isEthEnabled());
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if (!isEthEnabled()) {
            mLabel = mContext.getString(R.string.quick_settings_ethernet);
            mDrawable = R.drawable.ic_qs_ethernet_off;
        } else {
            mLabel = mContext.getString(R.string.quick_settings_ethernet_on);
            mDrawable = R.drawable.ic_qs_ethernet_on;
        }
    }
}
