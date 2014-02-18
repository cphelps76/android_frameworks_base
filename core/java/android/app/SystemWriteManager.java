package android.app;
import android.content.Context;
import android.content.Intent;
import android.util.Slog;
import android.os.RemoteException;
import android.os.ServiceManager;

/**
 * {@hide}
 */
public class SystemWriteManager{
    private final ISystemWriteService mService ;
    private String TAG = "SystemWriteManager";
     /**
        * @hide
        */
    SystemWriteManager(ISystemWriteService service) {
        mService = service;
    }
     /**
        * @hide
        */
    public String getProperty(String key) {
        try {
            String propValue = mService.getProperty(key);
            return  propValue;
        } catch (RemoteException ex) {
            Slog.e(TAG,"getPropertie error!");
            return null;
        }
    }
     /**
        * @hide
        */
    public String getPropertyString(String key,String def) {
        try {
            String propValue = mService.getPropertyString(key,def);
            return  propValue;
        } catch (RemoteException ex) {
            Slog.e(TAG,"getPropertie error!");
            return null;
        }
    }
     /**
         * @hide
         */
     public int getPropertyInt(String key,int def) {
         try {
             int propValue = mService.getPropertyInt(key,def);
             return  propValue;
         } catch (RemoteException ex) {
             Slog.e(TAG,"getPropertie error!");
             return -1;
         }
     }
     /**
         * @hide
         */
     public long getPropertyLong(String key,long def) {
         try {
             long propValue = mService.getPropertyLong(key,def);
             return  propValue;
         } catch (RemoteException ex) {
             Slog.e(TAG,"getPropertie error!");
             return -1;
         }
     }       
     /**
         * @hide
         */
     public boolean getPropertyBoolean(String key,boolean def) {
         try {
             boolean propValue = mService.getPropertyBoolean(key,def);
             return  propValue;
         } catch (RemoteException ex) {
             Slog.e(TAG,"getPropertie error!");
             return false;
         }
     }
     /**
        * @hide
        */  
    public boolean setProperty(String key,String value) {
        try {
            mService.setProperty(key,value);
            return true ;
        } catch (RemoteException ex) {
            Slog.e(TAG,"setPropertie error!");
            return false;
        }
    }
     /**
        * @hide
        */
    public String readSysfs(String path) {
        try {
            return mService.readSysfs(path);
        } catch (RemoteException ex) {
            Slog.e(TAG,"read sys error , path=" + path);
            return null;
        }
        
    }
     /**
        * @hide
        */
    public boolean writeSysfs(String path,String value) {
        try {
            return mService.writeSysfs(path,value);
        } catch (RemoteException ex) {
            Slog.e(TAG,"write sys error , path=" + path + " value=" + value);
             return false;
        }
       
    }
}
