package android.hardware.display;

interface IHdmiManager {

    void closeVdac(String mode);
    String getBestResolution();
    void hdmiPlugged();
    void hdmiUnplugged();
    boolean isFreescaleClosed();
    boolean isHdmiPlugged();
    void setOutputMode(String newMode);
    void setOutputWithoutFreescale(String newMode);
    int[] getPosition(String mode);
    String readSupportList(String path);
}
