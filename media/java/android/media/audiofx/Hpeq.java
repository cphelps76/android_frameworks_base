/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.media.audiofx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.util.Log;

import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.StringTokenizer;


/**
 * Hpeq(Harp Equalizer) is a equalizer developed by Harp.
 * <p>An application creates an Hpeq object to instantiate and control an Hpeq engine
 * in the audio framework. The application can either simply use predefined presets or have a more
 * precise control of the gain in each frequency band controlled by the equalizer.
 * <p>The methods, parameter types and units exposed by the Equalizer implementation are directly
 * mapping those defined by the OpenSL ES 1.0.1 Specification (http://www.khronos.org/opensles/)
 * for the SLEqualizerItf interface. Please refer to this specification for more details.
 * <p>To attach the Equalizer to a particular AudioTrack or MediaPlayer, specify the audio session
 * ID of this AudioTrack or MediaPlayer when constructing the Equalizer.
 * <p>NOTE: attaching an Hpeq to the global audio output mix by use of session 0 is deprecated.
 * <p>See {@link android.media.MediaPlayer#getAudioSessionId()} for details on audio sessions.
 * <p>See {@link android.media.audiofx.AudioEffect} class for more details on controlling audio
 * effects.
 */

public class Hpeq extends AudioEffect {

    private final static String TAG = "HPEQ-JAVA";

    /**
     * Set/Get gain for band 1(+/-12db).
     */
    public static final int HPEQ_PARAM_GAIN_BAND_1 = 0;

    /**
     * Set/Get gain for band 2(+/-12db).
     */
    public static final int HPEQ_PARAM_GAIN_BAND_2 = 1;

    /**
     * Set/Get gain for band 3(+/-12db).
     */
    public static final int HPEQ_PARAM_BAND_GAIN_3 = 2;

    /**
     * Set/Get gain for band 4(+/-12db).
     */
    public static final int HPEQ_PARAM_BAND_GAIN_4 = 3;

    /**
     * Set/Get gain for band 5(+/-12db).
     */
    public static final int HPEQ_PARAM_BAND_GAIN_5 = 4;

    /**
     * Maximum size for preset name
     */
    public static final int PARAM_STRING_SIZE_MAX = 32;

    /**
     * Registered listener for parameter changes.
     */
    private OnParameterChangeListener mParamListener = null;

    /**
     * Listener used internally to to receive raw parameter change event from AudioEffect super class
     */
    private BaseParameterListener mBaseParamListener = null;

    /**
     * Lock for access to mParamListener
     */
    private final Object mParamListenerLock = new Object();

    /**
     * Class constructor.
     * @param priority the priority level requested by the application for controlling the HPEQ
     * engine. As the same engine can be shared by several applications, this parameter indicates
     * how much the requesting application needs control of effect parameters. The normal priority
     * is 0, above normal is a positive number, below normal a negative number.
     * @param audioSession system wide unique audio session identifier. The HPEQ will be
     * attached to the MediaPlayer or AudioTrack in the same audio session.
     *
     * @throws java.lang.IllegalStateException
     * @throws java.lang.IllegalArgumentException
     * @throws java.lang.UnsupportedOperationException
     * @throws java.lang.RuntimeException
     */
    public Hpeq(int priority, int audioSession)
    throws IllegalStateException, IllegalArgumentException,
           UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_HPEQ, EFFECT_TYPE_NULL, priority, audioSession);

        if (audioSession == 0) {
            Log.w(TAG, "WARNING: attaching an Hpeq to global output mix is deprecated!");
        }
    }

    /**
     * Gets the count of all bands.
     * @return the count of all the bands.
     */
    public int getBandCount() {
        Log.d(TAG, "getBandCount: 5");
        return 5;
    }

    /**
     * Gets the min/max value for a gain.
     * @param range_buf buffer for holding the min/max gain values. 
     * @return success(0)/failure(-1) status.
     */
    public int getGainRange(int[] range_buf) {
        range_buf[0] = -12;
        range_buf[1] = 12;
        Log.d(TAG, "getGainRange:" + range_buf[0] + ":" + range_buf[1]);
        return 0;
    }
    
    /**
     * Gets gain for the specified band.
     * @param band the band to get the gain for.
     * @return the gain of the specified band.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int getGainForBand(int band)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] result = new int[1];
        checkStatus(getParameter(band, result));
        Log.d(TAG, "getGainForBand" + (band + 1) + ": " + result[0]);
        return result[0];
    }

    /**
     * Sets the gain for the specified band.
     * @param band the band to set the gain for.
     * @param gain gain value.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public void setGainForBand(int band, int gain)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        Log.d(TAG, "setGainForBand" + (band + 1) + ": " + gain);
        checkStatus(setParameter(band, gain));
    }

    /**
     * Gets gains for all the bands.
     * @param gain_buf buffer for holding the gains.
     * @return success(0)/failure(-1) status.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int getGainForAllBands(int[] gain_buf)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        Log.d(TAG, "[getGainForAllBands] Enter!");

        int bandCount = getBandCount();
        if (gain_buf.length < bandCount) {
            bandCount = gain_buf.length;
        }

        for (int i = 0; i < bandCount; i++) {
            gain_buf[i] = getGainForBand(i);
        }

        return 0;
    }

    /**
     * Sets gains for all the bands.
     * @param gain_buf buffer holding the gains.
     * @return success(0)/failure(-1) status.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int setGainForAllBands(int[] gain_buf)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        Log.d(TAG, "[setGainForAllBands] Enter!");

        int bandCount = getBandCount();
        if (gain_buf.length < bandCount) {
            bandCount = gain_buf.length;
        }

        for (int i = 0; i < bandCount; i++) {
            setGainForBand(i, gain_buf[i]);
        }

        return 0;
    }
    
    /**
     * The OnParameterChangeListener interface defines a method called by the HPEQ when a
     * parameter value has changed.
     */
    public interface OnParameterChangeListener  {
        /**
         * Method called when a parameter value has changed. The method is called only if the
         * parameter was changed by another application having the control of the same
         * HPEQ engine.
         * @param effect the Equalizer on which the interface is registered.
         * @param status status of the set parameter operation.
         * @param param1 ID of the modified parameter.
         * @param param2 additional parameter qualifier (e.g the band for band level parameter).
         * @param value the new parameter value.
         */
        void onParameterChange(Hpeq effect, int status, int param1, int param2, int value);
    }

    /**
     * Listener used internally to receive unformatted parameter change events from AudioEffect
     * super class.
     */
    private class BaseParameterListener implements AudioEffect.OnParameterChangeListener {
        private BaseParameterListener() {

        }
        public void onParameterChange(AudioEffect effect, int status, byte[] param, byte[] value) {
            OnParameterChangeListener l = null;

            synchronized (mParamListenerLock) {
                if (mParamListener != null) {
                    l = mParamListener;
                }
            }
            if (l != null) {
                int p1 = -1;
                int p2 = -1;
                int v = -1;

                if (param.length >= 4) {
                    p1 = byteArrayToInt(param, 0);
                    if (param.length >= 8) {
                        p2 = byteArrayToInt(param, 4);
                    }
                }
                if (value.length == 2) {
                    v = (int)byteArrayToShort(value, 0);;
                } else if (value.length == 4) {
                    v = byteArrayToInt(value, 0);
                }

                if (p1 != -1 && v != -1) {
                    l.onParameterChange(Hpeq.this, status, p1, p2, v);
                }
            }
        }
    }

    /**
     * Registers an OnParameterChangeListener interface.
     * @param listener OnParameterChangeListener interface registered
     */
    public void setParameterListener(OnParameterChangeListener listener) {
        synchronized (mParamListenerLock) {
            if (mParamListener == null) {
                mParamListener = listener;
                mBaseParamListener = new BaseParameterListener();
                super.setParameterListener(mBaseParamListener);
            }
        }
    }

}
