package com.android.providers.settings; 

import android.content.ComponentName;
import android.os.Message;
import android.os.Handler;
import android.content.BroadcastReceiver; 
import android.content.Context; 
import android.content.Intent; 
import android.util.Log;
import android.content.ContentResolver;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import java.util.Map;

public class SettingsReceiver extends BroadcastReceiver {
    
    private final String TAG = "SettingsReceiver"; 
    private final boolean DEBUG = true;
    public static final String ACTION_STORE_CLASSNAME = "com.amlogic.store.classname";

    private Context mContext;

    public SettingsReceiver() { 
    } 

    @Override 
    public void onReceive(Context context, Intent intent) { 
        Log.d(TAG, "Receive broadcast: " + intent.getAction());
        mContext = context;	
         
        if(intent.getAction().equals(ACTION_STORE_CLASSNAME)) {
            String storeClassName = intent.getStringExtra("className");
            Log.d(TAG, "store the class " + storeClassName + " into secure table");
            try {
                final ContentResolver resolver = mContext.getContentResolver();
                Settings.Secure.putInt(resolver, storeClassName, 1);
                int value = Settings.Secure.getInt(resolver, storeClassName);
                Log.d(TAG, "get the value of " + storeClassName + " : " + value);
                if(value == 1) {
                    Log.d(TAG, "store ClassName: " + storeClassName + " success!");
                }else {
                    Log.w(TAG, "store ClassName: " + storeClassName + " fail!");
                }
                Settings.Secure.putString(resolver, "class_name", "");
            }
            catch(SettingNotFoundException e) {
            
            }
        }
   } 
} 