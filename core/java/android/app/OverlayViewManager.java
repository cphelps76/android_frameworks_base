package android.app;

import android.os.RemoteException;

//single instance mode per process

public class OverlayViewManager
{
	private IOverlayView mService = null;
	
	OverlayViewManager(IOverlayView service)
	{
		mService = service;
	}
	
	public void init()
	{
		try
		{
			mService.init();
		}
		catch(RemoteException e)
		{
			e.printStackTrace();
		}
	}
	
	public void deinit()
	{
		try
		{
			mService.deinit();
		}
		catch(RemoteException e)
		{
			e.printStackTrace();
		}
	}
	
    public int displayHdmi()
    {
		int result = 0;

    	try
    	{
			result = mService.displayHdmi();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public int displayAndroid()
    {
		int result = 0;

    	try
    	{
    		result = mService.displayAndroid();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public int displayPip(int x, int y, int width, int height)
    {
		int result = 0;

    	try
    	{
    		result = mService.displayPip(x, y, width, height);
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}
	
		return result;
    }
    
    public int getHActive()
    {
		int result = 0;

    	try
    	{
    		result = mService.getHActive();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public int getVActive()
    {
		int result = 0;

    	try
    	{
    		result = mService.getVActive();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public boolean isDvi()
    {
		boolean result = false;

    	try
    	{
    		result = mService.isDvi();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public boolean isInterlace()
    {
		boolean result = false;

    	try
    	{
    		result = mService.isInterlace();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public int enableAudio(int flag)
    {
		int result = 0;

    	try
    	{
    		result = mService.enableAudio(flag);
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
    
    public int handleAudio()
    {
		int result = 0;

    	try
    	{
    		result = mService.handleAudio();
    	}
    	catch(RemoteException e)
    	{
    		e.printStackTrace();
    	}

		return result;
    }
}
