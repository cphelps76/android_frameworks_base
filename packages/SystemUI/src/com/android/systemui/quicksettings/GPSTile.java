package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;


public class GPSTile extends QuickSettingsTile implements LocationSettingsChangeCallback {

    private boolean enabled = false;
    private boolean working = false;

    ContentResolver mContentResolver;
    private LocationController mLocationController;
    private boolean mLocationEnabled;

    public GPSTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mContentResolver = mContext.getContentResolver();
        mLocationController = new LocationController(mContext);
        mLocationController.addSettingsChangedCallback(this);

        mLabel = mContext.getString(R.string.quick_settings_gps);
        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER);

        onClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                mLocationController.setLocationEnabled(!mLocationEnabled);
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER);
                mLabel = mContext.getString(R.string.quick_settings_gps);
                setGenericLabel();
                updateTile();
            }
        };

        mIntentFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
    }

    @Override
    void onPostCreate() {
        setGenericLabel();
        updateTile();
        super.onPostCreate();
    }

    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        int textResId = mLocationEnabled ? R.string.quick_settings_location_label
                : R.string.quick_settings_location_off_label;
        mLabel = mContext.getText(textResId).toString();
        mDrawable = mLocationEnabled
                ? R.drawable.ic_qs_location_on : R.drawable.ic_qs_location_off;
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        mLocationEnabled = locationEnabled;
        updateResources();
    }

    private void setGenericLabel() {
        // Show OFF next to the GPS label when in OFF state, ON/IN USE is indicated by the color
        String label = mContext.getString(R.string.quick_settings_gps);
        mLabel = (enabled ? label : label + " " + mContext.getString(R.string.quick_settings_label_disabled));
    }
}
