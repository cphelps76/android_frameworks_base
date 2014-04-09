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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemProperties;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import dalvik.system.CloseGuard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

import android.content.pm.ApplicationInfo;

/**
 * Sensor manager implementation that communicates with the built-in
 * system sensors.
 *
 * @hide
 */
public class SystemSensorManager extends SensorManager {
    private static native void nativeClassInit();
    private static native int nativeGetNextSensor(Sensor sensor, int next);

    private static boolean sSensorModuleInitialized = false;
    private static final Object sSensorModuleLock = new Object();
    private static final ArrayList<Sensor> sFullSensorsList = new ArrayList<Sensor>();
    private static final SparseArray<Sensor> sHandleToSensor = new SparseArray<Sensor>();
    private boolean hasAccelerometer = SystemProperties.getBoolean("hw.has.accelerometer", true);
    private boolean hasGyro = SystemProperties.getBoolean("hw.has.gyro", true);
    private static final boolean RC_SENSOR_DEBUG = false;
    // Listener list
    private final HashMap<SensorEventListener, SensorEventQueue> mSensorListeners =
            new HashMap<SensorEventListener, SensorEventQueue>();
    private final HashMap<TriggerEventListener, TriggerEventQueue> mTriggerListeners =
            new HashMap<TriggerEventListener, TriggerEventQueue>();

    // Looper associated with the context in which this instance was created.
    private final Looper mMainLooper;
    private final int mTargetSdkLevel;

    //sensor write list featrue
    private static final int CONFIG_REVERSE_X = 0x0001;
    private static final int CONFIG_REVERSE_Y = 0x0002;
    private static final int CONFIG_REVERSE_XY = 0x0004;
    private static final String SensorAccessFile = "/system/etc/sensor_access.txt";
    private static int mWhitePackage = 0x0000;
    private HashMap<String,SensorAccessPkgCfg> mSensorAccessList = getMap(SensorAccessFile);
    private static Context mContext;

    public class SensorAccessPkgCfg {
	public String mStrPkgName = "";
	public int mVersionCode = -1;//-1 mean all version
	public int mSensorConfig = 0;

	public SensorAccessPkgCfg(String strPkgName, int versionCode, int sensorConfig) {
		mStrPkgName = strPkgName;
		mVersionCode = versionCode;
		mSensorConfig = sensorConfig;
	}
    }

    /** {@hide} */
    public SystemSensorManager(Context context, Looper mainLooper) {
        mMainLooper = mainLooper;
        mTargetSdkLevel = context.getApplicationInfo().targetSdkVersion;
        synchronized(sSensorModuleLock) {
            if (!sSensorModuleInitialized) {
                sSensorModuleInitialized = true;

                nativeClassInit();

                // initialize the sensor list
                final ArrayList<Sensor> fullList = sFullSensorsList;
                int i = 0;
                do {
                    Sensor sensor = new Sensor();
                    i = nativeGetNextSensor(sensor, i);
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
                        //Sensor sensor = new Sensor();
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
            }
        }

	mContext = null;
        try {
             ApplicationInfo appInfo = context.getApplicationInfo();
             if ((appInfo.flags&ApplicationInfo.FLAG_SYSTEM) == 0){
                    //third part
                    mContext = context;
             }
        }catch(Exception ex){
        }
    }


    /** @hide */
    @Override
    protected List<Sensor> getFullSensorList() {
        return sFullSensorsList;
    }


    /** @hide */
    @Override
    protected boolean registerListenerImpl(SensorEventListener listener, Sensor sensor,
            int delayUs, Handler handler, int maxBatchReportLatencyUs, int reservedFlags) {
        if (listener == null || sensor == null) {
            Log.e(TAG, "sensor or listener is null");
            return false;
        }
        // Trigger Sensors should use the requestTriggerSensor call.
        if (Sensor.getReportingMode(sensor) == Sensor.REPORTING_MODE_ONE_SHOT) {
            Log.e(TAG, "Trigger Sensors should use the requestTriggerSensor.");
            return false;
        }
        if (maxBatchReportLatencyUs < 0 || delayUs < 0) {
            Log.e(TAG, "maxBatchReportLatencyUs and delayUs should be non-negative");
            return false;
        }

        // Invariants to preserve:
        // - one Looper per SensorEventListener
        // - one Looper per SensorEventQueue
        // We map SensorEventListener to a SensorEventQueue, which holds the looper
        boolean result = false;
        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue == null) {
                Looper looper = (handler != null) ? handler.getLooper() : mMainLooper;
                queue = new SensorEventQueue(listener, looper, this);
                boolean bVisualSensor = false;
                if(((!hasAccelerometer) || (sensor.getHandle() == 1))&& (Sensor.TYPE_ACCELEROMETER == sensor.getType())||
                    ((!hasGyro) && (Sensor.TYPE_GYROSCOPE == sensor.getType()))){
                        registerRemote(listener);
                        bVisualSensor = true;
                }
                if (!queue.addSensor(sensor, delayUs, maxBatchReportLatencyUs, reservedFlags)) {
                    if(bVisualSensor){
                        isReallySensor = false;
                        queue.addRCSensor(sensor);
                        result = true;
                    }else{
                        queue.dispose();
                        result = false;
                    }
                }else{
                    isReallySensor = true;
                    result = true;
                }
                mSensorListeners.put(listener, queue);
            } else {
                result = queue.addSensor(sensor, delayUs, maxBatchReportLatencyUs, reservedFlags);
            }
        }
        return result;
    }

    /** @hide */
    @Override
    protected void unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
        // Trigger Sensors should use the cancelTriggerSensor call.
        if (sensor != null && Sensor.getReportingMode(sensor) == Sensor.REPORTING_MODE_ONE_SHOT) {
            return;
        }

        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue != null) {
                boolean result;
                if (sensor == null) {
                    result = queue.removeAllSensors();
                } else {
                    result = queue.removeSensor(sensor, true);
                }
                if (result && !queue.hasSensors()) {
                    if (sensor == null ||((!hasAccelerometer) && (Sensor.TYPE_ACCELEROMETER == sensor.getType()))||
                         ((!hasGyro) && (Sensor.TYPE_GYROSCOPE == sensor.getType()))){
                             unregisterRemote(listener);
                             //return;
                     }
                    mSensorListeners.remove(listener);
                    queue.dispose();
                }
            }

	        if (mWhitePackage != 0x0000) {
                mWhitePackage = 0x0000;
            }
        }
    }

    /** @hide */
    @Override
    protected boolean requestTriggerSensorImpl(TriggerEventListener listener, Sensor sensor) {
        if (sensor == null) throw new IllegalArgumentException("sensor cannot be null");

        if (Sensor.getReportingMode(sensor) != Sensor.REPORTING_MODE_ONE_SHOT) return false;

        synchronized (mTriggerListeners) {
            TriggerEventQueue queue = mTriggerListeners.get(listener);
            if (queue == null) {
                queue = new TriggerEventQueue(listener, mMainLooper, this);
                if (!queue.addSensor(sensor, 0, 0, 0)) {
                    queue.dispose();
                    return false;
                }
                mTriggerListeners.put(listener, queue);
                return true;
            } else {
                return queue.addSensor(sensor, 0, 0, 0);
            }
        }
    }

    /** @hide */
    @Override
    protected boolean cancelTriggerSensorImpl(TriggerEventListener listener, Sensor sensor,
            boolean disable) {
        if (sensor != null && Sensor.getReportingMode(sensor) != Sensor.REPORTING_MODE_ONE_SHOT) {
            return false;
        }
        synchronized (mTriggerListeners) {
            TriggerEventQueue queue = mTriggerListeners.get(listener);
            if (queue != null) {
                boolean result;
                if (sensor == null) {
                    result = queue.removeAllSensors();
                } else {
                    result = queue.removeSensor(sensor, disable);
                }
                if (result && !queue.hasSensors()) {
                    mTriggerListeners.remove(listener);
                    queue.dispose();
                }
                return result;
            }
            return false;
        }
    }

    protected boolean flushImpl(SensorEventListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");

        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue == null) {
                return false;
            } else {
                return (queue.flush() == 0);
            }
        }
    }

    /*
     * BaseEventQueue is the communication channel with the sensor service,
     * SensorEventQueue, TriggerEventQueue are subclases and there is one-to-one mapping between
     * the queues and the listeners.
     */
    private static abstract class BaseEventQueue {
        private native int nativeInitBaseEventQueue(BaseEventQueue eventQ, MessageQueue msgQ,
                float[] scratch);
        private static native int nativeEnableSensor(int eventQ, int handle, int rateUs,
                int maxBatchReportLatencyUs, int reservedFlags);
        private static native int nativeDisableSensor(int eventQ, int handle);
        private static native void nativeDestroySensorEventQueue(int eventQ);
        private static native int nativeFlushSensor(int eventQ);
        private int nSensorEventQueue;
        private final SparseBooleanArray mActiveSensors = new SparseBooleanArray();
        protected final SparseIntArray mSensorAccuracies = new SparseIntArray();
        protected final SparseBooleanArray mFirstEvent = new SparseBooleanArray();
        private final CloseGuard mCloseGuard = CloseGuard.get();
        private final float[] mScratch = new float[16];
        protected final SystemSensorManager mManager;

        BaseEventQueue(Looper looper, SystemSensorManager manager) {
            nSensorEventQueue = nativeInitBaseEventQueue(this, looper.getQueue(), mScratch);
            mCloseGuard.open("dispose");
            mManager = manager;
        }

        public void dispose() {
            dispose(false);
        }

        /** @hide */
        public SparseBooleanArray getActivitySensor(){
            return mActiveSensors;
        }

        boolean addRCSensor(Sensor sensor) {
            // Check if already present.
            int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) return false;

            // Get ready to receive events before calling enable.
            mActiveSensors.put(handle, true);
            addSensorEvent(sensor);
            return true;
        }

        public boolean addSensor(
                Sensor sensor, int delayUs, int maxBatchReportLatencyUs, int reservedFlags) {
            // Check if already present.
            int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) return false;

            // Get ready to receive events before calling enable.
            mActiveSensors.put(handle, true);
            addSensorEvent(sensor);
            if (enableSensor(sensor, delayUs, maxBatchReportLatencyUs, reservedFlags) != 0) {
                // Try continuous mode if batching fails.
                if (maxBatchReportLatencyUs == 0 ||
                    maxBatchReportLatencyUs > 0 && enableSensor(sensor, delayUs, 0, 0) != 0) {
                  removeSensor(sensor, false);
                  return false;
                }
            }
            return true;
        }

        public boolean removeAllSensors() {
            for (int i=0 ; i<mActiveSensors.size(); i++) {
                if (mActiveSensors.valueAt(i) == true) {
                    int handle = mActiveSensors.keyAt(i);
                    Sensor sensor = sHandleToSensor.get(handle);
                    if (sensor != null) {
                        disableSensor(sensor);
                        mActiveSensors.put(handle, false);
                        removeSensorEvent(sensor);
                    } else {
                        // it should never happen -- just ignore.
                    }
                }
            }
            return true;
        }

        public boolean removeSensor(Sensor sensor, boolean disable) {
            final int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) {
                if (disable) disableSensor(sensor);
                mActiveSensors.put(sensor.getHandle(), false);
                removeSensorEvent(sensor);
                return true;
            }
            return false;
        }

        public int flush() {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            return nativeFlushSensor(nSensorEventQueue);
        }

        public boolean hasSensors() {
            // no more sensors are set
            return mActiveSensors.indexOfValue(true) >= 0;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                dispose(true);
            } finally {
                super.finalize();
            }
        }

        private void dispose(boolean finalized) {
            if (mCloseGuard != null) {
                if (finalized) {
                    mCloseGuard.warnIfOpen();
                }
                mCloseGuard.close();
            }
            if (nSensorEventQueue != 0) {
                nativeDestroySensorEventQueue(nSensorEventQueue);
                nSensorEventQueue = 0;
            }
        }

        private int enableSensor(
                Sensor sensor, int rateUs, int maxBatchReportLatencyUs, int reservedFlags) {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            if (sensor == null) throw new NullPointerException();
            return nativeEnableSensor(nSensorEventQueue, sensor.getHandle(), rateUs,
                    maxBatchReportLatencyUs, reservedFlags);
        }

        private int disableSensor(Sensor sensor) {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            if (sensor == null) throw new NullPointerException();
            return nativeDisableSensor(nSensorEventQueue, sensor.getHandle());
        }
        protected abstract void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp);
        protected abstract void dispatchFlushCompleteEvent(int handle);

        protected abstract void addSensorEvent(Sensor sensor);
        protected abstract void removeSensorEvent(Sensor sensor);
    }

    static final class SensorEventQueue extends BaseEventQueue {
        private final SensorEventListener mListener;
        private final SparseArray<SensorEvent> mSensorsEvents = new SparseArray<SensorEvent>();

        public SensorEventQueue(SensorEventListener listener, Looper looper,
                SystemSensorManager manager) {
            super(looper, manager);
            mListener = listener;
        }

        @Override
        public void addSensorEvent(Sensor sensor) {
            SensorEvent t = new SensorEvent(Sensor.getMaxLengthValuesArray(sensor,
                    mManager.mTargetSdkLevel));
            synchronized (mSensorsEvents) {
                mSensorsEvents.put(sensor.getHandle(), t);
            }
        }

        @Override
        public void removeSensorEvent(Sensor sensor) {
            synchronized (mSensorsEvents) {
                mSensorsEvents.delete(sensor.getHandle());
            }
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchSensorEvent(int handle, float[] values, int inAccuracy,
                long timestamp) {
            final Sensor sensor = sHandleToSensor.get(handle);
            SensorEvent t = null;
            synchronized (mSensorsEvents) {
                t = mSensorsEvents.get(handle);
            }

            if (t == null) {
                // This may happen if the client has unregistered and there are pending events in
                // the queue waiting to be delivered. Ignore.
                return;
            }
            // Copy from the values array.
            System.arraycopy(values, 0, t.values, 0, t.values.length);
            t.timestamp = timestamp;
            t.accuracy = inAccuracy;
            t.sensor = sensor;
            switch (t.sensor.getType()) {
                // Only report accuracy for sensors that support it.
                case Sensor.TYPE_MAGNETIC_FIELD:
                case Sensor.TYPE_ORIENTATION:
                    // call onAccuracyChanged() only if the value changes
                    final int accuracy = mSensorAccuracies.get(handle);
                    if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                        mSensorAccuracies.put(handle, t.accuracy);
                        mListener.onAccuracyChanged(t.sensor, t.accuracy);
                    }
                    break;
                default:
                    // For other sensors, just report the accuracy once
                    if (mFirstEvent.get(handle) == false) {
                        mFirstEvent.put(handle, true);
                        mListener.onAccuracyChanged(
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
            }

            mListener.onSensorChanged(t);
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {
            if (mListener instanceof SensorEventListener2) {
                final Sensor sensor = sHandleToSensor.get(handle);
                ((SensorEventListener2)mListener).onFlushCompleted(sensor);
            }
            return;
        }
    }

    static final class TriggerEventQueue extends BaseEventQueue {
        private final TriggerEventListener mListener;
        private final SparseArray<TriggerEvent> mTriggerEvents = new SparseArray<TriggerEvent>();

        public TriggerEventQueue(TriggerEventListener listener, Looper looper,
                SystemSensorManager manager) {
            super(looper, manager);
            mListener = listener;
        }

        @Override
        public void addSensorEvent(Sensor sensor) {
            TriggerEvent t = new TriggerEvent(Sensor.getMaxLengthValuesArray(sensor,
                    mManager.mTargetSdkLevel));
            synchronized (mTriggerEvents) {
                mTriggerEvents.put(sensor.getHandle(), t);
            }
        }

        @Override
        public void removeSensorEvent(Sensor sensor) {
            synchronized (mTriggerEvents) {
                mTriggerEvents.delete(sensor.getHandle());
            }
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp) {
            final Sensor sensor = sHandleToSensor.get(handle);
            TriggerEvent t = null;
            synchronized (mTriggerEvents) {
                t = mTriggerEvents.get(handle);
            }
            if (t == null) {
                Log.e(TAG, "Error: Trigger Event is null for Sensor: " + sensor);
                return;
            }

            // Copy from the values array.
            System.arraycopy(values, 0, t.values, 0, t.values.length);
            t.timestamp = timestamp;
            t.sensor = sensor;

            // A trigger sensor is auto disabled. So just clean up and don't call native
            // disable.
            mManager.cancelTriggerSensorImpl(mListener, sensor, false);

            mListener.onTrigger(t);
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {
        }
    }
    /*
    *   add for remote control
    *   remote control thread, read data from socket(remote_control_sensor)
    */
    //must sync with remote_control.h
    private static final int EVENT_TYPE_SENSOR = 4;
    private InputStream mIn;
    private OutputStream mOut;
    private LocalSocket mSocket;
    private boolean mThreadStart = false;
    private boolean mRegisterListener = false;
    private SensorEventListener mRCListener;
    private boolean isReallySensor = false;
    private RemoteControlThread mRCThread;
    class RemoteControlThread extends Thread {
        public RemoteControlThread() {
            mRCListener = null;
        }

        public void setRCListener(SensorEventListener listener){
            mRCListener = listener;
        }

        public void run() {
            if(connect()){
                Log.i(TAG, "RC, connect to remote_control_sensor socket ok");
            }
            mThreadStart = true;
            byte data[] = new byte[4];
            while(mThreadStart){
                int dataLen;
                int bytesLeft = 0;
                int bytesRead = 0;
                byte inStream[] = null;

                if( (null == mSocket) ||(null == mIn) || (null == mOut)){
                    break;
                }

                try {
                    if( mIn.read(data, 0, 4) < 4 ){
                        Log.d(TAG, "RC, read data length fail");
                        break;
                    }
                } catch (IOException ex) {
                    Log.d(TAG, "RC, read length exception" + ex);
                    break;
                }catch (Exception e) {
                    Log.d(TAG, "RC, unknown exception" + e);
                    break;
                }

                dataLen = 0;
                for(int i = 0; i < 4; i++){
                    dataLen += (data[i]&0xff)<<(8*i);
                }

                //Log.d(TAG, "RC, receive data length = " + dataLen);

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

                /*if(!mRegisterListener){
                    if (RC_SENSOR_DEBUG) Log.e(TAG, "RC, application not registerListener!!!");
                    continue;
                }*/

                if(dataLen>=18 && EVENT_TYPE_SENSOR == inStream[0]){
                    byte type;
                    int intBits, accuracy;
                    final float[] values = new float[3];
                    final long timestamp;

                    type = inStream[1];
                    intBits = ((inStream[2]&0xff)<<24)|((inStream[3]&0xff)<<16)|((inStream[4]&0xff)<<8)|(inStream[5]&0xff);
                    values[0] = Float.intBitsToFloat(intBits);
                    intBits = ((inStream[6]&0xff)<<24)|((inStream[7]&0xff)<<16)|((inStream[8]&0xff)<<8)|(inStream[9]&0xff);
                    values[1] = Float.intBitsToFloat(intBits);
                    intBits = ((inStream[10]&0xff)<<24)|((inStream[11]&0xff)<<16)|((inStream[12]&0xff)<<8)|(inStream[13]&0xff);
                    values[2] = Float.intBitsToFloat(intBits);
                    accuracy = ((inStream[14]&0xff)<<24)|((inStream[15]&0xff)<<16)|((inStream[16]&0xff)<<8)|(inStream[17]&0xff);

                    if(RC_SENSOR_DEBUG) Log.i(TAG, "RC, [x:" + values[0] + ",y:" + values[1] + ",z:" + values[2] + "], accuracy = " + accuracy);

                    Sensor sensorObject = null;
                    if( Sensor.TYPE_ACCELEROMETER == type ){
                        sensorObject = new Sensor(type);
                        //sensorObject = new Sensor();
                    }
                    else if( Sensor.TYPE_GYROSCOPE == type ) {
                        sensorObject = new Sensor(type);
                        //sensorObject = new Sensor();
                    }

                    timestamp = System.currentTimeMillis();
                    synchronized (mSensorListeners) {
                        if (mRCListener != null) {
                            final int size = mSensorListeners.size();
                            for (int i = 0 ; i < size ; i++) {
                                SensorEventQueue queue  = mSensorListeners.get(mRCListener);
                                if(isReallySensor){
                                    SparseBooleanArray mActiveSensors = queue.getActivitySensor();
                                    for (int j=0 ; j<mActiveSensors.size(); j++) {
                                        if (mActiveSensors.valueAt(i) == true){
                                            int handle = mActiveSensors.keyAt(j);
                                            Sensor sensor = sHandleToSensor.get(handle);
                                            if (sensor != null) {
                                                try{
                                                queue.dispatchSensorEvent(handle, values, accuracy, timestamp);
                                                }catch(Exception ex){}
                                            }
                                        }
                                    }
                                }else{
                                    try{
                                    queue.dispatchSensorEvent(sensorObject.getHandle(), values, accuracy, timestamp);
                                    }catch(Exception ex){}
                                }
                                  /*if (listener.getSensors() != null && listener.getSensors().size() > 0) {
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
                                  }*/
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
        mThreadStart = false;
        /*try {
            if (mOut != null) {
                byte disc[] = new byte[1];
                disc[0] = 'd';
                mOut.write(disc, 0, 1);
                mOut.flush();
            }
        } catch (IOException ex) { }*/

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

    private void registerRemote(SensorEventListener listener) {
        if(!mThreadStart){
            //mRCThread.start();
            mRCThread = new RemoteControlThread();
            mRCThread.start();
            mThreadStart = true;
        }
        mRCThread.setRCListener(listener);
        mRegisterListener = true;
    }

    private void unregisterRemote(SensorEventListener listener) {
        if(mRCListener == listener){
            mRegisterListener = false;

            mThreadStart = false;
            disconnect();
       }
    }

    private HashMap<String, SensorAccessPkgCfg> getMap(String path) {
        File file = new File(path);
        BufferedReader bs = null;
        HashMap<String, SensorAccessPkgCfg> map = null;
        if (!file.exists()) {
            return null;
        }
        try {
            bs = new BufferedReader(
            new InputStreamReader(new FileInputStream(file)));
            map = new HashMap<String, SensorAccessPkgCfg>();
            String line = null;
            String[] array = (String[])null;
	    String[] array2 = (String[])null;
            while ((line = bs.readLine()) != null) {
		//skip comment
		if(line.startsWith("#")) {
			//Log.i(TAG,"skip comment:" + line);
			continue;
		}

		/*
			packageName=sensorConfig => mean if packageName match,will use sensorConfig
		     OR	packageName,versionCode=sensorConfig => mean if packageName and versionCode all match,will use sensorConfig
		*/
                array = line.split("=");
                if (array.length == 2) {
		    String strPkg = array[0].trim();
		    int sensorConfig = Integer.parseInt((String)array[1].trim());
		    
		    String strPkgName = strPkg;
		    int versionCode = -1;
		    array2 = strPkg.split(",");
		    if( array2.length == 2 ) {
			strPkgName = array2[0].trim();
			versionCode = Integer.parseInt((String)array2[1].trim());
		    }
		    SensorAccessPkgCfg cfg = new SensorAccessPkgCfg( strPkgName, versionCode, sensorConfig );
                    map.put(strPkgName+","+versionCode, cfg);
		    //Log.i(TAG, "getMap()" + " strPkgName:" + strPkgName + " versionCode:" + versionCode + " sensorConfig:" + sensorConfig );
                }
            }
          bs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }
}
