/*
 * Copyright (C) 2009 The Android-x86 Open Source Project
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
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package com.android.server;

import java.net.UnknownHostException;
import android.net.pppoe.PppoeNative;
import android.net.pppoe.IPppoeManager;
import android.net.pppoe.PppoeManager;
import android.net.pppoe.PppoeStateTracker;
import android.net.pppoe.PppoeDevInfo;
import android.provider.Settings;
import android.util.Slog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import android.net.DhcpInfo;

public class PppoeService<syncronized> extends IPppoeManager.Stub{
	private Context mContext;
	private PppoeStateTracker mTracker;
	private String[] DevName;
	private static final String TAG = "PppoeService";
	private int isPppoeEnabled ;
	private int mPppoeState= PppoeManager.PPPOE_STATE_UNKNOWN;


	public PppoeService(Context context, PppoeStateTracker Tracker){
		mTracker = Tracker;
		mContext = context;

		isPppoeEnabled = getPersistedState();
		Slog.i(TAG,"Pppoe dev enabled " + isPppoeEnabled );
		getDeviceNameList();
		setPppoeState(isPppoeEnabled);
		registerForBroadcasts();
		Slog.i(TAG, "Trigger the pppoe monitor");
		mTracker.StartPolling();
	}

	public boolean isPppoeConfigured() {

		final ContentResolver cr = mContext.getContentResolver();
		int x = Settings.Secure.getInt(cr, Settings.Secure.PPPOE_CONF,0);

		if (x == 0) {
			Settings.Secure.putString(cr, Settings.Secure.PPPOE_IFNAME, "ppp0");
			Settings.Secure.putString(cr, Settings.Secure.PPPOE_IP, "0.0.0.0");
			Settings.Secure.putString(cr, Settings.Secure.PPPOE_DNS, "0.0.0.0");
			Settings.Secure.putString(cr, Settings.Secure.PPPOE_MASK, "255.255.255.0");
			Settings.Secure.putString(cr, Settings.Secure.PPPOE_ROUTE, "0.0.0.0");

			Slog.i(TAG, "@@@@@@NO CONFIG. set default");
			Settings.Secure.putInt(cr, Settings.Secure.PPPOE_CONF,1);
		}

		x = Settings.Secure.getInt(cr, Settings.Secure.PPPOE_CONF,0);
		
		if (x == 1)
			return true;
		return false;
	}

	public synchronized PppoeDevInfo getSavedPppoeConfig() {

		if (isPppoeConfigured() ) {
			final ContentResolver cr = mContext.getContentResolver();
			PppoeDevInfo info = new PppoeDevInfo();
			info.setIfName(Settings.Secure.getString(cr, Settings.Secure.PPPOE_IFNAME));
			info.setIpAddress(Settings.Secure.getString(cr, Settings.Secure.PPPOE_IP));
			info.setDnsAddr(Settings.Secure.getString(cr, Settings.Secure.PPPOE_DNS));
			info.setNetMask(Settings.Secure.getString(cr, Settings.Secure.PPPOE_MASK));
			info.setRouteAddr(Settings.Secure.getString(cr, Settings.Secure.PPPOE_ROUTE));

			return info;
		}
		return null;
	}


	public synchronized void setPppoeMode(String mode) {
		final ContentResolver cr = mContext.getContentResolver();
		Slog.i(TAG,"Set pppoe mode " + DevName + " mode " + mode);
		if (DevName != null) {
			Settings.Secure.putString(cr, Settings.Secure.PPPOE_IFNAME, DevName[0]);
			Settings.Secure.putInt(cr, Settings.Secure.PPPOE_CONF,1);
		}
	}

	public synchronized void UpdatePppoeDevInfo(PppoeDevInfo info) {
		final ContentResolver cr = mContext.getContentResolver();
	    Settings.Secure.putInt(cr, Settings.Secure.PPPOE_CONF,1);
	    Settings.Secure.putString(cr, Settings.Secure.PPPOE_IFNAME, info.getIfName());
	    Settings.Secure.putString(cr, Settings.Secure.PPPOE_IP, info.getIpAddress());
	    Settings.Secure.putString(cr, Settings.Secure.PPPOE_DNS, info.getDnsAddr());
	    Settings.Secure.putString(cr, Settings.Secure.PPPOE_ROUTE, info.getRouteAddr());
	    Settings.Secure.putString(cr, Settings.Secure.PPPOE_MASK,info.getNetMask());
	}

  private void registerForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mReceiver, intentFilter);
    }
    
	public int getTotalInterface() {
		return PppoeNative.getInterfaceCnt();
	}


	private int scanPppoeDevice() {
		int i = 0,j;
		if ((i = PppoeNative.getInterfaceCnt()) != 0) {
			Slog.i(TAG, "total found "+i+ " net devices");
			DevName = new String[i];
		}
		else
			return i;

		for (j = 0; j < i; j++) {
			DevName[j] = PppoeNative.getInterfaceName(j);
			if (DevName[j] == null)
				break;
			Slog.i(TAG,"device " + j + " name " + DevName[j]);
		}

		return i;
	}

	public String[] getDeviceNameList() {
		return (scanPppoeDevice() > 0 ) ? DevName : null;
	}

	private int getPersistedState() {
		final ContentResolver cr = mContext.getContentResolver();
		try {
			return Settings.Secure.getInt(cr, Settings.Secure.PPPOE_ON);
		} catch (Settings.SettingNotFoundException e) {
			return PppoeManager.PPPOE_STATE_UNKNOWN;
		}
	}

	private synchronized void persistPppoeEnabled(boolean enabled) {
	    final ContentResolver cr = mContext.getContentResolver();
	    Settings.Secure.putInt(cr, Settings.Secure.PPPOE_ON,
		enabled ? PppoeManager.PPPOE_STATE_ENABLED : PppoeManager.PPPOE_STATE_DISABLED);
	}

  private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                Slog.d(TAG, "ACTION_SCREEN_ON");
                try {
			mTracker.resetInterface();
		  } catch (UnknownHostException e) {
			Slog.e(TAG, "Wrong pppoe configuration");
		  }
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                Slog.d(TAG, "ACTION_SCREEN_OFF");
		  mTracker.stopInterface(false);
            } 
        }
    };
    
	public synchronized void setPppoeState(int state) {
		Slog.i(TAG, "setPppoeState from " + mPppoeState + " to "+ state);

		if (mPppoeState != state){
			mPppoeState = state;
			if (state == PppoeManager.PPPOE_STATE_DISABLED) {
				persistPppoeEnabled(false);
//				mTracker.stopInterface(false);				
				new Thread("stopInterface") {
					@Override
					public void run() {
						mTracker.stopInterface(false);  
					}
				}.start();
			        									
			} else {
				persistPppoeEnabled(true);
				if (!isPppoeConfigured()) {
					// If user did not configure any interfaces yet, pick the first one
					// and enable it.
					setPppoeMode(PppoeDevInfo.PPPOE_CONN_MODE_DHCP);
				}
//				try {
//					mTracker.resetInterface();					
//				} catch (UnknownHostException e) {
//					Slog.e(TAG, "Wrong pppoe configuration");
//				}
				new Thread("resetInterface") {
					@Override
					public void run() {
						try {
							mTracker.resetInterface();                   
						} catch (UnknownHostException e) {
							Slog.e(TAG, "Wrong pppoe configuration");
						}
					}
				}.start();	
			        		
			}
		}
	}

	public int getPppoeState( ) {
		return mPppoeState;
	}

	public boolean isPppoeDeviceUp( ) {
		try {
			boolean retval = false;
			FileReader fr = new FileReader("/sys/class/net/ppp0/operstate");
			BufferedReader br = new BufferedReader(fr, 32);
			String status = br.readLine();
			if (status != null && status.equals("up")) {
				Slog.d(TAG, "PppoeDevice status:" + status);
				retval = true;
			}
			else if (status != null && status.equals("down")) {
				Slog.d(TAG, "PppoeDevice status:" + status);
				retval = false;
			}
			else {
				retval =  false;
			}
			br.close();
			fr.close();
			return retval;
		} catch (IOException e) {
			Slog.d(TAG, "get PppoeDevice status error");
			return false;
		}
	}

	public DhcpInfo getDhcpInfo( ) {
		return mTracker.getDhcpInfo();
	}		
}
