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
 * SRS(Sound Retrieval System) is a set of advanced algorithm for simulating 3D audio experiences.
 * <p>An application creates an Srs object to instantiate and control an SRS engine
 * in the audio framework. The application can either simply use predefined presets or have a more
 * precise control of the gain in each frequency band controlled by the equalizer.
 * <p>The methods, parameter types and units exposed by the Equalizer implementation are directly
 * mapping those defined by the OpenSL ES 1.0.1 Specification (http://www.khronos.org/opensles/)
 * for the SLEqualizerItf interface. Please refer to this specification for more details.
 * <p>To attach the Equalizer to a particular AudioTrack or MediaPlayer, specify the audio session
 * ID of this AudioTrack or MediaPlayer when constructing the Equalizer.
 * <p>NOTE: attaching an SRS to the global audio output mix by use of session 0 is deprecated.
 * <p>See {@link android.media.MediaPlayer#getAudioSessionId()} for details on audio sessions.
 * <p>See {@link android.media.audiofx.AudioEffect} class for more details on controlling audio
 * effects.
 */

public class Srs extends AudioEffect {

    private final static String TAG = "SRS-JAVA";

    // These constants must be synchronized with those in
    // frameworks/base/media/libeffects/srs/srs_source/tshd.cpp(a temporary location, should be moved to
    // system/media/audio_effects/include/audio_effects/effect_srs.h)

    /**
     * Enable/Disable true bass(1/0).
     */
    public static final int SRS_PARAM_TRUEBASS_ENABLE = 0;

    /**
     * Enable/Disable dialog clarity(1/0).
     */
    public static final int SRS_PARAM_DIALOGCLARITY_ENABLE = 1;

    /**
     * Enable/Disable surround(1/0).
     */
    public static final int SRS_PARAM_SURROUND_ENABLE = 2;

    /**
     * Set/Get the Speaker LF Response for true bass(40, 60, 100, 150, 200, 250, 300, 400Hz).
     */
    public static final int SRS_PARAM_TRUEBASS_SPKER_SIZE = 3;

    /**
     * Set/Get the true bass gain(float: 0.0 - 1.0).
     */
    public static final int SRS_PARAM_TRUEBASS_GAIN = 4;

    /**
     * Set/Get the dialog clarity gain(float: 0.0 - 1.0).
     */
    public static final int SRS_PARAM_DIALOGCLARTY_GAIN = 5;

    /**
     * Set/Get the definition gain(float: 0.0 - 1.0).
     */
    public static final int SRS_PARAM_DEFINITION_GAIN = 6;

    /**
     * Set/Get the surround gain(float: 0.0 - 1.0).
     */
    public static final int SRS_PARAM_SURROUND_GAIN = 7;

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
     * @param priority the priority level requested by the application for controlling the SRS
     * engine. As the same engine can be shared by several applications, this parameter indicates
     * how much the requesting application needs control of effect parameters. The normal priority
     * is 0, above normal is a positive number, below normal a negative number.
     * @param audioSession system wide unique audio session identifier. The SRS will be
     * attached to the MediaPlayer or AudioTrack in the same audio session.
     *
     * @throws java.lang.IllegalStateException
     * @throws java.lang.IllegalArgumentException
     * @throws java.lang.UnsupportedOperationException
     * @throws java.lang.RuntimeException
     */
    public Srs(int priority, int audioSession)
    throws IllegalStateException, IllegalArgumentException,
           UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_SRS, EFFECT_TYPE_NULL, priority, audioSession);

        if (audioSession == 0) {
            Log.w(TAG, "WARNING: attaching an Srs to global output mix is deprecated!");
        }
    }

    /**
     * Gets the enable/disable status for one item of SRS effect.
     * @return true when true bass is enabled, false when true bass is disabled.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public boolean getSrsItemEnabled(int param)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] result = new int[1];
        checkStatus(getParameter(param, result));
        Log.d(TAG, "[getSrsItemEnabled] " + param + ":" + result[0]);
        if (result[0] == 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Enable/Disable one item of SRS effect.
     * @param param the SRS parameter to set.
     * @param enable true for enabling, and false for disabling.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public void setSrsItemEnabled(int param, boolean enable)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int value;
        if (enable) {
            value = 1;
        } else {
            value = 0;
        }

        Log.d(TAG, "[setSrsItemEnabled] " + param + ":" + value);
        checkStatus(setParameter(param, value));
    }

    /**
     * Gets Speaker LF Response for true bass.
     * @return the 0-based index for the Speaker LF Response.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int getSpeakerLfResponse()
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int index = -1;
        int[] result = new int[1];
        checkStatus(getParameter(SRS_PARAM_TRUEBASS_SPKER_SIZE, result));
        switch (result[0]) {
            case 40:  index = 0; break;
            case 60:  index = 1; break;
            case 100: index = 2; break;
            case 150: index = 4; break;
            case 200: index = 5; break;
            case 250: index = 6; break;
            case 300: index = 7; break;
            case 400: index = 8; break;
            default:
                throw (new IllegalArgumentException("Srs: bad parameter value"));
        }

        return index;
    }

    /**
     * Sets Speaker LF Response for true bass
     * @param index SpeakerLfResponse index.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public void setSpeakerLfResponse(int index)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int value;
        Log.d(TAG, "setSpeakerLfResponse index:" + index);
        switch (index) {
            case 0: value = 40; break;
            case 1: value = 60; break;
            case 2: value = 100; break;
            case 3: value = 150; break;
            case 4: value = 200; break;
            case 5: value = 250; break;
            case 6: value = 300; break;
            case 7: value = 400; break;
            default:
                throw (new IllegalArgumentException("Srs: bad parameter value"));
        }

        checkStatus(setParameter(SRS_PARAM_TRUEBASS_SPKER_SIZE, value));
    }

    /**
     * Gets gain for the specified SRS item.
     * @param param the SRS item to get the gain for.
     * @return the gain of the specified item.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public float getGain(int param)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        byte[] result = new byte[4];
        checkStatus(getParameter(param, result));

        ByteBuffer converter = ByteBuffer.wrap(result);
        converter.order(ByteOrder.nativeOrder());
        return converter.getFloat(0);
    }

    /**
     * Sets the gain for the specified SRS item.
     * @param param the SRS item to set the gain for.
     * @param gain gain value.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public void setGain(int param, float gain)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        ByteBuffer converter = ByteBuffer.allocate(4);
        converter.order(ByteOrder.nativeOrder());
        converter.putFloat(gain);
        checkStatus(setParameter(param, converter.array()));
        Log.d(TAG, "setGain, gain:" + gain);
    }
    
    /**
     * The OnParameterChangeListener interface defines a method called by the SRS when a
     * parameter value has changed.
     */
    public interface OnParameterChangeListener  {
        /**
         * Method called when a parameter value has changed. The method is called only if the
         * parameter was changed by another application having the control of the same
         * SRS engine.
         * @param effect the Equalizer on which the interface is registered.
         * @param status status of the set parameter operation.
         * @param param1 ID of the modified parameter.
         * @param param2 additional parameter qualifier (e.g the band for band level parameter).
         * @param value the new parameter value.
         */
        void onParameterChange(Srs effect, int status, int param1, int param2, int value);
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
                    l.onParameterChange(Srs.this, status, p1, p2, v);
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
