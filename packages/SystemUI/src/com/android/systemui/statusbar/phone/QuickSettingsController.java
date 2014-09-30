package com.android.systemui.statusbar.phone;

import java.util.ArrayList;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.systemui.quicksettings.AlarmTile;
import com.android.systemui.quicksettings.BugReportTile;
import com.android.systemui.quicksettings.GPSTile;
import com.android.systemui.quicksettings.InputMethodTile;
import com.android.systemui.quicksettings.PreferencesTile;
import com.android.systemui.quicksettings.QuickSettingsTile;
import com.android.systemui.quicksettings.RebootTile;
import com.android.systemui.quicksettings.RingerModeTile;
import com.android.systemui.quicksettings.ScreenshotTile;
import com.android.systemui.quicksettings.UserTile;
import com.android.systemui.quicksettings.WiFiDisplayTile;
import com.android.systemui.quicksettings.WiFiTile;

public class QuickSettingsController {

    private final Context mContext;
    public PanelBar mBar;
    private final ViewGroup mContainerView;
    private final Handler mHandler;
    private final ArrayList<Integer> quicksettings;
    public PhoneStatusBar mStatusBarService;

    // Constants

    public static final int WIFI_TILE = 0;
    public static final int PREFERENCES_TILE = 1;
    public static final int SOUND_TILE = 2;
    public static final int GPS_TILE = 3;
    public static final int REBOOT_TILE = 4;
    public static final int SCREENSHOT_TILE = 5;
    public static final int IME_TILE = 6;
    public static final int ALARM_TILE = 7;
    public static final int BUG_REPORT_TILE = 8;
    public static final int WIFI_DISPLAY_TILE = 9;

    public static final int USER_TILE = 99;

    private InputMethodTile IMETile;

    public QuickSettingsController(Context context, QuickSettingsContainerView container, PhoneStatusBar statusBarService) {
        mContext = context;
        mContainerView = container;
        mHandler = new Handler();
        quicksettings = new ArrayList<Integer>();
        quicksettings.add(USER_TILE);
        quicksettings.add(WIFI_TILE);
        quicksettings.add(PREFERENCES_TILE);
        quicksettings.add(SOUND_TILE);
        quicksettings.add(GPS_TILE);
        quicksettings.add(REBOOT_TILE);
        quicksettings.add(SCREENSHOT_TILE);

        // Temporary tiles. These toggles must be the last ones added to the view, as they will show only when they are needed
        quicksettings.add(ALARM_TILE);
        quicksettings.add(BUG_REPORT_TILE);
        quicksettings.add(WIFI_DISPLAY_TILE);
        quicksettings.add(IME_TILE);
        mStatusBarService = statusBarService;
    }

    void setupQuickSettings(){
        LayoutInflater inflater = LayoutInflater.from(mContext);
        addQuickSettings(inflater);
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    void addQuickSettings(LayoutInflater inflater){
        for(Integer entry: quicksettings){
            QuickSettingsTile qs = null;
            switch(entry){
            case WIFI_TILE:
                qs = new WiFiTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case SOUND_TILE:
                qs = new RingerModeTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case GPS_TILE:
                qs = new GPSTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case ALARM_TILE:
                qs = new AlarmTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case BUG_REPORT_TILE:
                qs = new BugReportTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case WIFI_DISPLAY_TILE:
                qs = new WiFiDisplayTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case PREFERENCES_TILE:
                qs = new PreferencesTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case REBOOT_TILE:
                qs = new RebootTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case SCREENSHOT_TILE:
                qs = new ScreenshotTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case IME_TILE:
                IMETile = new InputMethodTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                qs = IMETile;
                break;
            case USER_TILE:
                qs = new UserTile(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            }
            if(qs != null){
                qs.setupQuickSettingsTile();
            }
        }
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public void setImeWindowStatus(boolean visible){
        IMETile.toggleVisibility(visible);
    }

    public void updateResources() {
        // TODO Auto-generated method stub

    }

}
