/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
class BluetoothManagerService extends IBluetoothManager.Stub {
    private static final String TAG = "BluetoothManagerService";
    private static final boolean DBG = true;

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;
    private static final String ACTION_SERVICE_STATE_CHANGED="com.android.bluetooth.btservice.action.STATE_CHANGED";
    private static final String EXTRA_ACTION="action";
    private static final String SECURE_SETTINGS_BLUETOOTH_ADDR_VALID="bluetooth_addr_valid";
    private static final String SECURE_SETTINGS_BLUETOOTH_ADDRESS="bluetooth_address";
    private static final String SECURE_SETTINGS_BLUETOOTH_NAME="bluetooth_name";
    private static final int TIMEOUT_BIND_MS = 3000; //Maximum msec to wait for a bind
    private static final int TIMEOUT_SAVE_MS = 500; //Maximum msec to wait for a save
    //Maximum msec to wait for service restart
    private static final int SERVICE_RESTART_TIME_MS = 200;
    //Maximum msec to wait for restart due to error
    private static final int ERROR_RESTART_TIME_MS = 3000;
    //Maximum msec to delay MESSAGE_USER_SWITCHED
    private static final int USER_SWITCHED_TIME_MS = 200;

    private static final int MESSAGE_ENABLE = 1;
    private static final int MESSAGE_DISABLE = 2;
    private static final int MESSAGE_ENABLE_RADIO = 3;
    private static final int MESSAGE_DISABLE_RADIO = 4;
    //private static final int MESSAGE_REGISTER_ADAPTER = 20;
    //private static final int MESSAGE_UNREGISTER_ADAPTER = 21;
    private static final int MESSAGE_REGISTER_STATE_CHANGE_CALLBACK = 30;
    private static final int MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK = 31;
    private static final int MESSAGE_BLUETOOTH_SERVICE_CONNECTED = 40;
    private static final int MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED = 41;
    private static final int MESSAGE_RESTART_BLUETOOTH_SERVICE = 42;
    private static final int MESSAGE_BLUETOOTH_STATE_CHANGE=60;
    private static final int MESSAGE_TIMEOUT_BIND =100;
    private static final int MESSAGE_TIMEOUT_UNBIND =101;
    private static final int MESSAGE_GET_NAME_AND_ADDRESS=200;
    private static final int MESSAGE_SAVE_NAME_AND_ADDRESS=201;
    private static final int MESSAGE_USER_SWITCHED = 300;
    private static final int MESSAGE_GET_RX_COUNT=400;
    private static final int MAX_SAVE_RETRIES=3;

	private static String debugGetMessageName(int msgType) {
        switch (msgType) {
        case MESSAGE_ENABLE: return "MESSAGE_ENABLE";
        case MESSAGE_DISABLE:return "MESSAGE_DISABLE";
        case MESSAGE_ENABLE_RADIO: return "MESSAGE_ENABLE_RADIO";
        case MESSAGE_DISABLE_RADIO: return "MESSAGE_DISABLE_RADIO";
        //case MESSAGE_REGISTER_ADAPTER: return "MESSAGE_REGISTER_ADAPTER";
        // MESSAGE_UNREGISTER_ADAPTER: return "MESSAGE_UNREGISTER_ADAPTER";
        case MESSAGE_REGISTER_STATE_CHANGE_CALLBACK:return "MESSAGE_REGISTER_STATE_CHANGE_CALLBACK";
        case MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK:return "MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK";
        case MESSAGE_BLUETOOTH_SERVICE_CONNECTED:return "MESSAGE_BLUETOOTH_SERVICE_CONNECTED";
        case MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED:return "MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED";
        case MESSAGE_BLUETOOTH_STATE_CHANGE:return "MESSAGE_BLUETOOTH_STATE_CHANGE";
        case MESSAGE_TIMEOUT_BIND: return "MESSAGE_TIMEOUT_BIND";
        case MESSAGE_TIMEOUT_UNBIND: return "MESSAGE_TIMEOUT_UNBIND";
        case MESSAGE_GET_NAME_AND_ADDRESS: return "MESSAGE_GET_NAME_AND_ADDRESS";
        case MESSAGE_SAVE_NAME_AND_ADDRESS: return "MESSAGE_SAVE_NAME_AND_ADDRESS";
        }
        return "UNKNOWN("+msgType+")";
    }
    private static final int MAX_ERROR_RESTART_RETRIES=6;

    // Bluetooth persisted setting is off
    private static final int BLUETOOTH_OFF=0;
    // Bluetooth persisted setting is on
    // and Airplane mode won't affect Bluetooth state at start up
    private static final int BLUETOOTH_ON_BLUETOOTH=1;
    // Bluetooth persisted setting is on
    // but Airplane mode will affect Bluetooth state at start up
    // and Airplane mode will have higher priority.
    private static final int BLUETOOTH_ON_AIRPLANE=2;

    private static final int SERVICE_IBLUETOOTH = 1;
    private static final int SERVICE_IBLUETOOTHGATT = 2;

    private final Context mContext;

    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mRadioWakeLock;
    private Handler mDelayedHandler;
    private boolean mScreenOff;

    // Locks are not provided for mName and mAddress.
    // They are accessed in handler or broadcast receiver, same thread context.
    private String mAddress;
    private String mName;
    private final ContentResolver mContentResolver;
    private final RemoteCallbackList<IBluetoothManagerCallback> mCallbacks;
    private final RemoteCallbackList<IBluetoothStateChangeCallback> mStateChangeCallbacks;
    private IBluetooth mBluetooth;
    private IBluetoothGatt mBluetoothGatt;
    private boolean mBinding;
    private boolean mUnbinding;
    // used inside handler thread
    private boolean mQuietEnable = false;
    // configuarion from external IBinder call which is used to
    // synchronize with broadcast receiver.
    private boolean mQuietEnableExternal;
    // configuarion from external IBinder call which is used to
    // synchronize with broadcast receiver.
    private boolean mEnableExternal;
    // used inside handler thread
    private boolean mEnable;
    private int mState;
    private final BluetoothHandler mHandler;
    private int mErrorRecoveryRetryCounter;

    private void registerForAirplaneMode(IntentFilter filter) {
        final ContentResolver resolver = mContext.getContentResolver();
        final String airplaneModeRadios = Settings.Global.getString(resolver,
                Settings.Global.AIRPLANE_MODE_RADIOS);
        final String toggleableRadios = Settings.Global.getString(resolver,
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        boolean mIsAirplaneSensitive = airplaneModeRadios == null ? true :
                airplaneModeRadios.contains(Settings.Global.RADIO_BLUETOOTH);
        if (mIsAirplaneSensitive) {
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        }
    }

    private final IBluetoothCallback mBluetoothCallback =  new IBluetoothCallback.Stub() {
        @Override
        public void onBluetoothStateChange(int prevState, int newState) throws RemoteException  {
            if (DBG) Log.d(TAG, "onBluetoothStateChange prev=" + prevState + " new=" + newState);
            if ((prevState == BluetoothAdapter.STATE_TURNING_ON) &&
                (newState == BluetoothAdapter.STATE_OFF)) {
                mEnable = false;
            }
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_STATE_CHANGE,prevState,newState);
            mHandler.sendMessage(msg);
        }
    };
    private final Runnable mBTtimer = new Runnable() {
        public void run() {
            if (isEnabled() || isRadioEnabled()) {
                if (DBG) Log.d(TAG, "1.5s is passed , get rx count again");
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_GET_RX_COUNT,
                               0, 0));
                mDelayedHandler.postDelayed(this, 1*1500);
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED.equals(action)) {
                String newName = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
                if (DBG) Log.d(TAG, "Bluetooth Adapter name changed to " + newName);
                if (newName != null) {
                    storeNameAndAddress(newName, null);
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                synchronized(mReceiver) {
                    if (isBluetoothPersistedStateOn()) {
                        if (isAirplaneModeOn()) {
                            persistBluetoothSetting(BLUETOOTH_ON_AIRPLANE);
                        } else {
                            persistBluetoothSetting(BLUETOOTH_ON_BLUETOOTH);
                        }
                    }
                    if (isAirplaneModeOn()) {
                        // disable without persisting the setting
                        sendDisableMsg();
                    } else if (mEnableExternal) {
                        // enable without persisting the setting
                        sendEnableMsg(mQuietEnableExternal);
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if(!("true".equals(SystemProperties.get("bt.keep_on.enable")))) {
                    Log.v(TAG, "bt mScreenOn");
                    mScreenOff = false;
                    
                    mDelayedHandler.removeCallbacks(mBTtimer);
                    if (isBluetoothPersistedStateOn()) {
                        // enable without persisting the setting
                        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ENABLE,
                               0, 0));
                    } else {
                        if (DBG) Log.d(TAG, "enable Bluetooth radio for GPS after screen on");
    					//if radio is not on, so enable radio
    					//mDelayedHandler.removeCallbacks(mBTDelayeddisableradio);
    					if (!isRadioEnabled())
    						enableRadio();
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if(!("true".equals(SystemProperties.get("bt.keep_on.enable")))) {
                    Log.v(TAG, "bt mScreenOff");
                    mScreenOff = true;
                    //when bt is on or turning on in screen off, launch a timer to check whether bt 
                    //is in receving state
                    if (isEnabled() || mState == BluetoothAdapter.STATE_TURNING_ON || isRadioEnabled()) {
                        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_GET_RX_COUNT,
                               0, 0));
                        mDelayedHandler.postDelayed(mBTtimer, 1*1500);
                    }
                }
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_USER_SWITCHED,
                       intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0), 0));
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                synchronized(mReceiver) {
                    if (mEnableExternal && isBluetoothPersistedStateOnBluetooth()) {
                        //Enable
                        if (DBG) Log.d(TAG, "Auto-enabling Bluetooth.");
                        sendEnableMsg(mQuietEnableExternal);
                    }
                }

                if (!isNameAndAddressSet()) {
                    //Sync the Bluetooth name and address from the Bluetooth Adapter
                    if (DBG) Log.d(TAG,"Retrieving Bluetooth Adapter name and address...");
                    getNameAndAddress();
                }
            }
        }
    };

    BluetoothManagerService(Context context) {
        mHandler = new BluetoothHandler(IoThread.get().getLooper());

        mContext = context;
        mBluetooth = null;
        mBinding = false;
        mUnbinding = false;
        mEnable = false;
        mState = BluetoothAdapter.STATE_OFF;
        mQuietEnableExternal = false;
        mEnableExternal = false;
        mAddress = null;
        mName = null;
        mErrorRecoveryRetryCounter = 0;
        mContentResolver = context.getContentResolver();
        mCallbacks = new RemoteCallbackList<IBluetoothManagerCallback>();
        mStateChangeCallbacks = new RemoteCallbackList<IBluetoothStateChangeCallback>();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        registerForAirplaneMode(filter);
        // Bluetooth suspend/wakeup
        mDelayedHandler = new Handler();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        PowerManager powerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mRadioWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothRadio");
        
        mContext.registerReceiver(mReceiver, filter);
        loadStoredNameAndAddress();
        if (isBluetoothPersistedStateOn()) {
            mEnableExternal = true;
        }else{
            Log.d(TAG, "enable Bluetooth radio for GPS");
            enableRadio();
        }
    }

    /**
     *  Returns true if airplane mode is currently on
     */
    private final boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    /**
     *  Returns true if the Bluetooth saved state is "on"
     */
    private final boolean isBluetoothPersistedStateOn() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.BLUETOOTH_ON, 0) != BLUETOOTH_OFF;
    }

    /**
     *  Returns true if the Bluetooth saved state is BLUETOOTH_ON_BLUETOOTH
     */
    private final boolean isBluetoothPersistedStateOnBluetooth() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.BLUETOOTH_ON, 0) == BLUETOOTH_ON_BLUETOOTH;
    }

    /**
     *  Save the Bluetooth on/off state
     *
     */
    private void persistBluetoothSetting(int value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                               Settings.Global.BLUETOOTH_ON,
                               value);
    }

    /**
     * Returns true if the Bluetooth Adapter's name and address is
     * locally cached
     * @return
     */
    private boolean isNameAndAddressSet() {
        return mName !=null && mAddress!= null && mName.length()>0 && mAddress.length()>0;
    }

    /**
     * Retrieve the Bluetooth Adapter's name and address and save it in
     * in the local cache
     */
    private void loadStoredNameAndAddress() {
        if (DBG) Log.d(TAG, "Loading stored name and address");
        if (mContext.getResources().getBoolean
            (com.android.internal.R.bool.config_bluetooth_address_validation) &&
             Settings.Secure.getInt(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 0) == 0) {
            // if the valid flag is not set, don't load the address and name
            if (DBG) Log.d(TAG, "invalid bluetooth name and address stored");
            return;
        }
        mName = Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME);
        mAddress = Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS);
        if (DBG) Log.d(TAG, "Stored bluetooth Name=" + mName + ",Address=" + mAddress);
    }

    /**
     * Save the Bluetooth name and address in the persistent store.
     * Only non-null values will be saved.
     * @param name
     * @param address
     */
    private void storeNameAndAddress(String name, String address) {
        if (name != null) {
            Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME, name);
            mName = name;
            if (DBG) Log.d(TAG,"Stored Bluetooth name: " +
                Settings.Secure.getString(mContentResolver,SECURE_SETTINGS_BLUETOOTH_NAME));
        }

        if (address != null) {
            Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS, address);
            mAddress=address;
            if (DBG)  Log.d(TAG,"Stored Bluetoothaddress: " +
                Settings.Secure.getString(mContentResolver,SECURE_SETTINGS_BLUETOOTH_ADDRESS));
        }

        if ((name != null) && (address != null)) {
            Settings.Secure.putInt(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 1);
        }
    }

    public void acquireWakeLock() {
        if (DBG) Log.d(TAG,"acquireWakeLockk");
        if (mWakeLock != null && !mWakeLock.isHeld())
            mWakeLock.acquire();
    }
	
    public void releaseWakeLock() {
        if (DBG) Log.d(TAG,"releaseWakeLock");
        if (mWakeLock != null && mWakeLock.isHeld())
            mWakeLock.release();
    }

    public void acquire_radioWakeLock() {
        if (DBG) Log.d(TAG,"acquireRadioWakeLockk");
        if (mRadioWakeLock != null && !mRadioWakeLock.isHeld())
            mRadioWakeLock.acquire();
    }
	
    public void release_radioWakeLock() {
        if (DBG) Log.d(TAG,"releaseRadioWakeLock");
        if (mRadioWakeLock != null && mRadioWakeLock.isHeld())
            mRadioWakeLock.release();
    }

    public IBluetooth registerAdapter(IBluetoothManagerCallback callback){
        //Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_ADAPTER);
        //msg.obj = callback;
        //mHandler.sendMessage(msg);
        synchronized(mConnection) {
            boolean added = mCallbacks.register(callback);
            return mBluetooth;
        }
    }

    public void unregisterAdapter(IBluetoothManagerCallback callback) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");
        //Message msg = mHandler.obtainMessage(MESSAGE_UNREGISTER_ADAPTER);
        // msg.obj = callback;
        //mHandler.sendMessage(msg);
        synchronized (mConnection) {
            boolean removed = mCallbacks.unregister(callback);
        }
    }

    public void registerStateChangeCallback(IBluetoothStateChangeCallback callback) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_STATE_CHANGE_CALLBACK);
        msg.obj = callback;
        mHandler.sendMessage(msg);
    }

    public void unregisterStateChangeCallback(IBluetoothStateChangeCallback callback) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK);
        msg.obj = callback;
        mHandler.sendMessage(msg);
    }

    public boolean isEnabled() {
        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"isEnabled(): not allowed for non-active and non system user");
            return false;
        }

        synchronized(mConnection) {
            try {
                return (mBluetooth != null && mBluetooth.isEnabled());
            } catch (RemoteException e) {
                Log.e(TAG, "isEnabled()", e);
            }
        }
        return false;
    }

    /**
     * Tells whether the other radios(like fm/gps etc) are turned on or not
     * irrespective of the BT state.
     */
    public boolean isRadioEnabled() {
        synchronized(mConnection) {
            try {
                return (mBluetooth != null && mBluetooth.isRadioEnabled());
            } catch (RemoteException e) {
                Log.e(TAG, "isRadioEnabled()", e);
            }
        }
        return false;
    }

    public void getNameAndAddress() {
        if (DBG) {
            Log.d(TAG,"getNameAndAddress(): mBluetooth = " + mBluetooth +
                  " mBinding = " + mBinding);
        }
        Message msg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
        mHandler.sendMessage(msg);
    }
    public boolean enableNoAutoConnect()
    {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permission");

        if (DBG) {
            Log.d(TAG,"enableNoAutoConnect():  mBluetooth =" + mBluetooth +
                    " mBinding = " + mBinding);
        }
        int callingAppId = UserHandle.getAppId(Binder.getCallingUid());

        if (callingAppId != Process.NFC_UID) {
            throw new SecurityException("no permission to enable Bluetooth quietly");
        }

        synchronized(mReceiver) {
            mQuietEnableExternal = true;
            mEnableExternal = true;
            sendEnableMsg(true);
        }
        return true;

    }
    public boolean enable() {
        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"enable(): not allowed for non-active and non system user");
            return false;
        }

        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permission");
        if (DBG) {
            Log.d(TAG,"enable():  mBluetooth =" + mBluetooth +
                    " mBinding = " + mBinding);
        }

        synchronized(mReceiver) {
            mQuietEnableExternal = false;
            mEnableExternal = true;
            // waive WRITE_SECURE_SETTINGS permission check
            long callingIdentity = Binder.clearCallingIdentity();
            persistBluetoothSetting(BLUETOOTH_ON_BLUETOOTH);
            Binder.restoreCallingIdentity(callingIdentity);
            sendEnableMsg(false);
        }
        return true;
    }

    public boolean enableRadio() {
        if (DBG) {
            Log.d(TAG,"enable():  mBluetooth =" +
                    (mBluetooth==null?"null":mBluetooth) +
                    " mBinding = " + mBinding );
        }

        synchronized(mConnection) {
            //if (mBluetooth != null) return false;
            if (mBinding) {
                Log.w(TAG,"enable(): binding in progress. Returning..");
                return true;
            }
            if (mConnection == null) mBinding = true;
        }

        Message msg = mHandler.obtainMessage(MESSAGE_ENABLE_RADIO);
        // TO DO :handle persist when Fm app if required
        msg.arg1=1; //persist
        mHandler.sendMessage(msg);
        return true;
    }

    public boolean disable(boolean persist) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permissicacheNameAndAddresson");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"disable(): not allowed for non-active and non system user");
            return false;
        }

        if (DBG) {
            Log.d(TAG,"disable(): mBluetooth = " + mBluetooth +
                " mBinding = " + mBinding);
        }

        synchronized(mReceiver) {
            if (persist) {
                // waive WRITE_SECURE_SETTINGS permission check
                long callingIdentity = Binder.clearCallingIdentity();
                persistBluetoothSetting(BLUETOOTH_OFF);
                Binder.restoreCallingIdentity(callingIdentity);
            }
            mEnableExternal = false;
            sendDisableMsg();
        }
        return true;
    }

    public boolean disableRadio() {

        if (DBG) {
            Log.d(TAG,"disable(): mBluetooth = " +
                (mBluetooth==null?"null":mBluetooth) +
                " mBinding = " + mBinding);}

        synchronized(mConnection) {
             if (mBluetooth == null) return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_DISABLE_RADIO);
        // TO DO :handle persist when Fm app if required

        mHandler.sendMessage(msg);
        return true;
    }

    public void unbindAndFinish() {
        if (DBG) {
            Log.d(TAG,"unbindAndFinish(): " + mBluetooth +
                " mBinding = " + mBinding);
        }

        synchronized (mConnection) {
            if (mUnbinding) return;
            mUnbinding = true;
            if (mBluetooth != null) {
                if (!mConnection.isGetNameAddressOnly()) {
                    //Unregister callback object
                    try {
                        mBluetooth.unregisterCallback(mBluetoothCallback);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Unable to unregister BluetoothCallback",re);
                    }
                }
                if (DBG) Log.d(TAG, "Sending unbind request.");
                mBluetooth = null;
                //Unbind
                mContext.unbindService(mConnection);
                mUnbinding = false;
                mBinding = false;
            } else {
                mUnbinding=false;
            }
        }
    }

    public IBluetoothGatt getBluetoothGatt() {
        // sync protection
        return mBluetoothGatt;
    }

    private void sendBluetoothStateCallback(boolean isUp) {
        int n = mStateChangeCallbacks.beginBroadcast();
        if (DBG) Log.d(TAG,"Broadcasting onBluetoothStateChange("+isUp+") to " + n + " receivers.");
        for (int i=0; i <n;i++) {
            try {
                mStateChangeCallbacks.getBroadcastItem(i).onBluetoothStateChange(isUp);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to call onBluetoothStateChange() on callback #" + i , e);
            }
        }
        mStateChangeCallbacks.finishBroadcast();
    }

    /**
     * Inform BluetoothAdapter instances that Adapter service is up
     */
    private void sendBluetoothServiceUpCallback() {
        if (!mConnection.isGetNameAddressOnly()) {
            if (DBG) Log.d(TAG,"Calling onBluetoothServiceUp callbacks");
            int n = mCallbacks.beginBroadcast();
            Log.d(TAG,"Broadcasting onBluetoothServiceUp() to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBluetoothServiceUp(mBluetooth);
                }  catch (RemoteException e) {
                    Log.e(TAG, "Unable to call onBluetoothServiceUp() on callback #" + i, e);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }
    /**
     * Inform BluetoothAdapter instances that Adapter service is down
     */
    private void sendBluetoothServiceDownCallback() {
        if (!mConnection.isGetNameAddressOnly()) {
            if (DBG) Log.d(TAG,"Calling onBluetoothServiceDown callbacks");
            int n = mCallbacks.beginBroadcast();
            Log.d(TAG,"Broadcasting onBluetoothServiceDown() to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBluetoothServiceDown();
                }  catch (RemoteException e) {
                    Log.e(TAG, "Unable to call onBluetoothServiceDown() on callback #" + i, e);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }
    public String getAddress() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"getAddress(): not allowed for non-active and non system user");
            return null;
        }

        synchronized(mConnection) {
            if (mBluetooth != null) {
                try {
                    return mBluetooth.getAddress();
                } catch (RemoteException e) {
                    Log.e(TAG, "getAddress(): Unable to retrieve address remotely..Returning cached address",e);
                }
            }
        }
        // mAddress is accessed from outside.
        // It is alright without a lock. Here, bluetooth is off, no other thread is
        // changing mAddress
        return mAddress;
    }

    public String getName() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"getName(): not allowed for non-active and non system user");
            return null;
        }

        synchronized(mConnection) {
            if (mBluetooth != null) {
                try {
                    return mBluetooth.getName();
                } catch (RemoteException e) {
                    Log.e(TAG, "getName(): Unable to retrieve name remotely..Returning cached name",e);
                }
            }
        }
        // mName is accessed from outside.
        // It alright without a lock. Here, bluetooth is off, no other thread is
        // changing mName
        return mName;
    }

    private class BluetoothServiceConnection implements ServiceConnection {

        private boolean mGetNameAddressOnly;
        private boolean mIsTurnOnRadio;

        public void setTurnOnRadio(boolean isTurnOnRadio) {
            mIsTurnOnRadio = isTurnOnRadio;
        }

        public boolean isTurnOnRadio() {
            return mIsTurnOnRadio;
        }


        public void setGetNameAddressOnly(boolean getOnly) {
            mGetNameAddressOnly = getOnly;
        }

        public boolean isGetNameAddressOnly() {
            return mGetNameAddressOnly;
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "BluetoothServiceConnection: " + className.getClassName());
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_SERVICE_CONNECTED);
            // TBD if (className.getClassName().equals(IBluetooth.class.getName())) {
            if (className.getClassName().equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = SERVICE_IBLUETOOTH;
                // } else if (className.getClassName().equals(IBluetoothGatt.class.getName())) {
            } else if (className.getClassName().equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = SERVICE_IBLUETOOTHGATT;
            } else {
                Log.e(TAG, "Unknown service connected: " + className.getClassName());
                return;
            }
            msg.obj = service;
            mHandler.sendMessage(msg);
        }

        public void onServiceDisconnected(ComponentName className) {
            // Called if we unexpected disconnected.
            if (DBG) Log.d(TAG, "BluetoothServiceConnection, disconnected: " +
                           className.getClassName());
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED);
            if (className.getClassName().equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = SERVICE_IBLUETOOTH;
            } else if (className.getClassName().equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = SERVICE_IBLUETOOTHGATT;
            } else {
                Log.e(TAG, "Unknown service disconnected: " + className.getClassName());
                return;
            }
            mHandler.sendMessage(msg);
        }
    }

    private BluetoothServiceConnection mConnection = new BluetoothServiceConnection();

    private class BluetoothHandler extends Handler {
        public BluetoothHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DBG) Log.d (TAG, "Message: " + msg.what);
            switch (msg.what) {
                case MESSAGE_GET_NAME_AND_ADDRESS: {
                    if (DBG) Log.d(TAG,"MESSAGE_GET_NAME_AND_ADDRESS");
                    synchronized(mConnection) {
                        //Start bind request
                        if ((mBluetooth == null) && (!mBinding)) {
                            if (DBG) Log.d(TAG, "Binding to service to get name and address");
                            mConnection.setGetNameAddressOnly(true);
                            //Start bind timeout and bind
                            Message timeoutMsg = mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                            mHandler.sendMessageDelayed(timeoutMsg,TIMEOUT_BIND_MS);
                            Intent i = new Intent(IBluetooth.class.getName());
                            if (!doBind(i, mConnection,
                                    Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                                mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                            } else {
                                mBinding = true;
                            }
                        }
                        else {
                            Message saveMsg= mHandler.obtainMessage(MESSAGE_SAVE_NAME_AND_ADDRESS);
                            saveMsg.arg1 = 0;
                            if (mBluetooth != null) {
                                mHandler.sendMessage(saveMsg);
                            } else {
                                // if enable is also called to bind the service
                                // wait for MESSAGE_BLUETOOTH_SERVICE_CONNECTED
                                mHandler.sendMessageDelayed(saveMsg, TIMEOUT_SAVE_MS);
                            }
                        }
                    }
                    break;
                }
                case MESSAGE_SAVE_NAME_AND_ADDRESS: {
                    boolean unbind = false;
                    if (DBG) Log.d(TAG,"MESSAGE_SAVE_NAME_AND_ADDRESS");
                    synchronized(mConnection) {
                        if (!mEnable && mBluetooth != null) {
                        /*
                            try {
                                mBluetooth.enable();
                            } catch (RemoteException e) {
                                Log.e(TAG,"Unable to call enable()",e);
                            }
                        */
                        }
                    }
                    if (mBluetooth != null) waitForOnOff(true, false);
                    synchronized(mConnection) {
                        if (mBluetooth != null) {
                            String name =  null;
                            String address = null;
                            try {
                                name =  mBluetooth.getName();
                                address = mBluetooth.getAddress();
                            } catch (RemoteException re) {
                                Log.e(TAG,"",re);
                            }

                            if (name != null && address != null) {
                                storeNameAndAddress(name,address);
                                if (mConnection.isGetNameAddressOnly()) {
                                    unbind = true;
                                }
                            } else {
                                if (msg.arg1 < MAX_SAVE_RETRIES) {
                                    Message retryMsg = mHandler.obtainMessage(MESSAGE_SAVE_NAME_AND_ADDRESS);
                                    retryMsg.arg1= 1+msg.arg1;
                                    if (DBG) Log.d(TAG,"Retrying name/address remote retrieval and save.....Retry count =" + retryMsg.arg1);
                                    mHandler.sendMessageDelayed(retryMsg, TIMEOUT_SAVE_MS);
                                } else {
                                    Log.w(TAG,"Maximum name/address remote retrieval retry exceeded");
                                    if (mConnection.isGetNameAddressOnly()) {
                                        unbind = true;
                                    }
                                }
                            }
                            if (!mEnable) {
                                /*
                                try {
                                    mBluetooth.disable();
                                } catch (RemoteException e) {
                                    Log.e(TAG,"Unable to call disable()",e);
                                }
                                */
                            }
                        } else {
                            // rebind service by Request GET NAME AND ADDRESS
                            // if service is unbinded by disable or
                            // MESSAGE_BLUETOOTH_SERVICE_CONNECTED is not received
                            Message getMsg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
                            mHandler.sendMessage(getMsg);
                        }
                    }
                    if (!mEnable && mBluetooth != null) waitForOnOff(false, true);
                    if (unbind) {
                        unbindAndFinish();
                    }
                    break;
                }
                case MESSAGE_ENABLE:
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_ENABLE: mBluetooth = " + mBluetooth);
                    }
                    mHandler.removeMessages(MESSAGE_RESTART_BLUETOOTH_SERVICE);
                    mEnable = true;
                    handleEnable(msg.arg1 == 1);
                    break;

                case MESSAGE_DISABLE:
                    mHandler.removeMessages(MESSAGE_RESTART_BLUETOOTH_SERVICE);
                    if (mEnable && mBluetooth != null) {
                        waitForOnOff(true, false);
                        mEnable = false;
                        handleDisable();
                        waitForOnOff(false, false);
                    } else {
                        mEnable = false;
                        handleDisable();
                    }
                    break;

                case MESSAGE_ENABLE_RADIO:
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_ENABLE_RADIO: mBluetooth = " + mBluetooth);
                    }

                    handleEnableRadio();
                    break;

                case MESSAGE_DISABLE_RADIO:
                    handleDisableRadio();
                    break;

                case MESSAGE_REGISTER_STATE_CHANGE_CALLBACK:
                {
                    IBluetoothStateChangeCallback callback = (IBluetoothStateChangeCallback) msg.obj;
                    if (callback != null) {
                        mStateChangeCallbacks.register(callback);
                    }
                    break;
                }
                case MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK:
                {
                    IBluetoothStateChangeCallback callback = (IBluetoothStateChangeCallback) msg.obj;
                    if (callback != null) {
                        mStateChangeCallbacks.unregister(callback);
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_SERVICE_CONNECTED:
                {
                    if (DBG) Log.d(TAG,"MESSAGE_BLUETOOTH_SERVICE_CONNECTED: " + msg.arg1);

                    IBinder service = (IBinder) msg.obj;
                    synchronized(mConnection) {
                        if (msg.arg1 == SERVICE_IBLUETOOTHGATT) {
                            mBluetoothGatt = IBluetoothGatt.Stub.asInterface(service);
                            break;
                        } // else must be SERVICE_IBLUETOOTH

                        //Remove timeout
                        mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);

                        mBinding = false;
                        mBluetooth = IBluetooth.Stub.asInterface(service);

                        try {
                            boolean enableHciSnoopLog = (Settings.Secure.getInt(mContentResolver,
                                Settings.Secure.BLUETOOTH_HCI_LOG, 0) == 1);
                            if (!mBluetooth.configHciSnoopLog(enableHciSnoopLog)) {
                                Log.e(TAG,"IBluetooth.configHciSnoopLog return false");
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG,"Unable to call configHciSnoopLog", e);
                        }

                        if (mConnection.isGetNameAddressOnly()) {
                            //Request GET NAME AND ADDRESS
                            Message getMsg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
                            mHandler.sendMessage(getMsg);
                            //if (!mEnable) return;
                        }

                        mConnection.setGetNameAddressOnly(false);
                        //Register callback object
                        try {
                            mBluetooth.registerCallback(mBluetoothCallback);
                        } catch (RemoteException re) {
                            Log.e(TAG, "Unable to register BluetoothCallback",re);
                        }
                        //Inform BluetoothAdapter instances that service is up
                        sendBluetoothServiceUpCallback();

                        //Do enable request
                        if (mConnection.isTurnOnRadio()){
                            try {
                                if(!mBluetooth.enableRadio()) {
                                    Log.e(TAG,"IBluetooth.enableRadio() returned false");
                                }
                                mConnection.setTurnOnRadio(false);

                            } catch (RemoteException e) {
                                Log.e(TAG,"Unable to call enableRadio()",e);
                            }
                            break;
                        }
                        try {
                            if (mQuietEnable == false) {
                                if(!mBluetooth.enable()) {
                                    Log.e(TAG,"IBluetooth.enable() returned false");
                                }
                            }
                            else
                            {
                                if(!mBluetooth.enableNoAutoConnect()) {
                                    Log.e(TAG,"IBluetooth.enableNoAutoConnect() returned false");
                                }
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG,"Unable to call enable()",e);
                        }
                    }

                    if (!mEnable) {
                        waitForOnOff(true, false);
                        handleDisable();
                        waitForOnOff(false, false);
                    }
                    break;
                }
                case MESSAGE_TIMEOUT_BIND: {
                    Log.e(TAG, "MESSAGE_TIMEOUT_BIND");
                    synchronized(mConnection) {
                        mBinding = false;
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_STATE_CHANGE:
                {
                    int prevState = msg.arg1;
                    int newState = msg.arg2;
                    if (DBG) Log.d(TAG, "MESSAGE_BLUETOOTH_STATE_CHANGE: prevState = " + prevState + ", newState=" + newState);
                    mState = newState;
                    bluetoothStateChangeHandler(prevState, newState);
                    // handle error state transition case from TURNING_ON to OFF
                    // unbind and rebind bluetooth service and enable bluetooth
                    if ((prevState == BluetoothAdapter.STATE_TURNING_ON) &&
                        (newState == BluetoothAdapter.STATE_OFF) &&
                        (mBluetooth != null) && mEnable) {
                        recoverBluetoothServiceFromError();
                    }
                    if (newState == BluetoothAdapter.STATE_ON) {
                        // bluetooth is working, reset the counter
                        if (mErrorRecoveryRetryCounter != 0) {
                            Log.w(TAG, "bluetooth is recovered from error");
                            mErrorRecoveryRetryCounter = 0;
                        }
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED:
                {
                    Log.e(TAG, "MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED: " + msg.arg1);
                    synchronized(mConnection) {
                        if (msg.arg1 == SERVICE_IBLUETOOTH) {
                            // if service is unbinded already, do nothing and return
                            if (mBluetooth == null) break;
                            mBluetooth = null;
                        } else if (msg.arg1 == SERVICE_IBLUETOOTHGATT) {
                            mBluetoothGatt = null;
                            break;
                        } else {
                            Log.e(TAG, "Bad msg.arg1: " + msg.arg1);
                            break;
                        }
                    }

                    if (mEnable) {
                        mEnable = false;
                        // Send a Bluetooth Restart message
                        Message restartMsg = mHandler.obtainMessage(
                            MESSAGE_RESTART_BLUETOOTH_SERVICE);
                        mHandler.sendMessageDelayed(restartMsg,
                            SERVICE_RESTART_TIME_MS);
                    }

                    if (!mConnection.isGetNameAddressOnly()) {
                        sendBluetoothServiceDownCallback();

                        // Send BT state broadcast to update
                        // the BT icon correctly
                        if ((mState == BluetoothAdapter.STATE_TURNING_ON) ||
                            (mState == BluetoothAdapter.STATE_ON)) {
                            bluetoothStateChangeHandler(BluetoothAdapter.STATE_ON,
                                                        BluetoothAdapter.STATE_TURNING_OFF);
                            mState = BluetoothAdapter.STATE_TURNING_OFF;
                        }
                        if (mState == BluetoothAdapter.STATE_TURNING_OFF) {
                            bluetoothStateChangeHandler(BluetoothAdapter.STATE_TURNING_OFF,
                                                        BluetoothAdapter.STATE_OFF);
                        }

                        mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
                        mState = BluetoothAdapter.STATE_OFF;
                    }
                    break;
                }
                case MESSAGE_RESTART_BLUETOOTH_SERVICE:
                {
                    Log.d(TAG, "MESSAGE_RESTART_BLUETOOTH_SERVICE:"
                        +" Restart IBluetooth service");
                    /* Enable without persisting the setting as
                     it doesnt change when IBluetooth
                     service restarts */
                    mEnable = true;
                    handleEnable(mQuietEnable);
                    break;
                }

                case MESSAGE_TIMEOUT_UNBIND:
                {
                    Log.e(TAG, "MESSAGE_TIMEOUT_UNBIND");
                    synchronized(mConnection) {
                        mUnbinding = false;
                    }
                    break;
                }

                case MESSAGE_USER_SWITCHED:
                {
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_USER_SWITCHED");
                    }
                    mHandler.removeMessages(MESSAGE_USER_SWITCHED);
                    /* disable and enable BT when detect a user switch */
                    if ( (mEnable || isRadioEnabled()) && mBluetooth != null) {
                        synchronized (mConnection) {
                            if (mBluetooth != null) {
                                //Unregister callback object
                                try {
                                    mBluetooth.unregisterCallback(mBluetoothCallback);
                                } catch (RemoteException re) {
                                    Log.e(TAG, "Unable to unregister",re);
                                }
                            }
                        }

                        if (mState == BluetoothAdapter.STATE_TURNING_OFF) {
                            // MESSAGE_USER_SWITCHED happened right after MESSAGE_ENABLE
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_OFF);
                            mState = BluetoothAdapter.STATE_OFF;
                        }
                        if (mState == BluetoothAdapter.STATE_OFF) {
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_TURNING_ON);
                            mState = BluetoothAdapter.STATE_TURNING_ON;
                        }

                        waitForOnOff(true, false);

                        if (mState == BluetoothAdapter.STATE_TURNING_ON) {
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_ON);
                        }

                        // disable
                        handleDisable();
                        handleDisableRadio();
                        // Pbap service need receive STATE_TURNING_OFF intent to close
                        bluetoothStateChangeHandler(BluetoothAdapter.STATE_ON,
                                                    BluetoothAdapter.STATE_TURNING_OFF);

                        waitForOnOff(false, true);

                        bluetoothStateChangeHandler(BluetoothAdapter.STATE_TURNING_OFF,
                                                    BluetoothAdapter.STATE_OFF);
                        sendBluetoothServiceDownCallback();
                        synchronized (mConnection) {
                            if (mBluetooth != null) {
                                mBluetooth = null;
                                //Unbind
                                mContext.unbindService(mConnection);
                            }
                        }
                        SystemClock.sleep(100);

                        mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
                        mState = BluetoothAdapter.STATE_OFF;
                        // enable
                        handleEnable(mQuietEnable);
		    } else if (mBinding || mBluetooth != null) {
                        Message userMsg = mHandler.obtainMessage(MESSAGE_USER_SWITCHED);
                        userMsg.arg2 = 1 + msg.arg2;
                        // if user is switched when service is being binding
                        // delay sending MESSAGE_USER_SWITCHED
                        mHandler.sendMessageDelayed(userMsg, USER_SWITCHED_TIME_MS);
                        if (DBG) {
                            Log.d(TAG, "delay MESSAGE_USER_SWITCHED " + userMsg.arg2);
                        }
		    }
                    break;
                }
                case MESSAGE_GET_RX_COUNT:
                {
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_GET_RX_COUNT");
                    }
                    synchronized(mConnection) {
                        if(mBluetooth == null)
                            Log.e(TAG, "mBluetooth is null");
                        if (mBluetooth != null) {
                            try {
                                if (DBG) {
                                    Log.d(TAG, "mBluetooth.get_rx_count");
                                }
                                mBluetooth.get_rx_count();
                            } catch (RemoteException e) {
                                Log.e(TAG,"Unable to call get_rx_count()",e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleEnable(boolean quietMode) {
        mQuietEnable = quietMode;
        synchronized(mConnection) {
            if ((mBluetooth == null) && (!mBinding)) {
                //Start bind timeout and bind
                Message timeoutMsg=mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                mHandler.sendMessageDelayed(timeoutMsg,TIMEOUT_BIND_MS);
                mConnection.setGetNameAddressOnly(false);
                Intent i = new Intent(IBluetooth.class.getName());
                if (!doBind(i, mConnection,Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                    mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                } else {
                    mBinding = true;
                }
            } else if (mBluetooth != null) {
                if (mConnection.isGetNameAddressOnly()) {
                    // if GetNameAddressOnly is set, we can clear this flag,
                    // so the service won't be unbind
                    // after name and address are saved
                    mConnection.setGetNameAddressOnly(false);
                    //Register callback object
                    try {
                        mBluetooth.registerCallback(mBluetoothCallback);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Unable to register BluetoothCallback",re);
                    }
                    //Inform BluetoothAdapter instances that service is up
                    sendBluetoothServiceUpCallback();
                }
                //Enable bluetooth
                try {
                    if (!mQuietEnable) {
                        if(!mBluetooth.enable()) {
                            Log.e(TAG,"IBluetooth.enable() returned false");
                        }
                    }
                    else {
                        if(!mBluetooth.enableNoAutoConnect()) {
                            Log.e(TAG,"IBluetooth.enableNoAutoConnect() returned false");
                        }
                    }
                } catch (RemoteException e) {
                    Log.e(TAG,"Unable to call enable()",e);
                }
            }
        }
    }

    boolean doBind(Intent intent, ServiceConnection conn, int flags, UserHandle user) {
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, conn, flags, user)) {
            Log.e(TAG, "Fail to bind to: " + intent);
            return false;
        }
        return true;
    }

    private void handleDisable() {
        synchronized(mConnection) {
            // don't need to disable if GetNameAddressOnly is set,
            // service will be unbinded after Name and Address are saved
            if ((mBluetooth != null) && (!mConnection.isGetNameAddressOnly())) {
                if (DBG) Log.d(TAG,"Sending off request.");

                try {
                    if(!mBluetooth.disable()) {
                        Log.e(TAG,"IBluetooth.disable() returned false");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG,"Unable to call disable()",e);
                }
            }
        }
    }

    private boolean checkIfCallerIsForegroundUser() {
        int foregroundUser;
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long callingIdentity = Binder.clearCallingIdentity();
        int callingAppId = UserHandle.getAppId(callingUid);
        boolean valid = false;
        try {
            foregroundUser = ActivityManager.getCurrentUser();
            valid = (callingUser == foregroundUser) ||
                    callingAppId == Process.NFC_UID;
            if (DBG) {
                Log.d(TAG, "checkIfCallerIsForegroundUser: valid=" + valid
                    + " callingUser=" + callingUser
                    + " foregroundUser=" + foregroundUser);
            }
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
        return valid;
    }

    private final Runnable mBTShutDown = new Runnable() {
        public void run() {
            releaseWakeLock();
        }
    };

    private void bluetoothStateChangeHandler(int prevState, int newState) {
        if (prevState != newState) {
			if(!("true".equals(SystemProperties.get("bt.keep_on.enable")))) {
	            if(newState == BluetoothAdapter.STATE_RADIO_ON) {
	                acquire_radioWakeLock();
	            }

	            if(newState == BluetoothAdapter.STATE_RADIO_OFF) {
	                release_radioWakeLock();
	            }
			}
            
            if ((prevState == BluetoothAdapter.STATE_OFF) && (newState == BluetoothAdapter.STATE_RADIO_OFF)) {
                Intent intentRadio1 = new Intent(BluetoothAdapter.ACTION_RADIO_STATE_CHANGED);
                intentRadio1.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prevState);
                intentRadio1.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
                if (DBG) Log.d(TAG,"Radio State Change Intent: " + prevState + " -> " + newState);
                mContext.sendBroadcast(intentRadio1);

                sendBluetoothServiceDownCallback();
                unbindAndFinish();
                return;
            }
 
            //Notify all proxy objects first of adapter state change
            if (newState == BluetoothAdapter.STATE_ON || newState == BluetoothAdapter.STATE_OFF) {
                boolean isUp = (newState==BluetoothAdapter.STATE_ON);
                sendBluetoothStateCallback(isUp);

                if (isUp) {
                    // connect to GattService
                    if (mContext.getPackageManager().hasSystemFeature(
                                                     PackageManager.FEATURE_BLUETOOTH_LE)) {
                        Intent i = new Intent(IBluetoothGatt.class.getName());
                        doBind(i, mConnection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT);
                    }
                } else {
                    //If Bluetooth is off, send service down event to proxy objects, and unbind
                    if (!isUp && canUnbindBluetoothService()) {
                        if (!isRadioEnabled()) {
                            sendBluetoothServiceDownCallback();
                            unbindAndFinish();
                        }
                    }
                }
			}
			if(!("true".equals(SystemProperties.get("bt.keep_on.enable")))) {
	            //when turning on bt, acquire wake lock
	            if(newState == BluetoothAdapter.STATE_TURNING_ON) {
	                mDelayedHandler.removeCallbacks(mBTShutDown);
	                acquireWakeLock();
	            }
	                
	            //when bt is off, release wake lock after 200ms
	            if(newState == BluetoothAdapter.STATE_OFF){
	                //cancle the bt timer
	                mDelayedHandler.removeCallbacks(mBTtimer);
	                mDelayedHandler.postDelayed(mBTShutDown, 2*100);
	            }
			}

            //Send broadcast message to everyone else
            // BT specific braodcast event
            if ((newState != BluetoothAdapter.STATE_RADIO_ON) &&
                    (newState != BluetoothAdapter.STATE_RADIO_OFF)) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
                intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prevState);
                intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                if (DBG) Log.d(TAG,"Bluetooth State Change Intent: " +
                            prevState + " -> " + newState);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                   BLUETOOTH_PERM);

            } else {
                // Radio specific braodcast event
                Intent intentRadio =
                        new Intent(BluetoothAdapter.ACTION_RADIO_STATE_CHANGED);
                intentRadio.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prevState);
                intentRadio.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
                if (DBG) Log.d(TAG,"Radio State Change Intent: " +
                                prevState + " -> " + newState);
                mContext.sendBroadcast(intentRadio);

            }
        }
    }

    /**
     *  if on is true, wait for state become ON
     *  if off is true, wait for state become OFF
     *  if both on and off are false, wait for state not ON
     */
    private boolean waitForOnOff(boolean on, boolean off) {
        int i = 0;
        while (i < 10) {
            synchronized(mConnection) {
                try {
                    if (mBluetooth == null) break;
                    if (on) {
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_ON) return true;
                    } else if (off) {
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_OFF) return true;
                    } else {
                        if (mBluetooth.getState() != BluetoothAdapter.STATE_ON) return true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "getState()", e);
                    break;
                }
            }
            if (on || off) {
                SystemClock.sleep(300);
            } else {
                SystemClock.sleep(50);
            }
            i++;
        }
        Log.e(TAG,"waitForOnOff time out");
        return false;
    }
    private void handleEnableRadio() {

        synchronized(mConnection) {
            if (mBluetooth == null) {
                //Start bind timeout and bind
                Message timeoutMsg=mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                mHandler.sendMessageDelayed(timeoutMsg,TIMEOUT_BIND_MS);
                mConnection.setGetNameAddressOnly(false);
                mConnection.setTurnOnRadio(true);
                Intent i = new Intent(IBluetooth.class.getName());
                if (!mContext.bindService(i, mConnection,Context.BIND_AUTO_CREATE)) {
                    mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                    Log.e(TAG, "Fail to bind to: " + IBluetooth.class.getName());
                }
            } else {

                try {
                    if (DBG) Log.d(TAG,"Getting and storing Bluetooth name and address prior to enable.");
                    storeNameAndAddress(mBluetooth.getName(),mBluetooth.getAddress());
                } catch (RemoteException e) {Log.e(TAG, "", e);};

                //Enable Radio
                try {

                    if(!mBluetooth.enableRadio()) {
                        Log.e(TAG,"IBluetooth.enableRadio() returned false");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG,"Unable to call enableRadio()",e);
                }
            }
        }
    }

    private void handleDisableRadio() {
        synchronized(mConnection) {
            if (isRadioEnabled()) {
                try {
                    if(!mBluetooth.disableRadio()) {
                        Log.e(TAG,"IBluetooth.disableRadio() returned false");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG,"Unable to call disableRadio()",e);
                }
            }
        }
    }

    private void sendDisableMsg() {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_DISABLE));
    }

    private void sendEnableMsg(boolean quietMode) {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ENABLE,
                             quietMode ? 1 : 0, 0));
    }

    private boolean canUnbindBluetoothService() {
        synchronized(mConnection) {
            //Only unbind with mEnable flag not set
            //For race condition: disable and enable back-to-back
            //Avoid unbind right after enable due to callback from disable
            //Only unbind with Bluetooth at OFF state
            //Only unbind without any MESSAGE_BLUETOOTH_STATE_CHANGE message
            try {
                if (mEnable || (mBluetooth == null)) return false;
                if (mHandler.hasMessages(MESSAGE_BLUETOOTH_STATE_CHANGE)) return false;
                return (mBluetooth.getState() == BluetoothAdapter.STATE_OFF);
            } catch (RemoteException e) {
                Log.e(TAG, "getState()", e);
            }
        }
        return false;
    }

    private void recoverBluetoothServiceFromError() {
        Log.e(TAG,"recoverBluetoothServiceFromError");
        synchronized (mConnection) {
            if (mBluetooth != null) {
                //Unregister callback object
                try {
                    mBluetooth.unregisterCallback(mBluetoothCallback);
                } catch (RemoteException re) {
                    Log.e(TAG, "Unable to unregister",re);
                }
            }
        }

        SystemClock.sleep(500);

        // disable
        handleDisable();

        waitForOnOff(false, true);

        sendBluetoothServiceDownCallback();
        synchronized (mConnection) {
            if (mBluetooth != null) {
                mBluetooth = null;
                //Unbind
                mContext.unbindService(mConnection);
            }
        }

        mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
        mState = BluetoothAdapter.STATE_OFF;

        mEnable = false;

        if (mErrorRecoveryRetryCounter++ < MAX_ERROR_RESTART_RETRIES) {
            // Send a Bluetooth Restart message to reenable bluetooth
            Message restartMsg = mHandler.obtainMessage(
                             MESSAGE_RESTART_BLUETOOTH_SERVICE);
            mHandler.sendMessageDelayed(restartMsg, ERROR_RESTART_TIME_MS);
        } else {
            // todo: notify user to power down and power up phone to make bluetooth work.
        }
    }
}
