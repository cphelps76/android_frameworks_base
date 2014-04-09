package com.android.server;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Slog;
import android.app.ISystemWriteService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;


class SystemWriteService extends ISystemWriteService.Stub {
 
    private static final String TAG = "SystemWriteService";
    private static final boolean DEBUG = false;
    private final Context mContext;
    
    /**
    * @hide
    */
    public SystemWriteService(Context context) {
        super();
        mContext = context;
    }
     
    /**
    * @hide
    */
    public String getProperty(String key){
        if(DEBUG)
            Slog.i(TAG, "getProperty key:" + key);
        return SystemProperties.get(key);
    }

    /**
    * @hide
    */
    public String getPropertyString(String key,String def){
        if(DEBUG)
            Slog.i(TAG, "getPropertyString key:" + key + " def:" + def);
        return SystemProperties.get(key,def);
    }

    /**
    * @hide
    */
    public int getPropertyInt(String key,int def){
        if(DEBUG)
            Slog.i(TAG, "getPropertyInt key:" + key + " def:" + def);
        return SystemProperties.getInt(key,def);
    }

    /**
    * @hide
    */
    public long getPropertyLong(String key,long def){
        if(DEBUG)
            Slog.i(TAG, "getPropertyLong key:" + key + " def:" + def);
        return SystemProperties.getLong(key,def);
    }
     
    /**
    * @hide
    */
    public boolean getPropertyBoolean(String key,boolean def){
        if(DEBUG)
            Slog.i(TAG, "getPropertyBoolean key:" + key + " def:" + def);
        return SystemProperties.getBoolean(key,def);
    }    
     
    /**
    * @hide
    */
    public void setProperty(String key, String value){
        if(DEBUG)
            Slog.i(TAG, "setProperty key:" + key + " value:" + value);
        SystemProperties.set(key,value);
    }
     
    /**
    * @hide
    */
    public String readSysfs(String path) {
		
        if (!new File(path).exists()) {
            Slog.e(TAG, "File not found: " + path);
            return null; 
        }

        String str = null;
        StringBuilder value = new StringBuilder();
        
        if(DEBUG)
            Slog.i(TAG, "readSysfs path:" + path);
        
        try {
            FileReader fr = new FileReader(path);
            BufferedReader br = new BufferedReader(fr);
            try {
                while ((str = br.readLine()) != null) {
                    if(str != null)
                        value.append(str);
                };
				fr.close();
				br.close();
                if(value != null)
                    return value.toString();
                else 
                    return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
     
    /**
    * @hide
    */
    public boolean writeSysfs(String path, String value) {
        if(DEBUG)
            Slog.i(TAG, "writeSysfs path:" + path + " value:" + value);
        
        if (!new File(path).exists()) {
            Slog.e(TAG, "File not found: " + path);
            return false; 
        }
        
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path), 64);
            try {
                writer.write(value);
            } finally {
                writer.close();
            }           
            return true;
                
        } catch (IOException e) { 
            Slog.e(TAG, "IO Exception when write: " + path, e);
            return false;
        }                 
    }
}
