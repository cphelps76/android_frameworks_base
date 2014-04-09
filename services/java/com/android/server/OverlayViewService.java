package com.android.server;

import android.content.Context;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.app.IOverlayView;

public class OverlayViewService extends IOverlayView.Stub
{
	private static final String mTag = "OverlayViewService";
	private Context mContext = null;
	
	public OverlayViewService(Context context)
	{
		mContext = context;
	}

    private boolean getOverlayViewEnable() {
        boolean ret = false;
        ret = SystemProperties.getBoolean("ro.app.overlayviewE",false);
        return ret;
    }
	
    @Override
    public void init() throws RemoteException {
        // TODO Auto-generated method stub

        if(true == getOverlayViewEnable())
            _init();
    }
	
    @Override
    public void deinit() throws RemoteException {

        if(true == getOverlayViewEnable())
            _deinit();
    }

    @Override
    public int displayHdmi() throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _displayHdmi();
        return ret;
    }

    @Override
    public int displayAndroid() throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _displayAndroid();
        return ret;
    }

    @Override
    public int displayPip(int x, int y, int width, int height) throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _displayPip(x, y, width, height);
        return ret;
    }

    @Override
    public int getHActive() throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _getHActive();
        return ret;
    }

    @Override
    public int getVActive() throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _getVActive();
        return ret;
    }

    @Override
    public boolean isDvi() throws RemoteException {
        // TODO Auto-generated method stub
        boolean ret = false;
        if(true == getOverlayViewEnable())
            ret = _isDvi();
        return ret;
    }

    @Override
    public boolean isInterlace() throws RemoteException {
        // TODO Auto-generated method stub
        boolean ret = false;
        if(true == getOverlayViewEnable())
            ret = _isInterlace();
        return ret;
    }

    @Override
    public int enableAudio(int flag) throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _enableAudio(flag);
        return ret;
    }

    @Override
    public int handleAudio() throws RemoteException {
        // TODO Auto-generated method stub
        int ret = -1;
        if(true == getOverlayViewEnable())
            ret = _handleAudio();
        return ret;
    }
	
	private native void _init();
	private native void _deinit();
	private native int _displayHdmi();
	private native int _displayAndroid();
	private native int _displayPip(int x, int y, int width, int height);
	private native int _getHActive();
	private native int _getVActive();
	private native boolean _isDvi();
	private native boolean _isInterlace();
	private native int _enableAudio(int flag);
	private native int _handleAudio();
}
