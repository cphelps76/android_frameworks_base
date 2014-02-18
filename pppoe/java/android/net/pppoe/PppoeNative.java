package android.net.pppoe;

/**
 * Native calls to pppoe interface.
 *
 * {@hide}
 */

public class PppoeNative {
	public native static String getInterfaceName(int i);
	public native static int getInterfaceCnt();
	public native static int initPppoeNative();
	public native static String waitForEvent();
	public native static int isInterfaceAdded(String ifname);
}
