/*
 *         Copyright (C) 2014 Matricom
 *
 * HdmiManager API - used for sysfs interaction to adjust
 *    the display resolution, position, and digital audio
 *    for the TV being used.
 *
 *                 VERSION 3.2
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package android.hardware.display;

import android.app.SystemWriteManager;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.IWindowManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class HdmiManager {

    private static final String TAG = HdmiManager.class.getSimpleName();

    private static final boolean DEBUG = true;

    private static final String ACTION_OUTPUTMODE_CHANGE = "android.intent.action.OUTPUTMODE_CHANGE";
    private static final String ACTION_OUTPUTMODE_SAVE = "android.intent.action.OUTPUTMODE_SAVE";
    private static final String ACTION_OUTPUTMODE_CANCEL = "android.intent.action.OUTPUTMODE_CANCEL";

    public static final String FREESCALE_FB0 = "/sys/class/graphics/fb0/free_scale";
    public static final String FREESCALE_FB1 = "/sys/class/graphics/fb1/free_scale";
    public static final String FREESCALE_AXIS = "/sys/class/graphics/fb0/free_scale_axis";
    public static final String FREESCALE_MODE = "/sys/class/graphics/fb0/freescale_mode";
    public static final String UPDATE_FREESCALE = "/sys/class/graphics/fb0/update_freescale";
    public static final String HDMI_UNPLUGGED = "/sys/class/aml_mod/mod_on";
    public static final String HDMI_PLUGGED = "/sys/class/aml_mod/mod_off";
    public static final String BLANK_DISPLAY = "/sys/class/graphics/fb0/blank";
    public static final String PPSCALER_RECT = "/sys/class/ppmgr/ppscaler_rect";
    public static final String HDMI_SUPPORT_LIST = "/sys/class/amhdmitx/amhdmitx0/disp_cap";
    public static final String DISPLAY_MODE = "/sys/class/display/mode";
    public static final String VIDEO_AXIS = "/sys/class/video/axis";
    public static final String REQUEST_2X_SCALE = "/sys/class/graphics/fb0/request2XScale";
    public static final String SCALE_AXIS_OSD0 = "/sys/class/graphics/fb0/scale_axis";
    public static final String SCALE_AXIS_OSD1 = "/sys/class/graphics/fb1/scale_axis";
    public static final String WINDOW_AXIS = "/sys/class/graphics/fb0/window_axis";
    public static final String SCALE_OSD1 = "/sys/class/graphics/fb1/scale";
    public static final String OUTPUT_AXIS = "/sys/class/display/axis";
    public static final String AUDIODSP_DIGITAL_RAW = "/sys/class/audiodsp/digital_raw";

    public static final String HDMIONLY = "ro.platform.hdmionly";
    public static final String REAL_OUTPUT_MODE = "ro.platform.has.realoutputmode";

    public static final String UBOOT_CVBSMODE = "ubootenv.var.cvbsmode";
    public static final String UBOOT_HDMIMODE = "ubootenv.var.hdmimode";
    public static final String UBOOT_COMMONMODE = "ubootenv.var.outputmode";
    public static final String UBOOT_DIGITAL_AUDIO_OUTPUT = "ubootenv.var.digitaudiooutput";

    public static final String UBOOT_480I_OUTPUT_X = "ubootenv.var.480ioutputx";
    public static final String UBOOT_480I_OUTPUT_Y = "ubootenv.var.480ioutputy";
    public static final String UBOOT_480I_OUTPUT_WIDTH = "ubootenv.var.480ioutputwidth";
    public static final String UBOOT_480I_OUTPUT_HEIGHT = "ubootenv.var.480ioutputheight";
    public static final String UBOOT_480P_OUTPUT_X = "ubootenv.var.480poutputx";
    public static final String UBOOT_480P_OUTPUT_Y = "ubootenv.var.480poutputy";
    public static final String UBOOT_480P_OUTPUT_WIDTH = "ubootenv.var.480poutputwidth";
    public static final String UBOOT_480P_OUTPUT_HEIGHT = "ubootenv.var.480poutputheight";
    public static final String UBOOT_576I_OUTPUT_X = "ubootenv.var.576ioutputx";
    public static final String UBOOT_576I_OUTPUT_Y = "ubootenv.var.576ioutputy";
    public static final String UBOOT_576I_OUTPUT_WIDTH = "ubootenv.var.576ioutputwidth";
    public static final String UBOOT_576I_OUTPUT_HEIGHT = "ubootenv.var.576ioutputheight";
    public static final String UBOOT_576P_OUTPUT_X = "ubootenv.var.576poutputx";
    public static final String UBOOT_576P_OUTPUT_Y = "ubootenv.var.576poutputy";
    public static final String UBOOT_576P_OUTPUT_WIDTH = "ubootenv.var.576poutputwidth";
    public static final String UBOOT_576P_OUTPUT_HEIGHT = "ubootenv.var.576poutputheight";
    public static final String UBOOT_720I_OUTPUT_X = "ubootenv.var.720ioutputx";
    public static final String UBOOT_720I_OUTPUT_Y = "ubootenv.var.720ioutputy";
    public static final String UBOOT_720I_OUTPUT_WIDTH = "ubootenv.var.720ioutputwidth";
    public static final String UBOOT_720I_OUTPUT_HEIGHT = "ubootenv.var.720ioutputheight";
    public static final String UBOOT_720P_OUTPUT_X = "ubootenv.var.720poutputx";
    public static final String UBOOT_720P_OUTPUT_Y = "ubootenv.var.720poutputy";
    public static final String UBOOT_720P_OUTPUT_WIDTH = "ubootenv.var.720poutputwidth";
    public static final String UBOOT_720P_OUTPUT_HEIGHT = "ubootenv.var.720poutputheight";
    public static final String UBOOT_1080I_OUTPUT_X = "ubootenv.var.1080ioutputx";
    public static final String UBOOT_1080I_OUTPUT_Y = "ubootenv.var.1080ioutputy";
    public static final String UBOOT_1080I_OUTPUT_WIDTH = "ubootenv.var.1080ioutputwidth";
    public static final String UBOOT_1080I_OUTPUT_HEIGHT = "ubootenv.var.1080ioutputheight";
    public static final String UBOOT_1080P_OUTPUT_X = "ubootenv.var.1080poutputx";
    public static final String UBOOT_1080P_OUTPUT_Y = "ubootenv.var.1080poutputy";
    public static final String UBOOT_1080P_OUTPUT_WIDTH = "ubootenv.var.1080poutputwidth";
    public static final String UBOOT_1080P_OUTPUT_HEIGHT = "ubootenv.var.1080poutputheight";
    public static final String UBOOT_4K2K24HZ_OUTPUT_X = "ubootenv.var.4k2k24hz_x";
    public static final String UBOOT_4K2K24HZ_OUTPUT_Y = "ubootenv.var.4k2k24hz_y";
    public static final String UBOOT_4K2K24HZ_OUTPUT_WIDTH = "ubootenv.var.4k2k24hz_width";
    public static final String UBOOT_4K2K24HZ_OUTPUT_HEIGHT = "ubootenv.var.4k2k24hz_height";
    public static final String UBOOT_4K2K25HZ_OUTPUT_X = "ubootenv.var.4k2k25hz_x";
    public static final String UBOOT_4K2K25HZ_OUTPUT_Y = "ubootenv.var.4k2k25hz_y";
    public static final String UBOOT_4K2K25HZ_OUTPUT_WIDTH = "ubootenv.var.4k2k25hz_width";
    public static final String UBOOT_4K2K25HZ_OUTPUT_HEIGHT = "ubootenv.var.4k2k25hz_height";
    public static final String UBOOT_4K2K30HZ_OUTPUT_X = "ubootenv.var.4k2k30hz_x";
    public static final String UBOOT_4K2K30HZ_OUTPUT_Y = "ubootenv.var.4k2k30hz_y";
    public static final String UBOOT_4K2K30HZ_OUTPUT_WIDTH = "ubootenv.var.4k2k30hz_width";
    public static final String UBOOT_4K2K30HZ_OUTPUT_HEIGHT = "ubootenv.var.4k2k30hz_height";
    public static final String UBOOT_4K2KSMPTE_OUTPUT_X = "ubootenv.var.4k2ksmpte_x";
    public static final String UBOOT_4K2KSMPTE_OUTPUT_Y = "ubootenv.var.4k2ksmpte_y";
    public static final String UBOOT_4K2KSMPTE_OUTPUT_WIDTH = "ubootenv.var.4k2ksmpte_width";
    public static final String UBOOT_4K2KSMPTE_OUTPUT_HEIGHT = "ubootenv.var.4k2ksmpte_height";

    public static final String[] COMMON_MODE_VALUE_LIST =  {
            "480i", "480p", "576i", "576p", "720p", "1080i", "1080p",
            "720p50hz", "1080i50hz", "1080p50hz", "480cvbs", "576cvbs",
            "4k2k24hz", "4k2k25hz", "4k2k30hz", "4k2ksmpte"
    };

    public static final String[] CVBS_SUPPORT_LIST = { "480cvbs", "576cvbs" };

    // 480p values
    public static final int OUTPUT480_FULL_WIDTH = 720;
    public static final int OUTPUT480_FULL_HEIGHT = 480;
    public static final String DISPLAY_AXIS_480 = " 720 480 ";
    // 576p values
    public static final int OUTPUT576_FULL_WIDTH = 720;
    public static final int OUTPUT576_FULL_HEIGHT = 576;
    public static final String DISPLAY_AXIS_576 = " 720 576 ";
    // 720p values
    public static final int OUTPUT720_FULL_WIDTH = 1280;
    public static final int OUTPUT720_FULL_HEIGHT = 720;
    public static final String DISPLAY_AXIS_720 = " 1280 720 ";
    // 1080p values
    public static final int OUTPUT1080_FULL_WIDTH = 1920;
    public static final int OUTPUT1080_FULL_HEIGHT = 1080;
    public static final String DISPLAY_AXIS_1080 = " 1920 1080 ";
    // 4k2k values
    public static final int OUTPUT4K2K_FULL_WIDTH = 3840;
    public static final int OUTPUT4K2K_FULL_HEIGHT = 2160;
    //4k2k smpte values
    public static final int OUTPUT4K2KSMPTE_FULL_WIDTH = 4096;
    public static final int OUTPUT4K2KSMPTE_FULL_HEIGHT = 2160;

    private Context mContext;
    private static SystemWriteManager mSystemWriteManager;

    public HdmiManager(Context context) {
        mContext = context;
        mSystemWriteManager = (SystemWriteManager) context.getSystemService("system_write");
    }

    /**
     * Method called when HDMI state changes as a result of being unplugged
     * @see {#HdmiStateChangeReceiver}
     */
    public void hdmiUnplugged() {
        Log.d(TAG, "HDMI unplugged");
        String resolution = getRequestedResolution();
        if (isHdmiOnly()) {
            if (isFreescaleClosed()) {
                setOutputWithoutFreescale(resolution);
            } else {
                setOutputMode(resolution);
            }
            if (isCompensated()) syncCompensation();
            openVdac(resolution);
            blankDisplay(false);            
        }
    }

    /**
     * Method called when HDMI state changes as a result of being plugged
     * Also called on boot when HDMI is detected
     * If compensation has been adjusted, this is adjusted as well
     */
    public void hdmiPlugged() {
        Log.d(TAG, "HDMI plugged");
        if (isHdmiOnly()) {
            String resolution = getRequestedResolution();

            closeVdac(resolution);

            if (isFreescaleClosed()) {
                if (DEBUG) Log.d(TAG, "Freescale is closed");
                setOutputWithoutFreescale(resolution);
            } else {
                if (DEBUG) Log.d(TAG, "Freescale is open");
                setOutputMode(resolution);
            }
            blankDisplay(false);
        }
        if (isCompensated()) syncCompensation();
    }

    /**
     * Read #DISPLAY_MODE from sysfs and return the current resolution
     * @return current HDMI resolution
     */
    public String getResolution() {
        if (DEBUG) Log.d(TAG, "Current resolution is " + mSystemWriteManager.readSysfs(DISPLAY_MODE));
        return mSystemWriteManager.readSysfs(DISPLAY_MODE);
    }

    /**
     ** Read the HDMI_SUPPORT_LIST and provide an array of available resolutions
     * @return  resolutions   string array of available resolutions
     */
    public String[] getAvailableResolutions() {
        String[] resolutionsToParse = null;
        ArrayList<String> resolutionsArray = new ArrayList<String>();
        String rawList = readSupportList(HDMI_SUPPORT_LIST);
        if (getResolution().contains("cvbs")) {
            rawList = CVBS_SUPPORT_LIST[0] + "|" + CVBS_SUPPORT_LIST[1];
        }
        if (rawList != null) {
            resolutionsToParse = rawList.split("\\|");
        }
        if (resolutionsToParse != null) {
            for (int i = 0; i < resolutionsToParse.length; i++) {
                String resolution;
                if (resolutionsToParse[i].contains("*")) {
                    String res = resolutionsToParse[i];
                    resolution = res.substring(0, res.length()-1);
                } else {
                    resolution = resolutionsToParse[i];
                }
                resolutionsArray.add(resolution);
            }
        }
        String[] resolutions = new String[resolutionsArray.size()];
        return resolutionsArray.toArray(resolutions);
    }

    /**
     * Get the position array of the current resolution
     * @return display position int array from resolution
     */
    public int[] getResolutionPosition() {
        String resolution = getResolution();
        int[] position = { 0, 0, 1280, 720};
        if (resolution.contains("480")) {
            position[2] = OUTPUT480_FULL_WIDTH;
            position[3] = OUTPUT480_FULL_HEIGHT;
        } else if (resolution.contains("576")) {
            position[2] = OUTPUT576_FULL_WIDTH;
            position[3] = OUTPUT576_FULL_HEIGHT;
        } else if (resolution.contains("1080")) {
            position[2] = OUTPUT1080_FULL_WIDTH;
            position[3] = OUTPUT1080_FULL_HEIGHT;
        } else if (resolution.contains("4k2k")) {
            if (resolution.contains("smpte")) {
                position[2] = OUTPUT4K2KSMPTE_FULL_WIDTH;
                position[3] = OUTPUT4K2KSMPTE_FULL_HEIGHT;
            } else {
                position[2] = OUTPUT4K2K_FULL_WIDTH;
                position[3] = OUTPUT4K2K_FULL_HEIGHT;
            }
        }
        return position;
    }

    private boolean isResolutionAvailable(String resolution) {
        String[] resolutions = getAvailableResolutions();
        for (String res : resolutions) {
            if (resolution.equals(res)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the requested resolution factoring in auto adjustment
     * If user enabled auto adjustment, this resolution supercedes the
     * user selected resolution, as well as the default resolution
     * @return if (!autoAdjust) return user selected resolution, else return best possible
     */
    public String getRequestedResolution() {
        String resolution = getBestResolution();
        boolean autoAdjust = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.HDMI_AUTO_ADJUST, 0) != 0;

        if (!autoAdjust) {
            // auto adjust not enabled so lets try to read user selected resolution
            // if not available fall back to 720p
            String userResolution = Settings.Secure.getString(mContext.getContentResolver(),
                    Settings.Secure.HDMI_RESOLUTION);
            resolution = (isResolutionAvailable(userResolution) ? userResolution : "720p");
        }
        return resolution;
    }

    /**
     * Check Settings.Secure to see if height and width have been compensated
     * @return true if any side has a compensation adjustment; false otherwise
     */
    private boolean isCompensated() {
        int left = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_LEFT, 100);
        int top = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_LEFT, 100);
        int right = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_RIGHT, 100);
        int bottom = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_BOTTOM, 100);
        boolean leftOffset = left != 100;
        boolean topOffset = top != 100;
        boolean rightOffset = right != 100;
        boolean bottomOffset = bottom != 100;

        if (leftOffset) {
            Log.d(TAG, "Overscan is compensated for left. Adjusting back to " + left);
        }
        if (topOffset) {
            Log.d(TAG, "Overscan is compensated for top. Adjusting back to " + top);
        }

        if (rightOffset) {
            Log.d(TAG, "Overscan is compensated for right. Adjusting back to " + right);
        }
        if (bottomOffset) {
            Log.d(TAG, "Overscan is compensated for bottom. Adjusting back to " + bottom);
         }

        return (leftOffset || topOffset || rightOffset || bottomOffset) ? true: false;
    }

    /**
     * Update position with compensation if isCompensated()
     */
    public void syncCompensation() {
        int [] position = getPosition(getResolution());
        setPosition(position[0], position[1], position[2], position[3]);
        savePosition(position[0], position[1], position[2], position[3]);
    }

    /**
     * Sets the display position from the provided values and writes them to PPSCALER_RECT
     * @param left   x value
     * @param top    y value
     * @param right  width
     * @param bottom height
     */
    public void setPosition(int left, int top, int right, int bottom) {
        String position = String.valueOf(left) + " " + String.valueOf(top) + " "
                        + String.valueOf(right) + " " + String.valueOf(bottom) + " 0";
        String windowAxis = String.valueOf(left) + " " + String.valueOf(top) + " "
                        + (right - 1) + " " +(bottom - 1);
        if (isRealOutputMode()) {
            setWindowAxis(windowAxis);
            setFreescaleFb0("0x10001");
        } else {
            setPpscalerRect(position);
            updateFreescale("1");
        }
    }

    /**
     * Save the display position handed to the API
     * to the corresponding resolution uboot props from getResolution()
     * @param left   x value
     * @param top    y value
     * @param right  width
     * @param bottom height
     */
    public void savePosition(int left, int top, int right, int bottom) {
        String position = getResolution();
        if (position.equals("480i") || position.equals("480cvbs")) {
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("480p")) {
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("576i") || position.equals("576cvbs")) {
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("576p")) {
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.contains("1080i")) {
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.contains("1080p")) {
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("4k2k24hz")) {
            mSystemWriteManager.setProperty(UBOOT_4K2K24HZ_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_4K2K24HZ_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_4K2K24HZ_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_4K2K24HZ_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("4k2k25hz")) {
            mSystemWriteManager.setProperty(UBOOT_4K2K25HZ_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_4K2K25HZ_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_4K2K25HZ_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_4K2K25HZ_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("4k2k30hz")) {
            mSystemWriteManager.setProperty(UBOOT_4K2K30HZ_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_4K2K30HZ_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_4K2K30HZ_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_4K2K30HZ_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("4k2ksmpte")) {
            mSystemWriteManager.setProperty(UBOOT_4K2KSMPTE_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_4K2KSMPTE_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_4K2KSMPTE_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_4K2KSMPTE_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else {
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_HEIGHT, String.valueOf(bottom));
        }

        if (isRealOutputMode()) {
            blankDisplay(true);
            if ((position.contains("720") || position.contains("1080"))
                    && left == 0 && top == 0) {
                setFreescaleFb0("0x0");
            }

            String output = String.valueOf(left) + " " + String.valueOf(top) + getDisplayAxisByMode(position)
                    + String.valueOf(left) + " " + String.valueOf(top) + " " + 18 + " " + 18;
            String video = String.valueOf(left) + " " + String.valueOf(left) + " " + (left + right - 1)
                    + " " + (top + bottom -1);
            setOutputAxis(output);
            setVideoAxis(video);
            blankDisplay(false);
        }
    }    

    /**
     * Reset the display position to 100% for the resolution
     */
   public void resetPosition() {
        // reset position to 100% of current resolution
        int[] position = getResolutionPosition();
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_LEFT, 100);
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_TOP, 100);
        Settings.Secure.putInt(mContext.getContentResolver(),
            Settings.Secure.HDMI_OVERSCAN_RIGHT, 100);
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_BOTTOM, 100);

        setPosition(position[0], position[1], position[2], position[3]);
        savePosition(position[0], position[1], position[2], position[3]);
    }

    /**
     * Gets the full width position for the current resolution
     * @return position[2]  The width position value
     */
    public int getFullWidthPosition() {
        int[] position = getResolutionPosition();
        return position[2];
    }

    /**
     * Gets the full height position for the current resolution
     * @return position[3]  The height position value
     */
    public int getFullHeightPosition() {
        int[] position = getResolutionPosition();
        return position[3];
    }

    /**
     * Get the display position from the passed resolution mode
     * @param mode  Desired resolution that a position is requested for
     * @return      int array position based on resolution passed
     */
    public static int[] getPosition(String mode) {
        int[] currentPosition = { 0, 0, 1280, 720 };
        int index = 4; // 720p
        for (int i = 0; i < COMMON_MODE_VALUE_LIST.length; i++) {
            if (mode.equalsIgnoreCase(COMMON_MODE_VALUE_LIST[i]))
                index = i;
        }
        switch (index) {
            case 0: // 480i
            case 10:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_WIDTH, OUTPUT480_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_HEIGHT, OUTPUT480_FULL_HEIGHT);
                break;
            case 1: // 480p
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_WIDTH, OUTPUT480_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_HEIGHT, OUTPUT480_FULL_HEIGHT);
                break;
            case 2: // 576i
            case 11:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_WIDTH, OUTPUT576_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_HEIGHT, OUTPUT576_FULL_HEIGHT);
                break;
            case 3: // 576p
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_WIDTH, OUTPUT576_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_HEIGHT, OUTPUT576_FULL_HEIGHT);
                break;
            case 4: // 720p
            case 7: // 720p 50Hz
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_WIDTH, OUTPUT720_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_HEIGHT, OUTPUT720_FULL_HEIGHT);
                break;
            case 5: // 1080i
            case 8: // 1080i 50Hz
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_WIDTH, OUTPUT1080_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_HEIGHT, OUTPUT1080_FULL_HEIGHT);
                break;
            case 6: // 1080p
            case 9: // 1080p 50Hz
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_WIDTH, OUTPUT1080_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_HEIGHT, OUTPUT1080_FULL_HEIGHT);
                break;            
            case 12: // 4k2k 24Hz
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K24HZ_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K24HZ_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K24HZ_OUTPUT_WIDTH, OUTPUT4K2K_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K24HZ_OUTPUT_HEIGHT, OUTPUT4K2K_FULL_HEIGHT);
                break;
            case 13: // 4k2k 25Hz
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K25HZ_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K25HZ_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K25HZ_OUTPUT_WIDTH, OUTPUT4K2K_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K25HZ_OUTPUT_HEIGHT, OUTPUT4K2K_FULL_HEIGHT);
                break;
            case 14: // 4k2k 30Hz
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K30HZ_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K30HZ_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K30HZ_OUTPUT_WIDTH, OUTPUT4K2K_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_4K2K30HZ_OUTPUT_HEIGHT, OUTPUT4K2K_FULL_HEIGHT);
                break;
            case 15: // 4k2k smpte
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_4K2KSMPTE_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_4K2KSMPTE_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_4K2KSMPTE_OUTPUT_WIDTH, OUTPUT4K2KSMPTE_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_4K2KSMPTE_OUTPUT_HEIGHT, OUTPUT4K2KSMPTE_FULL_HEIGHT);
            default:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_WIDTH, OUTPUT720_FULL_WIDTH);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_HEIGHT, OUTPUT720_FULL_HEIGHT);
                break;
        }
        if (DEBUG) Log.d(TAG, "getPosition says position is " + currentPosition[0] + " " + currentPosition[1] + " " + currentPosition[2] + " " + currentPosition[3]);
        return currentPosition;
    }

    /**
     * Sets the desired output mode if freescale is closed
     * @param newMode Resolution desired
     */
    public void setOutputWithoutFreescale(String newMode) {
        if (DEBUG) Log.d(TAG, "Setting " + newMode + " as output without freescale");
        if (newMode.contains("cvbs")) {
            openVdac(newMode);
        } else {
            closeVdac(newMode);
        }        

        blankDisplay(true);
        setPpscalerRect("0");
        setFreescaleFb0("0");
        setFreescaleFb1("0");
        setDisplayMode(newMode);
        setCommonMode(newMode);
        saveNewModeToProp(newMode);

        int[] currentPosition = {0, 0, 1280, 720};

        if (isRealOutputMode()) {
            setDensity(newMode);
            if (newMode.contains("1080") || newMode.contains("4k2k")) {
                setDisplaySize(1920, 1080);
            } else {
                setDisplaySize(1280, 720);
            }

            String displayValue = currentPosition[0] + " " + currentPosition[1] + " " +
                    1920 + " " + 1080 + " " + currentPosition[0] + " " + currentPosition[1] + " " + 18 + " " + 18;
            setOutputAxis(displayValue);
        } else {
            currentPosition = getPosition(newMode);
            if ((newMode.equals(COMMON_MODE_VALUE_LIST[5])) || (newMode.equals(COMMON_MODE_VALUE_LIST[6]))
                    || (newMode.equals(COMMON_MODE_VALUE_LIST[8])) || (newMode.equals(COMMON_MODE_VALUE_LIST[9]))) {
                setOutputAxis(((int)(currentPosition[0]/2))*2 + " " + ((int)(currentPosition[1]/2))*2
                        + " 1280 720 " + ((int)(currentPosition[0]/2))*2 + " " + ((int)(currentPosition[1]/2))*2 + " 18 18");
                setOSD0ScaleAxis("0 0 " + (960 - (int)(currentPosition[0]/2) - 1) + " " + (1080 - (int)(currentPosition[1]/2) - 1));
                setRequestScale("7 " + ((int)(currentPosition[2]/2)) + " " + ((int)(currentPosition[3]/2))*2);
                setOSD1ScaleAxis("1280 720 " + ((int)(currentPosition[2]/2))*2 + " " + ((int)(currentPosition[3]/2))*2);
                setOSD1Scale("0x10001");
            } else {
                setOutputAxis(currentPosition[0] + " " + currentPosition[1]
                         + " 1280 720 " + currentPosition[0] + " " + currentPosition[1] + " 18 18");
                setRequestScale("16 " + currentPosition[2] + " "+ currentPosition[3]);
                setOSD1ScaleAxis("1280 720 " + currentPosition[2] + " " + currentPosition[3]);
                setOSD1Scale("0x10001");
            }
            setVideoAxis(currentPosition[0] + " " + currentPosition[1] + " "
                    + (currentPosition[2] + currentPosition[0] - 1) + " " + (currentPosition[3] + currentPosition[1] - 1));
        }
    }    

    /**
     * Sets the desired output mode if freescale is open
     * @param newMode desired resolution
     */
    public void setOutputMode(String newMode) {
        String currentMode = getResolution();
        if (newMode.equals(currentMode)) {
            // 'tis the same, so go home
            if (DEBUG) Log.d(TAG, "newMode=" + newMode + " == currentMode=" + currentMode);
            return;
        }
        Log.d(TAG, "Setting " + newMode + " as output");

        if (newMode.contains("cvbs")) {
            openVdac(newMode);
        } else {
            closeVdac(newMode);
        }

        int[] currentPosition = getPosition(newMode);

        String windowAxis = currentPosition[0] + " " + currentPosition[1]
                + " " + (currentPosition[2] + currentPosition[0] - 1)
                + " " + (currentPosition[3] + currentPosition[1] - 1);
        String videoAxis = currentPosition[0] + " " + currentPosition[1]
                + " " + (currentPosition[2] + currentPosition[0] - 1)
                + " " + (currentPosition[3] + currentPosition[1] - 1);
        String displayAxis = currentPosition[0] + " " + currentPosition[1]
                + getDisplayAxisByMode(newMode)+ currentPosition[0] + " "
                + currentPosition[1] + " " + 18 + " " + 18;
        String ppScalerRect = currentPosition[0] + " " + currentPosition[1]
                + " " + (currentPosition[2] + currentPosition[0])
                + " " + (currentPosition[3] + currentPosition[1]) + " " + 0;


        if (isRealOutputMode()) {
            blankDisplay(true);
            // close freescale
            setFreescaleFb0("0");
            if (newMode.contains("4k2k")) {
                // set to 1080p as base
                setDensity(newMode);
                setDisplaySize(OUTPUT1080_FULL_WIDTH, OUTPUT1080_FULL_HEIGHT);

                // open freescale and scale up to 4k
                setFreescaleMode("1");
                setFreescaleAxis(newMode);
                setWindowAxis(windowAxis);
                setFreescaleFb0("0x10001");
            } else if (newMode.contains("1080")) {
                setDensity(newMode);
                setDisplaySize(OUTPUT1080_FULL_WIDTH, OUTPUT1080_FULL_HEIGHT);
                setFreescaleMode("1");
                setFreescaleAxis(newMode);
                setWindowAxis(windowAxis);
                if (currentPosition[0] == 0 && currentPosition[1] == 0) {
                    setFreescaleFb0("0");
                } else {
                    setFreescaleFb0("0x10001");
                }
            } else if (newMode.contains("720")) {
                setDensity(newMode);
                setDisplaySize(OUTPUT720_FULL_WIDTH, OUTPUT720_FULL_HEIGHT);
                setFreescaleMode("1");
                setFreescaleAxis(newMode);
                setWindowAxis(windowAxis);
                if (currentPosition[0] == 0 && currentPosition[1] == 0) {
                    setFreescaleFb0("0");
                } else {
                    setFreescaleFb0("0x10001");
                }
            } else if (newMode.contains("576") || newMode.contains("480")) {
                setDensity(newMode);
                if (newMode.contains("576")) {
                    setDisplaySize(OUTPUT576_FULL_WIDTH, OUTPUT576_FULL_HEIGHT);
                } else {
                    setDisplaySize(OUTPUT480_FULL_WIDTH, OUTPUT480_FULL_HEIGHT);
                }
                setFreescaleMode("1");
                setFreescaleAxis(newMode);
                setWindowAxis(windowAxis);
                setFreescaleFb0("0x10001");
            }

            setVideoAxis(videoAxis);
            setOutputAxis(displayAxis);
        } else {
            setFreescaleAxis(newMode);
            setDisplayMode(newMode);
            setPpscalerRect(ppScalerRect);
            updateFreescale("1");
            setWindowAxis(windowAxis);
        }

        setCommonMode(newMode);
        saveNewModeToProp(newMode);
    }

    public boolean isRealOutputMode() {
        boolean isReal = mSystemWriteManager.getPropertyBoolean(REAL_OUTPUT_MODE, false);
        Log.d(TAG, "isRealOutputMode=" + isReal);
        return isReal;
    }

    public boolean isHdmiOnly() {
        boolean isHdmiOnly = mSystemWriteManager.getPropertyBoolean(HDMIONLY, true);
        Log.d(TAG, "isHdmiOnly=" + isHdmiOnly);
        return isHdmiOnly;
    }

    public void blankDisplay(boolean blank) {
        if (blank) {
            mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "1");
        } else {
            mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "0");
        }
    }

    public void setFreescaleFb0(String bit) {
        mSystemWriteManager.writeSysfs(FREESCALE_FB0, bit);
    }

    public void setFreescaleFb1(String bit) {
            mSystemWriteManager.writeSysfs(FREESCALE_FB1, bit);
        }

    public void setFreescaleMode(String mode) {
        mSystemWriteManager.writeSysfs(FREESCALE_MODE, mode);
    }

    public void setCommonMode(String mode) {
        mSystemWriteManager.setProperty(UBOOT_COMMONMODE, mode);
    }

    public void setOutputAxis(String output) {
        mSystemWriteManager.writeSysfs(OUTPUT_AXIS, output);
    }
    public void setVideoAxis(String video) {
        mSystemWriteManager.writeSysfs(VIDEO_AXIS, video);
    }

    public void setDisplayMode(String mode) {
        mSystemWriteManager.writeSysfs(DISPLAY_MODE, mode);
    }

    public void setPpscalerRect(String rect) {
        mSystemWriteManager.writeSysfs(PPSCALER_RECT, rect);
    }
    public void updateFreescale(String value) {
        mSystemWriteManager.writeSysfs(UPDATE_FREESCALE, value);
    }
    public void setWindowAxis(String window) {
        mSystemWriteManager.writeSysfs(WINDOW_AXIS, window);
    }

    public void setOSD0ScaleAxis(String axis) {
        mSystemWriteManager.writeSysfs(SCALE_AXIS_OSD0, axis);
    }
    public void setOSD1ScaleAxis(String axis) {
        mSystemWriteManager.writeSysfs(SCALE_AXIS_OSD1, axis);
    }
    public void setRequestScale(String scale) {
        mSystemWriteManager.writeSysfs(REQUEST_2X_SCALE, scale);
    }
    public void setOSD1Scale(String scale) {
        mSystemWriteManager.writeSysfs(SCALE_OSD1, scale);
    }

    public void setCvbsMode(String mode) {
        mSystemWriteManager.setProperty(UBOOT_CVBSMODE, mode);
    }

    public void setHdmiMode(String mode) {
        mSystemWriteManager.setProperty(UBOOT_HDMIMODE, mode);
    }

    public String getDisplayAxisByMode(String newMode) {
        if (newMode.indexOf("1080") >= 0) {
            return DISPLAY_AXIS_1080;
        } else if (newMode.indexOf("720") >= 0) {
            return DISPLAY_AXIS_720;
        } else if (newMode.indexOf("576") >= 0) {
            return DISPLAY_AXIS_576;
        } else {
            return DISPLAY_AXIS_480;
        }
    }

    public void setFreescaleAxis(String newMode) {
        String axis = "0 0 1279 719";
        if (newMode.contains("4k2k") || newMode.contains("1080")) {
            axis = "0 0 1919 1079";
        } else if (newMode.equals("576i") || newMode.equals("480i")) {
            axis = "0 0 1279 721";
        }    
        mSystemWriteManager.writeSysfs(FREESCALE_AXIS, axis);
        setFreescaleFb0("1");
    }

    public void saveNewModeToProp(String newMode) {
        if (newMode != null) {
            if (newMode.contains("cvbs")) {
                setCvbsMode(newMode);
            } else {
                setHdmiMode(newMode);
            }
        }
    }

    public void setDensity(String newMode) {
        int density = 240;
        if (newMode.contains("720") || newMode.contains("480")
                || newMode.contains("576")) {
            density = 160;
        }

        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));
        if (wm != null) {
            try {
                if (density > 0) {
                    wm.setForcedDisplayDensity(Display.DEFAULT_DISPLAY, density);
                } else {
                    wm.clearForcedDisplayDensity(Display.DEFAULT_DISPLAY);
                }
            } catch (RemoteException ignored) {}
        } else {
            Log.d(TAG, "Can't connect to window manager; is the system running?");
        }
    }

    public void setDisplaySize(int w, int h) {
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                Context.WINDOW_SERVICE));

        if (wm != null) {
            try {
                if (w >= 0 && h >= 0) {
                    wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, w, h);
                } else {
                    wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
                }
            } catch (RemoteException ignored) {}
        } else {
            Log.d(TAG, "Can't connect to window manager; is the system running?");
        }
    }

    public void setDisplaySize(String width, String height) {
        int w = Integer.parseInt(width);
        int h = Integer.parseInt(height);
        setDisplaySize(w, h);
    }

    /**
     * Reads HDMI_SUPPORT_LIST and returns the best possible resolution
     * @return best possible resolution the TV allows which is provided from EDID
     */
    public String getBestResolution() {
        String[] resolutions = null;
        String resolution = "720p";
        String rawList = readSupportList(HDMI_SUPPORT_LIST);
        if (getResolution().contains("cvbs")) {
            rawList = CVBS_SUPPORT_LIST[0] + "|" + CVBS_SUPPORT_LIST[1];
        }
        if (DEBUG) Log.d(TAG, "Raw supported resolutions: " + rawList);
        if (rawList != null) {
            resolutions = rawList.split("\\|");
        }
        if (resolutions != null) {
            if (getResolution().contains("cvbs")) {
                resolution = resolutions[resolutions.length-1]; // 576cvbs
            } else {
                for (int i = 0; i < resolutions.length; i++) {
                    if (DEBUG) Log.d(TAG, "checking " + resolutions[i]);
                    if (resolutions[i].contains("*")) {
                        if (DEBUG) Log.d(TAG, "* found - setting as bestResolution");
                        // best mode
                        String res = resolutions[i];
                        resolution = res.substring(0, res.length()-1);
                    }
                }
            }
        }
        Log.d(TAG, "Best resolution is " + resolution);
        return resolution;
    }

    /**
     * Checks if fb0 freescale is closed
     * @return true if FREESCALE_FB0 contains 0x0
     */
    public boolean isFreescaleClosed() {
        return mSystemWriteManager.readSysfs(FREESCALE_FB0).contains("0x0");
    }

    /**
     * Check if hdmi is currently plugged in
     * @return true if hpd_state equals 1
     */
    public static boolean isHdmiPlugged() {
        return mSystemWriteManager.readSysfs("/sys/class/amhdmitx/amhdmitx0/hpd_state").equals("1");
    }

    /**
     * Set vdac closed dependent on the current mode
     * @param  mode resolution
     */
    public void closeVdac(String mode) {
        if (isHdmiOnly()) {
            if (!mode.contains("cvbs")) {
                mSystemWriteManager.writeSysfs(HDMI_PLUGGED, "vdac");
            }
        }
    }

    public void openVdac(String mode){
        if(isHdmiOnly()) {
            if(mode.contains("cvbs")) {
                mSystemWriteManager.writeSysfs(HDMI_UNPLUGGED,"vdac");
            }
        }
    }

    /**
     * Sets the digital audio value based on the provided value
     * @param value  the value corresponding to the audio route
     *               0 - PCM
     *               1 - RAW/SPDIF passthrough
     *               2 - HDMI passthrough
     */
    public void setDigitalAudioValue(String value) {
        mSystemWriteManager.setProperty(UBOOT_DIGITAL_AUDIO_OUTPUT, value);
        mSystemWriteManager.writeSysfs(AUDIODSP_DIGITAL_RAW, value);
    }

    /**
     * Reads the HDMI support list
     * @param path  HDMI support list path
     * @return      "|" delimited String of supported resolutions
     */
    private String readSupportList(String path) {
        String str = null;
        StringBuilder builder = new StringBuilder();
        try {
            FileReader reader = new FileReader(path);
            BufferedReader buffer = new BufferedReader(reader);
            try {
                while ((str = buffer.readLine()) != null) {
                    if (str != null) {
                        builder.append(str);
                        builder.append("|");
                    }
                };
            } catch (IOException io) {
                Log.e(TAG, "Gathering support list failed", io);
                return null;
            } finally {
                if (buffer != null) {
                    try {
                        buffer.close();
                    } catch (IOException io) { return null;}
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException io) { return null;}
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Support list not found", e);
            return null;
        }
        return builder.toString();
    }
}
