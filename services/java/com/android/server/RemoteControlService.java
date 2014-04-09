package com.android.server;

import android.util.Config;
import android.util.Log;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
//import android.remotecontrol.IRemoteControl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import android.net.LocalSocketAddress;
import android.net.LocalSocket;

//public class RemoteControlService extends IRemoteControl.Stub{
public class RemoteControlService {
    private static final String TAG = "RemoteControlService";

	//for remote control daemon enable or disable
	public static final String ACTION_DAEMON_ENABLE		= "android.intent.action.RC_DAEMON_ENABLE";
	public static final String ACTION_DAEMON_DISABLE	= "android.intent.action.RC_DAEMON_DISABLE";
	
	//for online video
	public static final String ACTION_PLAY_FAST			= "android.intent.action.RC_PLAY_FAST";
	public static final String ACTION_PLAY_SLOW			= "android.intent.action.RC_PLAY_SLOW";
	public static final String ACTION_PLAY_NEXT			= "android.intent.action.RC_PLAY_NEXT";
	public static final String ACTION_PLAY_PREVIOUS		= "android.intent.action.RC_PLAY_PREVIOUS";
	public static final String ACTION_PLAY_URL			= "android.intent.action.RC_PLAY_URL";
	//key:"URL" value:"http://v.youku.com/v_show/id_XMzY2NDU0MTQ0.html"
	//for data
	public static final String ACTION_DATA				= "android.intent.action.RC_DATA";

	public static final String ACTION_CLIENT_CONNECT	= "com.bestv.msg.phone.connect";
	public static final String ACTION_CLIENT_DISCONNECT	= "com.bestv.msg.phone.disconnect";

	private static final int SERVICE_TYPE_PLAY_FAST		= 0;
	private static final int SERVICE_TYPE_PLAY_SLOW		= 1;
	private static final int SERVICE_TYPE_PLAY_NEXT		= 2;
	private static final int SERVICE_TYPE_PLAY_PREVIOUS	= 3;
	private static final int SERVICE_TYPE_PLAY_URL		= 4;
	private static final int SERVICE_TYPE_DATA			= 5;

	//this must sync. with remote_control_local.h
	private static final int SERVICE_TYPE_CONNECT		= 0x80;
	private static final int SERVICE_TYPE_DISCONNECT	= 0x81;
	
	/*
	*	remote control thread, read data from socket(remote_control_service)
	*/	
	//must sync with remote_control.h
	private static final int EVENT_TYPE_SERVICE = 8;	
	private InputStream mIn;
	private OutputStream mOut;
	private LocalSocket mSocket;

	private Context mContext;
	
    /**
     * Constructs a new RemoteControlService instance.
     * 
     * @param context  Binder context for this service
     */
    public RemoteControlService(Context context) {
        mContext = context;

		Log.d(TAG, "created a remote control service:" + context);

		if( connect() ){
			// create a receive message thread from remote control daemon.
			new Thread(new MessageThread()).start();

			// install an intent filter to receive daemon enable or disable event
			IntentFilter intentFilter = new IntentFilter(ACTION_DAEMON_ENABLE);
			intentFilter.addAction(ACTION_DAEMON_DISABLE);
	        context.registerReceiver(mEnableReceiver, intentFilter);
		}
	}

	/*
	*	remote control daemon can receive client connect
	*/
	public void enableConnection(){
		sendData(true);
	}

	/*
	*	remote control daemon can not receive any client connect
	*/
	public void disableConnection(){
		sendData(false);
	}

    private boolean sendData(boolean enable) {
		try {
			if( null != mOut ) {
				byte len[] = new byte[4];
				int dataLen = 1;
				
				len[0] = (byte)((dataLen>>24)&0xff);
				len[1] = (byte)((dataLen>>16)&0xff);
				len[2] = (byte)((dataLen>>8)&0xff);
				len[3] = (byte)(dataLen&0xff);
				
				mOut.write(len, 0, len.length);
				//write to buffer
				mOut.write(enable?1:0);
				//write to stream
				mOut.flush();
			}
		}
		catch(IOException e) {
			Log.e(TAG, "sendData fail:" + e.getMessage());
			return false;
		}
		return true;
	}

 	private boolean connect() {
        if (mSocket != null) {
            return true;
        }
		
        Log.i(TAG, "connecting...");
        try {
            mSocket = new LocalSocket();

            /*LocalSocketAddress address = new LocalSocketAddress(
                "remote_control_service", LocalSocketAddress.Namespace.RESERVED);*/
            LocalSocketAddress address = new LocalSocketAddress("remote_control_service");

            mSocket.connect(address);

            mIn = mSocket.getInputStream();
            mOut = mSocket.getOutputStream();
        } catch (IOException ex) {
        	Log.e(TAG, "connect exception:" + ex);
            disconnect();
            return false;
        }
        return true;
    }

	private void disconnect() {
        Log.i(TAG,"disconnecting...");
		try {
			if (mSocket != null) mSocket.close();
		} catch (IOException ex) { }
		
		try {
			if (mIn != null) mIn.close();
		} catch (IOException ex) { }
		
		try {
			if (mOut != null) mOut.close();
		} catch (IOException ex) { }
		
		mSocket = null;
		mIn = null;
		mOut = null;
	}

	private final BroadcastReceiver mEnableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
			
			Log.i(TAG, "BroadcastReceiver onReceive:" + action);

            if (action.equals(ACTION_DAEMON_ENABLE)){
				sendData(true);
            } 
			else if(action.equals(ACTION_DAEMON_DISABLE)){
				sendData(false);
			}
        }
	};

	Handler mHandler = new Handler(){
		public void handleMessage(Message msg){
			String[] action = new String[]{
				ACTION_PLAY_FAST,
				ACTION_PLAY_SLOW,
				ACTION_PLAY_NEXT,
				ACTION_PLAY_PREVIOUS,
				ACTION_PLAY_URL,
				ACTION_DATA
			};
			
			if( null == mContext ) {
				Log.e(TAG, "can not found the context for send broadcast!");
			}

			if( (SERVICE_TYPE_CONNECT == msg.what) ||
				(SERVICE_TYPE_DISCONNECT == msg.what)){
				String connectAction = (SERVICE_TYPE_CONNECT == msg.what)?ACTION_CLIENT_CONNECT:ACTION_CLIENT_DISCONNECT;
				Intent it = new Intent(connectAction);
				it.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
				mContext.sendBroadcast(it);
				Log.d(TAG, "sendBroadcast:" + it);
			}
			else{
				if( msg.what > SERVICE_TYPE_DATA ){
					Log.e(TAG, "service data type error");
				}
				else{
					Intent intent = new Intent(action[msg.what]);
					intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
					Bundle bundle = new Bundle();
					bundle.putString("data", (String)msg.obj);
					intent.putExtras(bundle);	
					mContext.sendBroadcast(intent);
					Log.d(TAG, "sendBroadcast:" + intent);
				}
			}
	
			super.handleMessage(msg);
		}			
	};
	
	class MessageThread extends Thread {

		public MessageThread() {
		    super("MessageThread");
	 	}

		public void run(){
			
			byte data[] = new byte[4];
			while(true){
				int dataLen;
				int bytesLeft = 0;
				int bytesRead = 0;
				byte inStream[] = null;

				if( (null == mSocket) ||(null == mIn) || (null == mOut)){
					break;
				}
				
				try {
					if( mIn.read(data, 0, 4) < 4 ){
						Log.e(TAG, "RC, read data length fail");
					}
				} catch (IOException ex) {
	                Log.e(TAG, "RC, read length exception" + ex);
					break;
				}

				dataLen = 0;
				for(int i = 0; i < 4; i++){
					dataLen += (data[i]&0xff)<<(8*i); 
				}

				//Log.i(TAG, "RC, receive data length = " + dataLen);
				
				bytesLeft = dataLen;
				inStream = new byte[dataLen];
				while(bytesLeft > 0){			
					try {
						bytesRead = mIn.read(inStream, bytesRead, bytesLeft);
						bytesLeft -= bytesRead; 
					} catch (IOException ex) {
		                Log.e(TAG, "RC, read data exception" + ex);
						break;
					}
				}
				
				processData(inStream, dataLen);
			}
		}

		public void processData(byte inStream[], int len){
			if( EVENT_TYPE_SERVICE != inStream[0] ){
				Log.w(TAG, "RC, data type is not service");
				return;
			}

			int what = (inStream[1]&0xff);
			String data = new String(inStream, 2, len - 2);//inStream[0]:data type ,inStream[1]:service type

			Log.i(TAG, "RC, process what:" + what + ",data:" + data);
			sendMessage(what, data);
		}
		
		public void sendMessage(int what, String data){
			Message message = Message.obtain();
			message.what = what;
			message.obj = data;
			RemoteControlService.this.mHandler.sendMessage(message);
		}
	}
}

