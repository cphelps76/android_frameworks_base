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

    public static final String[] COMMON_MODE_VALUE_LIST =  {
            "480i", "480p", "576i", "576p", "720p", "1080i", "1080p",
            "720p50hz", "1080i50hz", "1080p50hz", "480cvbs", "576cvbs"
    };

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
        String cvbsMode = mSystemWriteManager.getPropertyString(UBOOT_CVBSMODE, "480cvbs");
        if (mSystemWriteManager.getPropertyBoolean(REAL_OUTPUT_MODE, false)) {
            if (mSystemWriteManager.getPropertyBoolean(HDMIONLY, true)) {
                setOutputMode(cvbsMode);
                mSystemWriteManager.writeSysfs(HDMI_UNPLUGGED, "vdac"); //open vdac
            }
        } else {
            if (mSystemWriteManager.getPropertyBoolean(HDMIONLY, true)) {
                if (isFreescaleClosed()) {
                    setOutputWithoutFreescale(cvbsMode);
                } else {
                    setOutputMode(cvbsMode);
                }
                mSystemWriteManager.writeSysfs(HDMI_UNPLUGGED, "vdac"); //open vdac
                mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "0");
            }
        }
    }

    /**
     * Method called when HDMI state changes as a result of being plugged
     * Also called on boot when HDMI is detected
     * If compensation has been adjusted, this is adjusted as well
     */
    public void hdmiPlugged() {
        Log.d(TAG, "HDMI plugged");
        if (mSystemWriteManager.getPropertyBoolean(REAL_OUTPUT_MODE, false)) {
            if (mSystemWriteManager.getPropertyBoolean(HDMIONLY, true)) {
                mSystemWriteManager.writeSysfs(HDMI_PLUGGED, "vdac");
                setOutputMode(getRequestedResolution());
            }
        } else if (mSystemWriteManager.getPropertyBoolean(HDMIONLY, true)) {
            mSystemWriteManager.writeSysfs(HDMI_PLUGGED, "vdac");
            String resolution = getRequestedResolution();

            if (isFreescaleClosed()) {
                Log.d(TAG, "Freescale is closed");
                setOutputWithoutFreescale(resolution);
            } else {
                Log.d(TAG, "Freescale is open");
                setOutputMode(resolution);
            }
            mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "0");
        }
        if (isCompensated()) syncCompensation();
    }

    /**
     * Read #DISPLAY_MODE from sysfs and return the current resolution
     * @return current HDMI resolution
     */
    public String getResolution() {
        Log.d(TAG, "Current resolution is " + mSystemWriteManager.readSysfs(DISPLAY_MODE));
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
        }
        return position;
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
            resolution = (userResolution != null ? userResolution : "720p");
        }
        return resolution;
    }

    /**
     * Check Settings.Secure to see if height and width have been compensated
     * @return true if width != 100 && height  != 100; else false
     */
    private boolean isCompensated() {
        int width = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_WIDTH, 100);
        int height = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_HEIGHT, 100);
        boolean widthOffset = width != 100;
        boolean heightOffset = height != 100;

        if (widthOffset) {
            Log.d(TAG, "Overscan is compensated for width. Adjusting back to " + width);
        }
        if (heightOffset) {
            Log.d(TAG, "Overscan is compensated for height. Adjusting back to " + height);
        }

        return (widthOffset || heightOffset) ? true: false;
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
       mSystemWriteManager.writeSysfs(PPSCALER_RECT, position);
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
        if (position.equals("480i")) {
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_480I_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("480p")) {
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_480P_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("576i")) {
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_576I_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.equals("576p")) {
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_576P_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.contains("1080I")) {
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_1080I_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else if (position.contains("1080p")) {
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_1080P_OUTPUT_HEIGHT, String.valueOf(bottom));
        } else {
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_X, String.valueOf(left));
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_Y, String.valueOf(top));
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_WIDTH, String.valueOf(right));
            mSystemWriteManager.setProperty(UBOOT_720P_OUTPUT_HEIGHT, String.valueOf(bottom));
        }
    }

    /**
     * Reset the display position to 100% for the resolution
     */
   public void resetPosition() {
        // reset position to 100% of current resolution
        int[] position = getResolutionPosition();
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_WIDTH, 100);
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.HDMI_OVERSCAN_HEIGHT, 100);

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
            case 0:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_WIDTH, 720);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_HEIGHT, 480);
                break;
            case 1:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_WIDTH, 720);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_480P_OUTPUT_HEIGHT, 480);
                break;
            case 2:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_WIDTH, 720);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_HEIGHT, 576);
                break;
            case 3:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_WIDTH, 720);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_576P_OUTPUT_HEIGHT, 576);
                break;
            case 4:
            case 7:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_WIDTH, 1280);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_HEIGHT, 720);
                break;
            case 5:
            case 8:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_WIDTH, 1920);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_1080I_OUTPUT_HEIGHT, 1080);
                break;
            case 6:
            case 9:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_WIDTH, 1920);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_1080P_OUTPUT_HEIGHT, 1080);
                break;
            case 10:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_WIDTH, 720);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_480I_OUTPUT_HEIGHT, 480);
                break;
            case 11:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_WIDTH, 720);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_576I_OUTPUT_HEIGHT, 576);
                break;
            default:
                currentPosition[0] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_X, 0);
                currentPosition[1] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_Y, 0);
                currentPosition[2] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_WIDTH, 1280);
                currentPosition[3] = mSystemWriteManager.getPropertyInt(UBOOT_720P_OUTPUT_HEIGHT, 720);
                break;
        }
        Log.d(TAG, "getPosition says position is " + currentPosition[0] + " " + currentPosition[1] + " " + currentPosition[2] + " " + currentPosition[3]);
        return currentPosition;
    }

    /**
     * Sets the desired output mode if freescale is closed
     * @param newMode Resolution desired
     */
    public void setOutputWithoutFreescale(String newMode) {
        if (newMode.contains("cvbs")) {
            openVdac(newMode);
        } else {
            closeVdac(newMode);
        }

        mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "1");
        mSystemWriteManager.writeSysfs(PPSCALER_RECT, "0");
        mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0");
        mSystemWriteManager.writeSysfs(FREESCALE_FB1, "0");
        mSystemWriteManager.writeSysfs(DISPLAY_MODE, newMode);
        mSystemWriteManager.setProperty(UBOOT_COMMONMODE, newMode);
        saveNewModeToProp(newMode);

        int[] currentPosition = {0, 0, 1280, 720};

        if (mSystemWriteManager.getPropertyBoolean(REAL_OUTPUT_MODE, false)) {
            setDensity(newMode);
            if (newMode.contains("1080")) {
                setDisplaySize(1920, 1080);
            } else {
                setDisplaySize(1280, 720);
            }

            String displayValue = currentPosition[0] + " " + currentPosition[1] + " " +
                    1920 + " " + 1080 + " " + currentPosition[0] + " " + currentPosition[1] + " " + 18 + " " + 18;
            mSystemWriteManager.writeSysfs(OUTPUT_AXIS, displayValue);
        } else {
            currentPosition = getPosition(newMode);
            if ((newMode.equals(COMMON_MODE_VALUE_LIST[5])) || (newMode.equals(COMMON_MODE_VALUE_LIST[6]))
                    || (newMode.equals(COMMON_MODE_VALUE_LIST[8])) || (newMode.equals(COMMON_MODE_VALUE_LIST[9]))) {
                mSystemWriteManager.writeSysfs(OUTPUT_AXIS, ((int)(currentPosition[0]/2))*2 + " " + ((int)(currentPosition[1]/2))*2
                        + " 1280 720 " + ((int)(currentPosition[0]/2))*2 + " " + ((int)(currentPosition[1]/2))*2 + " 18 18");
                mSystemWriteManager.writeSysfs(SCALE_AXIS_OSD0, "0 0 " + (960 - (int)(currentPosition[0]/2) - 1) + " " + (1080 - (int)(currentPosition[1]/2) - 1));
                mSystemWriteManager.writeSysfs(REQUEST_2X_SCALE, "7 " + ((int)(currentPosition[2]/2)) + " " + ((int)(currentPosition[3]/2))*2);
                mSystemWriteManager.writeSysfs(SCALE_AXIS_OSD1, "1280 720 " + ((int)(currentPosition[2]/2))*2 + " " + ((int)(currentPosition[3]/2))*2);
                mSystemWriteManager.writeSysfs(SCALE_OSD1, "0x10001");
            } else {
                mSystemWriteManager.writeSysfs(OUTPUT_AXIS, currentPosition[0] + " " + currentPosition[1]
                         + " 1280 720 " + currentPosition[0] + " " + currentPosition[1] + " 18 18");
                mSystemWriteManager.writeSysfs(REQUEST_2X_SCALE, "16 " + currentPosition[2] + " "+ currentPosition[3]);
                mSystemWriteManager.writeSysfs(SCALE_AXIS_OSD1, "1280 720 " + currentPosition[2] + " " + currentPosition[3]);
                mSystemWriteManager.writeSysfs(SCALE_OSD1, "0x10001");
            }
            mSystemWriteManager.writeSysfs(VIDEO_AXIS, currentPosition[0] + " " + currentPosition[1] + " "
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
            Log.d(TAG, "newMode=" + newMode + " == currentMode=" + currentMode);
            return;
        }

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

        if (mSystemWriteManager.getPropertyBoolean(REAL_OUTPUT_MODE, false)) {
            mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "1");
            // close freescale
            mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0");
            if (newMode.contains("1080")) {
                setDensity(newMode);
                setDisplaySize(1920, 1080);
                mSystemWriteManager.writeSysfs(FREESCALE_MODE, "1");
                mSystemWriteManager.writeSysfs(FREESCALE_AXIS, "0 0 1919 1079");
                mSystemWriteManager.writeSysfs(WINDOW_AXIS, windowAxis);
                if (currentPosition[0] == 0 && currentPosition[1] == 0) {
                    mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0");
                } else {
                    mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0x10001");
                }
            } else if (newMode.contains("720")) {
                setDensity(newMode);
                setDisplaySize(1280, 720);
                mSystemWriteManager.writeSysfs(FREESCALE_MODE, "1");
                mSystemWriteManager.writeSysfs(FREESCALE_AXIS, "0 0 1279 719");
                mSystemWriteManager.writeSysfs(WINDOW_AXIS, windowAxis);
                if (currentPosition[0] == 0 && currentPosition[1] == 0) {
                    mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0");
                } else {
                    mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0x10001");
                }
            } else if (newMode.contains("576") || newMode.contains("480")) {
                setDensity(newMode);
                setDisplaySize(1280, 720);
                mSystemWriteManager.writeSysfs(FREESCALE_MODE, "1");
                if (newMode.equals("576i") || newMode.equals("480i")) {
                    mSystemWriteManager.writeSysfs(FREESCALE_AXIS, "0 0 1279 721");
                } else {
                    mSystemWriteManager.writeSysfs(FREESCALE_AXIS, "0 0 1279 719");
                }
                mSystemWriteManager.writeSysfs(WINDOW_AXIS, windowAxis);
                mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0x10001");
            }

            mSystemWriteManager.writeSysfs(VIDEO_AXIS, videoAxis);
            mSystemWriteManager.writeSysfs(OUTPUT_AXIS, displayAxis);
        } else {
            setFreescaleAxis(newMode);
            mSystemWriteManager.writeSysfs(DISPLAY_MODE, newMode);
            mSystemWriteManager.writeSysfs(PPSCALER_RECT, ppScalerRect);
            mSystemWriteManager.writeSysfs(UPDATE_FREESCALE, "1");
            mSystemWriteManager.writeSysfs(WINDOW_AXIS, windowAxis);
        }

        mSystemWriteManager.setProperty(UBOOT_COMMONMODE, newMode);
        saveNewModeToProp(newMode);
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
        if (newMode.contains("720") || newMode.contains("1080")) {
            mSystemWriteManager.writeSysfs(FREESCALE_AXIS, "0 0 1279 719");
        } else {
            mSystemWriteManager.writeSysfs(FREESCALE_AXIS, "0 0 1281 719");
        }
        mSystemWriteManager.writeSysfs(FREESCALE_FB0, "1");
    }

    public void saveNewModeToProp(String newMode) {
        if (newMode != null) {
            if (newMode.contains("cvbs")) {
                mSystemWriteManager.setProperty(UBOOT_CVBSMODE, newMode);
            } else {
                mSystemWriteManager.setProperty(UBOOT_HDMIMODE, newMode);
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
        Log.d(TAG, "Raw supported resolutions: " + rawList);
        if (rawList != null) {
            resolutions = rawList.split("\\|");
        }
        if (resolutions != null) {
            for (int i = 0; i < resolutions.length; i++) {
                Log.d(TAG, "checking " + resolutions[i]);
                if (resolutions[i].contains("*")) {
                    Log.d(TAG, "* found - setting as bestResolution");
                    // best mode
                    String res = resolutions[i];
                    resolution = res.substring(0, res.length()-1);
                }
            }
        }
        Log.d(TAG, "Best resolution is " + resolution);
        return resolution;
    }

    /**
     * Checks if fb0 freescaled is closed
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
        if (mSystemWriteManager.getPropertyBoolean(HDMIONLY, false)) {
            if (!mode.contains("cvbs")) {
                mSystemWriteManager.writeSysfs(HDMI_PLUGGED, "vdac");
            }
        }
    }

    public static void openVdac(String mode){
        if(mSystemWriteManager.getPropertyBoolean(HDMIONLY,false)) {
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
