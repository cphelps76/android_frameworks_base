package android.hardware.display;

interface IHdmiManager {

    void closeVdac(String mode);
    String getResolution();
    String[] getAvailableResolutions();
    int[] getResolutionPosition();
    String getRequestedResolution();
    String getBestResolution();
    void hdmiPlugged();
    void hdmiUnplugged();
    void syncCompensation();
    boolean isFreescaleClosed();
    boolean isHdmiPlugged();
    void setOutputMode(String newMode);
    void setOutputWithoutFreescale(String newMode);
    int[] getPosition(String mode);
    void setPosition(int left, int top, int right, int bottom);
    void savePosition(int left, int top, int right, int bottom);
    void resetPosition();
    int getFullWidthPosition();
    int getFullHeightPosition();
    void setDigitalAudioValue(String value);
    String readSupportList(String path);
}
