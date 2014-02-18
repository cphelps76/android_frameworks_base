package android.net.pppoe;
import android.net.DhcpInfo;
import android.net.pppoe.PppoeDevInfo;

interface IPppoeManager
{
	String[] getDeviceNameList();
	void setPppoeState(int state);
	int getPppoeState( );
	void UpdatePppoeDevInfo(in PppoeDevInfo info);
	boolean isPppoeConfigured();
	PppoeDevInfo getSavedPppoeConfig();
	int getTotalInterface();
	void setPppoeMode(String mode);
	boolean isPppoeDeviceUp();
	DhcpInfo getDhcpInfo();
}
