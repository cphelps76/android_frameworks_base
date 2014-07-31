package android.hardware.display;

import android.app.SystemWriteManager;
import android.content.Context;
import android.util.Log;

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
    public static final String SCALE_OSD1 = "/sys/class/graphics/fb1/scale";
    public static final String OUTPUT_AXIS = "/sys/class/display/axis";

    public static final String HDMIONLY_PROP = "ro.platform.hdmionly";
    public static final String UBOOT_CVBSMODE = "ubootenv.var.cvbsmode";
    public static final String UBOOT_COMMONMODE = "ubootenv.var.commonmode";

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
    // 576p values
    public static final int OUTPUT576_FULL_WIDTH = 720;
    public static final int OUTPUT576_FULL_HEIGHT = 576;
    // 720p values
    public static final int OUTPUT720_FULL_WIDTH = 1280;
    public static final int OUTPUT720_FULL_HEIGHT = 720;
    // 1080p values
    public static final int OUTPUT1080_FULL_WIDTH = 1920;
    public static final int OUTPUT1080_FULL_HEIGHT = 1080;

    private Context mContext;
    private static SystemWriteManager mSystemWriteManager;

    public HdmiManager(Context context) {
        mContext = context;
        mSystemWriteManager = (SystemWriteManager) context.getSystemService("system_write");
    }

    public void hdmiUnplugged() {
        Log.d(TAG, "HDMI unplugged");
        if (mSystemWriteManager.getPropertyBoolean(HDMIONLY_PROP, true)) {
            String cvbsMode = mSystemWriteManager.getPropertyString(UBOOT_CVBSMODE, "480cvbs");
            if (isFreescaleClosed()) {
                setOutputWithoutFreescale(cvbsMode);
            } else {
                setOutputMode(cvbsMode);
            }
            mSystemWriteManager.writeSysfs(HDMI_UNPLUGGED, "vdac"); //open vdac
            mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "0");
        }
    }

    public void hdmiPlugged() {
        Log.d(TAG, "HDMI plugged");
        if (mSystemWriteManager.getPropertyBoolean(HDMIONLY_PROP, true)) {
            mSystemWriteManager.writeSysfs(HDMI_PLUGGED, "vdac");
            if (isFreescaleClosed()) {
                Log.d(TAG, "Freescale is closed");
                setOutputWithoutFreescale(getBestResolution());
            } else {
                Log.d(TAG, "Freescale is open");
                setOutputMode(getBestResolution());
            }
            mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "0");
        }
    }

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

    public void setOutputWithoutFreescale(String newMode) {
        mSystemWriteManager.writeSysfs(BLANK_DISPLAY, "1");
        mSystemWriteManager.writeSysfs(PPSCALER_RECT, "0");
        mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0");
        mSystemWriteManager.writeSysfs(FREESCALE_FB1, "0");
        mSystemWriteManager.writeSysfs(DISPLAY_MODE, newMode);
        mSystemWriteManager.setProperty(UBOOT_COMMONMODE, newMode);
        int[] currentPosition = {0, 0, 1280, 720};
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

    public void setOutputMode(String newMode) {
        String currentMode = mSystemWriteManager.readSysfs(DISPLAY_MODE);
        if (newMode.equals(currentMode)) {
            // 'tis the same, so go home
            Log.d(TAG, "newMode=" + newMode + " == currentMode=" + currentMode);
            return;
        }
        mSystemWriteManager.writeSysfs(DISPLAY_MODE, newMode);
        int[] currentPosition = getPosition(newMode);
        String value = currentPosition[0] + " " + currentPosition[1]
                + " " + (currentPosition[2] + currentPosition[0] - 1)
                + " " + (currentPosition[3] + currentPosition[1] - 1)
                + " " + 0;
        mSystemWriteManager.writeSysfs(PPSCALER_RECT, value);
        mSystemWriteManager.writeSysfs(FREESCALE_FB0, "0");
        mSystemWriteManager.writeSysfs(FREESCALE_FB1, "0");
        mSystemWriteManager.writeSysfs(FREESCALE_FB0, "1");
        mSystemWriteManager.writeSysfs(FREESCALE_FB1, "1");

        closeVdac(newMode);
        mSystemWriteManager.setProperty(UBOOT_COMMONMODE, newMode);
    }

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

    public boolean isFreescaleClosed() {
        return mSystemWriteManager.readSysfs(FREESCALE_FB0).contains("0x0");
    }

    public static boolean isHdmiPlugged() {
        return mSystemWriteManager.readSysfs("/sys/class/amhdmitx/amhdmitx0/hpd_state").equals("1");
    }

    public void closeVdac(String mode) {
        if (mSystemWriteManager.getPropertyBoolean(HDMIONLY_PROP, false)) {
            if (!mode.contains("cvbs")) {
                mSystemWriteManager.writeSysfs(HDMI_PLUGGED, "vdac");
            }
        }
    }

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
