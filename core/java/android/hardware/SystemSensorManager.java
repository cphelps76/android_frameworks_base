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

package android.hardware;

import android.os.Looper;
import android.os.Process;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import android.net.LocalSocketAddress;
import android.net.LocalSocket;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.ApplicationInfo;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
/**
 * Sensor manager implementation that communicates with the built-in
 * system sensors.
 *
 * @hide
 */
public class SystemSensorManager extends SensorManager {
    private static final int SENSOR_DISABLE = -1;
    private static boolean sSensorModuleInitialized = false;
    private static ArrayList<Sensor> sFullSensorsList = new ArrayList<Sensor>();
    /* The thread and the sensor list are global to the process
     * but the actual thread is spawned on demand */
    private static SensorThread sSensorThread;
    private static int sQueue;

    // Used within this module from outside SensorManager, don't make private
    static SparseArray<Sensor> sHandleToSensor = new SparseArray<Sensor>();
    static final ArrayList<ListenerDelegate> sListeners =
        new ArrayList<ListenerDelegate>();

    // Common pool of sensor events.
    static SensorEventPool sPool;

    // Looper associated with the context in which this instance was created.
    final Looper mMainLooper;

    //sensor control btn settings:landscreen by default
    private static int sensorCtlId=-1;
    private static int dispScreen = 0;
    private static int mWhitePackage = 0x0000;
    private static final int CONFIG_REVERSE_X = 0x0001;
    private static final int CONFIG_REVERSE_Y = 0x0002;
    private static final int CONFIG_REVERSE_XY = 0x0004;
    private boolean mCxtFormWL = false;
    private Context mContext;

    private static final String setRotVal = "android.intent.action.RECORD_ROTATION_SETTINGS";
    private static final String setRotShow = "android.intent.action.RECORD_ROTATION_SHOW";
    private static final String setRotStr = "rot_setting";
    private static final String rotProp = "sys.rotation.settings";
    private static final String SensorAccessFile = "/system/etc/sensor_access.txt";
    private static HashMap<String,String> mSensorAccessList = getMap(SensorAccessFile);
    private static boolean mHasRegist = false;
    /*-----------------------------------------------------------------------*/
    private static boolean hasAccelerometer = SystemProperties.getBoolean("hw.has.accelerometer", true);
    private static boolean hasGyro = SystemProperties.getBoolean("hw.has.gyro", true);
    private static final boolean RC_SENSOR_DEBUG = false;
    static private class SensorThread {

        Thread mThread;
        boolean mSensorsReady;

        SensorThread() {
        }

        @Override
        protected void finalize() {
        }

        // must be called with sListeners lock
        boolean startLocked() {
            try {
                if (mThread == null) {
                    mSensorsReady = false;
                    SensorThreadRunnable runnable = new SensorThreadRunnable();
                    Thread thread = new Thread(runnable, SensorThread.class.getName());
                    thread.start();
                    synchronized (runnable) {
                        while (mSensorsReady == false) {
                            runnable.wait();
                        }
                    }
                    mThread = thread;
                }
            } catch (InterruptedException e) {
            }
            return mThread == null ? false : true;
        }

        private class SensorThreadRunnable implements Runnable {
            SensorThreadRunnable() {
            }

            private boolean open() {
                // NOTE: this cannot synchronize on sListeners, since
                // it's held in the main thread at least until we
                // return from here.
                sQueue = sensors_create_queue();
                return true;
            }

            public void run() {
                //Log.d(TAG, "entering main sensor thread");
                final float[] values = new float[3];
                final int[] status = new int[1];
                final long timestamp[] = new long[1];
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);

                if (!open()) {
                    return;
                }

                synchronized (this) {
                    // we've open the driver, we're ready to open the sensors
                    mSensorsReady = true;
                    this.notify();
                }

                while (true) {
                    // wait for an event
                    final int sensor = sensors_data_poll(sQueue, values, status, timestamp);

                    int accuracy = status[0];
                    synchronized (sListeners) {
                        if (sensor == -1 || sListeners.isEmpty()) {
                            // we lost the connection to the event stream. this happens
                            // when the last listener is removed or if there is an error
                            if (sensor == -1 && !sListeners.isEmpty()) {
                                // log a warning in case of abnormal termination
                                Log.e(TAG, "_sensors_data_poll() failed, we bail out: sensors=" + sensor);
                            }
                            // we have no more listeners or polling failed, terminate the thread
                            sensors_destroy_queue(sQueue);
                            sQueue = 0;
                            mThread = null;
                            break;
                        }
                        final Sensor sensorObject = sHandleToSensor.get(sensor);
                        if (sensorObject != null) {
                            // report the sensor event to all listeners that
                            // care about it.
                            final int size = sListeners.size();
                            for (int i=0 ; i<size ; i++) {
                                ListenerDelegate listener = sListeners.get(i);
                                if (listener.hasSensor(sensorObject)) {
                                    // this is asynchronous (okay to call
                                    // with sListeners lock held).
                                    listener.onSensorChangedLocked(sensorObject,
                                            values, timestamp, accuracy);
                                }
                            }
                        }
                    }
                }
                //Log.d(TAG, "exiting main sensor thread");
            }
        }
    }

    /*-----------------------------------------------------------------------*/

    private class ListenerDelegate {
        private final SensorEventListener mSensorEventListener;
        private final ArrayList<Sensor> mSensorList = new ArrayList<Sensor>();
        private final Handler mHandler;
        public SparseBooleanArray mSensors = new SparseBooleanArray();
        public SparseBooleanArray mFirstEvent = new SparseBooleanArray();
        public SparseIntArray mSensorAccuracies = new SparseIntArray();

        ListenerDelegate(SensorEventListener listener, Sensor sensor, Handler handler) {
            mSensorEventListener = listener;
            Looper looper = (handler != null) ? handler.getLooper() : mMainLooper;
            // currently we create one Handler instance per listener, but we could
            // have one per looper (we'd need to pass the ListenerDelegate
            // instance to handleMessage and keep track of them separately).
            mHandler = new Handler(looper) {
                @Override
                public void handleMessage(Message msg) {
                    final SensorEvent t = (SensorEvent)msg.obj;
                    final int handle = t.sensor.getHandle();

                    switch (t.sensor.getType()) {
                        // Only report accuracy for sensors that support it.
                        case Sensor.TYPE_MAGNETIC_FIELD:
                        case Sensor.TYPE_ORIENTATION:
                            // call onAccuracyChanged() only if the value changes
                            final int accuracy = mSensorAccuracies.get(handle);
                            if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                                mSensorAccuracies.put(handle, t.accuracy);
                                mSensorEventListener.onAccuracyChanged(t.sensor, t.accuracy);
                            }
                            break;
                        default:
                            // For other sensors, just report the accuracy once
                            if (mFirstEvent.get(handle) == false) {
                                mFirstEvent.put(handle, true);
                                mSensorEventListener.onAccuracyChanged(
                                        t.sensor, SENSOR_STATUS_ACCURACY_HIGH);
                            }
                            break;
                    }
                    if (mContext != null && ( mWhitePackage != 0x0000 ) && t.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        if ((mWhitePackage & CONFIG_REVERSE_X) != 0) {
                            t.values[0] = -t.values[0];
                        }
                        if ((mWhitePackage & CONFIG_REVERSE_Y) != 0) {
                            t.values[1] = -t.values[1];
                        }
                        if ((mWhitePackage & CONFIG_REVERSE_XY) != 0) {
                            float temp_val = 0;
                            temp_val = t.values[0];
                            t.values[0] = t.values[1];
                            t.values[1] = temp_val;
                        }
                    } else if (SystemSensorManager.sensorCtlId > 0&&t.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                        //change values when rotation changed by user switch rotation btn
                        float temp_val = 0;
                        //Log.d("rot","screenLand?"+(SystemSensorManager.screenLand)+ "rot "+(SystemSensorManager.sensorCtlId));
                        if ((SystemSensorManager.dispScreen&1) != 0){
                            switch (SystemSensorManager.sensorCtlId){
                                case Surface.ROTATION_90:
                                    temp_val = t.values[1];
                                    t.values[0] = -temp_val;
                                    t.values[1] = t.values[0];
                                    break;
                                case Surface.ROTATION_180:
                                    temp_val = t.values[0];
                                    t.values[0] = t.values[1];
                                    t.values[1] = temp_val;
                                    break;
                                case Surface.ROTATION_270:
                                    t.values[0] = -t.values[0];
                                    t.values[1] = t.values[1];
                                    break;
                            }
                        }else {
                              switch (SystemSensorManager.sensorCtlId){
                              case Surface.ROTATION_90:
                                  temp_val = t.values[0];
                                  t.values[0] = t.values[1];
                                  t.values[1] = -temp_val;
                                  break;
                              case Surface.ROTATION_180:
                                  t.values[0] = -t.values[0];
                                  t.values[1] = -t.values[1];
                                  break;
                              case Surface.ROTATION_270:
                                  t.values[0] = -t.values[0];
                                  t.values[1] = t.values[1];
                                  break;
                              }
                           }
                    }
                    mSensorEventListener.onSensorChanged(t);
                    sPool.returnToPool(t);
                }
            };
            addSensor(sensor);
        }

        Object getListener() {
            return mSensorEventListener;
        }

        void addSensor(Sensor sensor) {
            mSensors.put(sensor.getHandle(), true);
            mSensorList.add(sensor);
        }
        int removeSensor(Sensor sensor) {
            mSensors.delete(sensor.getHandle());
            mSensorList.remove(sensor);
            return mSensors.size();
        }
        boolean hasSensor(Sensor sensor) {
            return mSensors.get(sensor.getHandle());
        }
        List<Sensor> getSensors() {
            return mSensorList;
        }

        void onSensorChangedLocked(Sensor sensor, float[] values, long[] timestamp, int accuracy) {
            SensorEvent t = sPool.getFromPool();
            final float[] v = t.values;
            v[0] = values[0];
            v[1] = values[1];
            v[2] = values[2];
            t.timestamp = timestamp[0];
            t.accuracy = accuracy;
            t.sensor = sensor;
            Message msg = Message.obtain();
            msg.what = 0;
            msg.obj = t;
            msg.setAsynchronous(true);
            mHandler.sendMessage(msg);
        }
    }
    private void regSensorCtlReceiver(Context cxt){
        if (!mHasRegist){
            IntentFilter rotFilter = new IntentFilter();
            rotFilter.addAction(setRotVal);
            cxt.registerReceiver(mcastReceiver,rotFilter);
            mHasRegist = true;
        }
    }
    private BroadcastReceiver mcastReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (setRotVal.equals(action)){
                int rot = intent.getIntExtra(setRotStr,-1);
                if (!mCxtFormWL && -1 != rot && null != mContext){
                    Display mDisplay = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    dispScreen = mDisplay.getRotation();
                }else{
                    dispScreen = 0;
                }
                //Log.d("rot","receive sensor info:"+rot+" landScreen"+dispScreen);
                sensorCtlId=rot;
            }
        }
    };

    /**
     * {@hide} ADD FOR CUSTOM SENSOR CONTROL
     * judge Context obj whether system application or not
     */
    public void setContext(Context context){
        Boolean setbyuser = SystemProperties.getBoolean(rotProp, false);
        mContext = null;
        try {
            ApplicationInfo appInfo = context.getApplicationInfo();
             if ((appInfo.flags&ApplicationInfo.FLAG_SYSTEM) == 0){
                    //third part
                    mContext = context;
                    if(!setbyuser){
                        mCxtFormWL = true;
                    }else{
                        mCxtFormWL = false;
                    }
             }
        }catch(Exception ex){
        }
    }

    /**
     * {@hide}
     */
    public SystemSensorManager(Looper mainLooper) {
        mMainLooper = mainLooper;

        synchronized(sListeners) {
            if (!sSensorModuleInitialized) {
                sSensorModuleInitialized = true;

                nativeClassInit();

                // initialize the sensor list
                sensors_module_init();
                final ArrayList<Sensor> fullList = sFullSensorsList;
                int i = 0;
                do {
                    Sensor sensor = new Sensor();
                    i = sensors_module_get_next_sensor(sensor, i);

                    if (i>=0) {
                        //Log.d(TAG, "found sensor: " + sensor.getName() +
                        //        ", handle=" + sensor.getHandle());
                        fullList.add(sensor);
                        sHandleToSensor.append(sensor.getHandle(), sensor);
                    }
                } while (i>0);

                if(!hasAccelerometer){
                    boolean gSensor = false;
                    for (Sensor s : fullList) {
                        if (s.getType() == Sensor.TYPE_ACCELEROMETER){
                            gSensor = true;
                            break;
                        }
                    }

                    if(!gSensor){
                        //simulater a sensor with remote control
                        Sensor sensor = new Sensor(Sensor.TYPE_ACCELEROMETER);
                    //  Sensor sensor = new Sensor();
                        fullList.add(sensor);
                        sHandleToSensor.append(sensor.getHandle(), sensor);
                        Log.i(TAG, "RC, really has no accelerometer");
                    }
                    else{
                        hasAccelerometer = true;//really has accelerometer
                        Log.i(TAG, "RC, really has accelerometer");
                    }
                }

                if(!hasGyro){
                    boolean gyroSensor = false;
                    for (Sensor s : fullList) {
                        if (s.getType() == Sensor.TYPE_GYROSCOPE){
                            gyroSensor = true;
                            break;
                        }
                    }
                    if(!gyroSensor){
                        //simulater a sensor with remote control
                        Sensor sensor = new Sensor(Sensor.TYPE_GYROSCOPE);
                        fullList.add(sensor);
                        sHandleToSensor.append(sensor.getHandle(), sensor);
                        Log.i(TAG, "RC, really has no gyro");
                    }
                    else{
                        hasGyro = true;//really has gyro
                        Log.i(TAG, "RC, really has gyro");
                    }
                }
                sPool = new SensorEventPool( sFullSensorsList.size()*2 );
                sSensorThread = new SensorThread();
            }
        }
    }

    /** @hide */
    @Override
    protected List<Sensor> getFullSensorList() {
        return sFullSensorsList;
    }

    private boolean enableSensorLocked(Sensor sensor, int delay) {
        boolean result = false;
        for (ListenerDelegate i : sListeners) {
            if (i.hasSensor(sensor)) {
                String name = sensor.getName();
                int handle = sensor.getHandle();
                result = sensors_enable_sensor(sQueue, name, handle, delay);
                break;
            }
        }
        return result;
    }

    private boolean disableSensorLocked(Sensor sensor) {
        for (ListenerDelegate i : sListeners) {
            if (i.hasSensor(sensor)) {
                // not an error, it's just that this sensor is still in use
                return true;
            }
        }
        String name = sensor.getName();
        int handle = sensor.getHandle();
        return sensors_enable_sensor(sQueue, name, handle, SENSOR_DISABLE);
    }

    /** @hide */
    @Override
    protected boolean registerListenerImpl(SensorEventListener listener, Sensor sensor,
            int delay, Handler handler) {
        boolean result = true;
        synchronized (sListeners) {
            // look for this listener in our list
            ListenerDelegate l = null;
            for (ListenerDelegate i : sListeners) {
                if (i.getListener() == listener) {
                    l = i;
                    break;
                }
            }

            //Log.v(TAG, " Sensor.getType "+(sensor.getType())+" hasAccelerometer "+hasAccelerometer+" hasGyro "+hasGyro);
            /*if(((!hasAccelerometer)||(sensor.getHandle() == 1))&& (Sensor.TYPE_ACCELEROMETER == sensor.getType())||
            ((!hasGyro) && (Sensor.TYPE_GYROSCOPE == sensor.getType()))){
                    Log.v(TAG, "registerListener, listener: " + listener + ",sListeners size:" + sListeners.size() + ", SensorEventListener:" + l);             
                    String name = sensor.getName();
                    int handle = sensor.getHandle();
                    if (l == null) {
                        l = new ListenerDelegate(listener, sensor, handler);
                        sListeners.add(l);
                        if (!sListeners.isEmpty()) {
                            registerRemote();
                        }
                    } else {
                        l.addSensor(sensor);
                    }
                }*/

            // if we don't find it, add it to the list
            if (l == null) {
                l = new ListenerDelegate(listener, sensor, handler);
                sListeners.add(l);
                // if the list is not empty, start our main thread
                if (!sListeners.isEmpty()) {
                    if(((!hasAccelerometer)||(sensor.getHandle() == 1))&& (Sensor.TYPE_ACCELEROMETER == sensor.getType())||
                    ((!hasGyro) && (Sensor.TYPE_GYROSCOPE == sensor.getType()))){
                        registerRemote();
                    }
                    if (sSensorThread.startLocked()) {
                        if (!enableSensorLocked(sensor, delay)) {
                            // oops. there was an error
                            sListeners.remove(l);
                            result = false;
                        }
                    } else {
                        // there was an error, remove the listener
                        sListeners.remove(l);
                        result = false;
                    }
                } else {
                    // weird, we couldn't add the listener
                    result = false;
                }
            } else if (!l.hasSensor(sensor)) {
                l.addSensor(sensor);
                if (!enableSensorLocked(sensor, delay)) {
                    // oops. there was an error
                    l.removeSensor(sensor);
                    result = false;
                }
            }
            if(mContext!=null && result && l != null && Sensor.TYPE_ACCELEROMETER == sensor.getType() && hasAccelerometer){
                if (mCxtFormWL) {
                    String curPkgName = mContext.getApplicationInfo().packageName;
                    if (mSensorAccessList != null && mSensorAccessList.containsKey(curPkgName)) {
                        mWhitePackage = Integer.parseInt((String)mSensorAccessList.get(curPkgName));
                    }else {
                        mWhitePackage = 0x0000;
                    }
                }else if (!mCxtFormWL){
                    //we show sensor control when someone register sensor
                    if (false) Log.d("rot","registSensor and sensorCtlId="+sensorCtlId);
                    sensorCtlId = 0;
                    regSensorCtlReceiver(mContext);
                    Intent intent = new Intent(setRotShow);
                    intent.putExtra(setRotStr, 0);
                    mContext.sendBroadcast(intent);
                }
            }
        }

        return result;
    }

    /** @hide */
    @Override
    protected void unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
        synchronized (sListeners) {
            final int size = sListeners.size();
            for (int i=0 ; i<size ; i++) {
                ListenerDelegate l = sListeners.get(i);
                if (l.getListener() == listener) {
                    if (sensor == null) {
                        sListeners.remove(i);
                        // disable all sensors for this listener
                        for (Sensor s : l.getSensors()) {
                            disableSensorLocked(s);
                        }
                    } else if (l.removeSensor(sensor) == 0) {
                        // if we have no more sensors enabled on this listener,
                        // take it off the list.
                        sListeners.remove(i);
                        if (((!hasAccelerometer) || sensor.getHandle() == 1) && (Sensor.TYPE_ACCELEROMETER == sensor.getType())||
                        ((!hasGyro) && (Sensor.TYPE_GYROSCOPE == sensor.getType()))){
                                unregisterRemote();
                                //return;
                            }
                        disableSensorLocked(sensor);
                    }
                    if (mWhitePackage != 0x0000) {
                        mWhitePackage = 0x0000;
                    }
                    if (!mCxtFormWL && mContext != null&&sensorCtlId >= 0){
                        //if sensor control show then we hide it
                        Intent intent = new Intent(setRotShow);
                        intent.putExtra(setRotStr, -1);
                        mContext.sendBroadcast(intent);
                        mContext = null;
                        sensorCtlId = -1;
                    }
                    if (!mCxtFormWL && mContext != null){
                        if (mHasRegist) {
                            mContext.unregisterReceiver(mcastReceiver);
                            mHasRegist = false;
                        }
                        mContext = null;
                        sensorCtlId = -1;
                    }
                    break;
                }
            }
        }
    }

/*
    *   tellen add 20110614 for remote control
    *   remote control thread, read data from socket(remote_control_sensor)
    */
    //must sync with remote_control.h
    private static final int EVENT_TYPE_SENSOR = 4;
    private InputStream mIn;
    private OutputStream mOut;
    private LocalSocket mSocket;
    private boolean mThreadStart = false;
    private boolean mRegisterListener = false;
    //private Thread mRCThread = new Thread("RemoteControlSensorReader") {
    class RemoteControlThread extends Thread {
        public RemoteControlThread() {
            super("RemoteControlSensorReader");
        }
        public void run() {
            if(connect()){
                Log.i(TAG, "RC, connect to remote_control_sensor socket ok");
            }

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
                        break;
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
                        if (RC_SENSOR_DEBUG) Log.e(TAG, "RC, read data exception" + ex);
                        break;
                    }
                }

                if(!mRegisterListener){
                    if (RC_SENSOR_DEBUG) Log.e(TAG, "RC, application not registerListener!!!");
                    continue;
                }

                if(dataLen>=18 && EVENT_TYPE_SENSOR == inStream[0]){
                    byte type;
                    int intBits, accuracy;
                    final float[] values = new float[3];
                    final long timestamp[] = new long[1];

                    type = inStream[1];
                    intBits = ((inStream[2]&0xff)<<24)|((inStream[3]&0xff)<<16)|((inStream[4]&0xff)<<8)|(inStream[5]&0xff);
                    values[0] = Float.intBitsToFloat(intBits);
                    intBits = ((inStream[6]&0xff)<<24)|((inStream[7]&0xff)<<16)|((inStream[8]&0xff)<<8)|(inStream[9]&0xff);
                    values[1] = Float.intBitsToFloat(intBits);
                    intBits = ((inStream[10]&0xff)<<24)|((inStream[11]&0xff)<<16)|((inStream[12]&0xff)<<8)|(inStream[13]&0xff);
                    values[2] = Float.intBitsToFloat(intBits);
                    accuracy = ((inStream[14]&0xff)<<24)|((inStream[15]&0xff)<<16)|((inStream[16]&0xff)<<8)|(inStream[17]&0xff);

                    //Log.i(TAG, "RC, [x:" + values[0] + ",y:" + values[1] + ",z:" + values[2] + "], accuracy = " + accuracy);

                    Sensor sensorObject = null;
                    if( Sensor.TYPE_ACCELEROMETER == type ){
                        sensorObject = new Sensor(type);
                        //sensorObject = new Sensor();
                    }
                    else if( Sensor.TYPE_GYROSCOPE == type ) {
                        sensorObject = new Sensor(type);
                        //sensorObject = new Sensor();
                    }

                    timestamp[0] = System.nanoTime();
                    synchronized (sListeners) {
                        if (sensorObject != null) {
                            final int size = sListeners.size();
                            for (int i = 0 ; i < size ; i++) {
                                    ListenerDelegate listener = sListeners.get(i);
                                    if (listener.getSensors() != null && listener.getSensors().size() > 0) {
                                        for (Sensor s : listener.getSensors()) {
                                            if (listener.hasSensor(s)) {
                                                Log.i(TAG, "aRC, listen size:" + size + ",get listener:" + listener.getListener());
                                                listener.onSensorChangedLocked(s, values, timestamp, accuracy);
                                            }
                                        }
                                    }else{
                                        if (listener.hasSensor(sensorObject)) {
                                            Log.i(TAG, "RC, listen size:" + size + ",get listener:" + listener.getListener());
                                            listener.onSensorChangedLocked(sensorObject, values, timestamp, accuracy);
                                        }
                                    }
                            }
                        }
                    }
                }
            }
            disconnect();
        }
    };

    private boolean connect() {
        if (mSocket != null) {
            return true;
        }
        if (RC_SENSOR_DEBUG) Log.i(TAG, "RC, connecting...");
        try {
            mSocket = new LocalSocket();

            /*LocalSocketAddress address = new LocalSocketAddress(
                "remote_control_sensor", LocalSocketAddress.Namespace.RESERVED);*/
            LocalSocketAddress address = new LocalSocketAddress("remote_control_sensor");
            mSocket.connect(address);

            mIn = mSocket.getInputStream();
            mOut = mSocket.getOutputStream();
        } catch (IOException ex) {
            if (RC_SENSOR_DEBUG) Log.e(TAG, "RC, connect exception" + ex);
            disconnect();
            return false;
        }
        return true;
    }

    private void disconnect() {
        if (RC_SENSOR_DEBUG) Log.i(TAG,"RC, disconnecting...");

        try {
            if (mOut != null) {
                byte disc[] = new byte[1];
                disc[0] = 'd';
                mOut.write(disc, 0, 1);
                mOut.flush();
            }
        } catch (IOException ex) { }

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

    private void registerRemote() {
        if(!mThreadStart){
            //mRCThread.start();
            new Thread(new RemoteControlThread()).start();
            mThreadStart = true;
        }

        mRegisterListener = true;
    }

    private void unregisterRemote() {
        mRegisterListener = false;

        mThreadStart = false;
        disconnect();
    }

    private static HashMap<String, String> getMap(String path) {
        File file = new File(path);
        BufferedReader bs = null;
        HashMap<String, String> map = null;
        if (!file.exists()) {
            return null;
        }
        try {
            bs = new BufferedReader(
            new InputStreamReader(new FileInputStream(file)));
            map = new HashMap<String, String>();
            String line = null;
            String[] array = (String[])null;
            while ((line = bs.readLine()) != null) {
                array = line.split("=");
                if (array.length == 2) {
                    map.put(array[0].trim(), array[1].trim());
                }
            }
          bs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    private static native void nativeClassInit();

    private static native int sensors_module_init();
    private static native int sensors_module_get_next_sensor(Sensor sensor, int next);

    // Used within this module from outside SensorManager, don't make private
    static native int sensors_create_queue();
    static native void sensors_destroy_queue(int queue);
    static native boolean sensors_enable_sensor(int queue, String name, int sensor, int enable);
    static native int sensors_data_poll(int queue, float[] values, int[] status, long[] timestamp);
}
