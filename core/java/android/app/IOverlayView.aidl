package android.app;

interface IOverlayView
{
	void init();
	
	int displayHdmi();
    
	int displayAndroid();
    
	int displayPip(int x, int y, int width, int height);
    
	int getHActive();
    
	int getVActive();
    
	boolean isDvi();
    
	boolean isInterlace();
    
	int enableAudio(int flag);
    
	int handleAudio();
	
	void deinit();
}
