package com.android.server;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Slog;
import android.content.IntentFilter;
import android.content.ContextWrapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import android.os.Bundle;
import android.os.SystemProperties;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class SystemKeyServices {
    Context mContext = null ;
    IntentFilter filter = null ;
    SystemKeyReceiver receiver = null;
    private static boolean isSet = false ;
    ArrayList<String> mKeyNameList = null ;
    private String TAG = "SystemKeyServices";
    SystemKeyServices(Context context ){
        mContext= context;
        filter = new IntentFilter("sys.key.info");
        receiver = new SystemKeyReceiver();
        mKeyNameList = new ArrayList<String>();
    }
    
    void registerSystemKeyReceiver( ){
        mContext.registerReceiver(receiver, filter);
        Slog.d("SystemKeyServices", "registerSystemKeyReceiver() ");
    }

    void unregisterSystemKeyReceiver()
    {
        mContext.unregisterReceiver(receiver);
        Slog.d("SystemKeyServices", "unregisterSystemKeyReceiver() ");
    }

    private  String stringToAscii(String value) {
        StringBuffer sbu = new StringBuffer();
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            sbu.append(Integer.toHexString((int) chars[i]));
        }
        return sbu.toString();
    }

    private String hexStringToByte(String hex) {
        int len = (hex.length() / 2);
        byte[] result = new byte[len];
        char[] achar = hex.toCharArray();
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            result[i] = (byte) (toByte(achar[pos]) << 4 | toByte(achar[pos + 1]));
        }
        return new String(result);
    }

    private  byte toByte(char c) {
        byte b = (byte) "0123456789abcdef".indexOf(c);
        return b;
    }

	private String getRealString(String s) {
		char[] cArray = s.toCharArray();
		for (int j = 0; j < cArray.length; j = j + 2) {
			if (cArray[j] == '0' && cArray[j + 1] == '0') {
				return s.substring(0, j);
			}
		}
		return s;
	}
    public class SystemKeyReceiver extends BroadcastReceiver {
        private boolean mThreadStart = false;
	    private boolean mRegisterListener = false;
    
        @Override
        public void onReceive(Context arg0, Intent intent) {
             Slog.d("SystemKeyServices", "onReceive()");
             Bundle bundle = intent.getExtras();
             // "w,version,key_name,key_value"
             String SysKeyInfo = bundle.getString("SYS_KEY_INFO");
             String realSysInfo = null;
             if(SysKeyInfo != null)
             {
                if (SysKeyInfo != null && SysKeyInfo.length() > 2) {
                    String[] array =  SysKeyInfo.split(",");

                    
                    if(array.length >= 2 && ("w".equals(array[0])|| "r".equals(array[0])))
                    {
                        if("w".equals(array[0])){
                            if(array.length == 3)
                            {   
                                StringBuilder str = new StringBuilder();
                                str.append(array[0]);
                                str.append(",");
                                str.append("#####");
                                str.append(",");
                                str.append(array[1]);
                                str.append(",");
                                str.append(stringToAscii(array[2]));
                                realSysInfo = str.toString();
                                if (!mKeyNameList.contains(array[1])){
                                     mKeyNameList.add(array[1]);
                                }
                               
                            }else if(array.length == 4){
                                StringBuilder str = new StringBuilder();
                                str.append(array[0]);
                                str.append(",");
                                str.append(array[1]);
                                str.append(",");
                                str.append(array[2]);
                                str.append(",");
                                str.append(stringToAscii(array[3]));
                                realSysInfo = str.toString();
                                if (!mKeyNameList.contains(array[2])){
                                     mKeyNameList.add(array[2]);
                                }
                            }
                            else if(array.length == 2)
                            {
                                realSysInfo = SysKeyInfo;
                                
                            }

                        }else if(("r".equals(array[0])) && array.length == 2 ){
                            realSysInfo = SysKeyInfo ;
                        }

                        
                        if(realSysInfo!= null)
                        {   if( mKeyNameList != null && mKeyNameList.size() > 16){
                                Slog.d("SystemKeyServices", "input too many keys !!!" );
                                return ;
                            }
                            SystemProperties.set("ctl.start","sys_write_daemon");
                            Thread t = new  SocketThread(realSysInfo);
                            t.start();
                        }
                    }
                }
             }
        }

    	class SocketThread extends Thread {
            String mKeyInfo = null;
            //String context ;
            SocketThread(String key){
                mKeyInfo = key;
            }                       
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(mKeyInfo != null)
                    socketWrite(mKeyInfo);
            };

            void socketWrite(String key){
                int KEY_BYTES = 1024; 
    			LocalSocket mSocket = null;
    			LocalSocketAddress localAddress;
    			mSocket = new LocalSocket();
    			localAddress = new LocalSocketAddress("sys_write",LocalSocketAddress.Namespace.RESERVED);
    			try {
    				mSocket.connect(localAddress);
    				OutputStream mOut = mSocket.getOutputStream();
                    InputStream mIn = mSocket.getInputStream();

             //send the ask info to socket 
             mOut.write(key.getBytes());
             mOut.flush();
            //get the sys info form socket 
					   byte[] socket_buf = new byte[KEY_BYTES];
             String keyInfo = null ;
             String result_key_Info = null;
             int readNum = -1;
             readNum = mIn.read(socket_buf,0,KEY_BYTES) ;
             if(readNum >  0)
             {  
                keyInfo = new String(socket_buf);
                keyInfo =  keyInfo.substring(0, readNum);
                if(!"#!!#success".equals(keyInfo))
                {   
                    String realString =  getRealString(keyInfo.toLowerCase());  
                    result_key_Info =  hexStringToByte(realString) ;
                }
                else
                    result_key_Info = "SUCCESS!";
                    Slog.d("SystemKeyServices", "get the key info is : " + result_key_Info);
                }
                else{
                    result_key_Info = "GET_ERROR";
                    Slog.d("SystemKeyServices", "Something wrong, please check your input !");
                }

                    Intent i = new Intent();
                    i.setAction("aml.key.info");
                    i.putExtra("AML_KEY_INFO", result_key_Info);
                    mContext.sendBroadcast(i);
                    mIn.close();
                    mOut.close();
                    mSocket.close();
          } catch (IOException e) {
                 e.printStackTrace();
          	}	
      	}
    	}      
    }
}
