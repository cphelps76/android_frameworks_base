package android.hardware.display;

import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;

public final class HdmiManagerGlobal {

    private static HdmiManagerGlobal sInstance;
    private final IHdmiManager mHm;

    private HdmiManagerGlobal(IHdmiManager hdmi) {
        mHm = hdmi;
    }

    /**
     * Gets an instance of the hdmi manager.
     * @return The hdmi manager instance.
     */
    public static HdmiManagerGlobal getInstance() {
        synchronized (HdmiManager.class) {
            if (sInstance == null) {
                IBinder b = ServiceManager.getService(Context.HDMI_SERVICE);
                if (b != null) {
                    sInstance = new HdmiManagerGlobal(IHdmiManager.Stub.asInterface(b));
                }
            }
            return sInstance;
        }
    }
}
