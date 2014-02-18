/******************************************************************************
 *  This program is protected under international and U.S. copyright laws as
 *  an unpublished work. This program is confidential and proprietary to the
 *  copyright owners. Reproduction or disclosure, in whole or in part, or the
 *  production of derivative works therefrom without the express permission of
 *  the copyright owners is prohibited.
 *
 *               Copyright (C) 2011-2012 by Dolby Laboratories,
 *                             All rights reserved.
 ******************************************************************************/

package android.dolby.ds;

import android.media.audiofx.AudioEffect;
import android.util.Log;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.UUID;

import android.dolby.DsLog;

public class DsEffect
{
    private static final String LOG_TAG = "DsEffect";
    public static final UUID EFFECT_TYPE_NULL = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210");
    UUID nxp_env_reverb_uuid = UUID.fromString("4a387fc0-8ab3-11df-8bad-0002a5d5c51b");

    //These are legitimate, randomly calculated values and can be used permanently
    public static final UUID EFFECT_TYPE_DS = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587");
    public static final UUID EFFECT_DS = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");

    protected static final int
        DS_PARAM_TUNING = 0,
        DS_PARAM_DEFINE_SETTINGS = 1,
        DS_PARAM_ALL_VALUES = 2,
        DS_PARAM_SINGLE_DEVICE_VALUE = 3,
        DS_PARAM_VISUALIZER_DATA = 4,
        DS_PARAM_DEFINE_PARAMS = 5,
        DS_PARAM_VERSION = 6,
        DS_PARAM_VISUALIZER_ENABLE = 7;
        //TODO: clarify the DS_PARAM_XXX

    protected Class classAudioEffect = null;
    protected AudioEffect audioEffect = null;
    protected Method methodSetParameter = null;
    protected Method methodGetParameter = null;
    private int audioSessionId_;

    /**
     * Constructs a Ds AudioEffect.
     *
     * This implementation will formally call the AudioEffect super class
     * once Ds formally extends from AudioEffect.
     *
     * @param audioSessionId The audio session Id to be passed to the
     * AudioEffect constructor.
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public DsEffect(int audioSessionId) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, RuntimeException
    {
        try
        {
            classAudioEffect = Class.forName("android.media.audiofx.AudioEffect");
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            throw e;
        }
        Constructor ctorAudioEffect = null;
        try
        {
            //The constructor for audioeffect takes the type UUD, id UUID, priority and mixer session Id.
            ctorAudioEffect = classAudioEffect.getConstructor(new Class[]{UUID.class, UUID.class, int.class, int.class});
            DsLog.log2 (LOG_TAG, "Found AudioEffect Constructor");
        }
        catch (SecurityException e)
        {
            Log.e(LOG_TAG, e.toString());
            throw e;
        } catch (NoSuchMethodException e)
        {
            Log.e(LOG_TAG, e.toString());
            throw e;
        }

        try {
            audioEffect = (AudioEffect)ctorAudioEffect.newInstance(EFFECT_TYPE_NULL, EFFECT_DS, /*!!!ERA USE HIGH PRIORITY?*/0, audioSessionId);
             DsLog.log2 (LOG_TAG, "Created Ds AudioEffect successfully");
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            throw e;
        } catch (InstantiationException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            throw e;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            throw e;
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            throw e;
        }

        //lets find the descriptor of what we created:
        AudioEffect.Descriptor e = audioEffect.getDescriptor();

        methodSetParameter = classAudioEffect.getMethod("setParameter", new Class[]{byte[].class, byte[].class});
        methodGetParameter = classAudioEffect.getMethod("getParameter", new Class[]{byte[].class, byte[].class});

        DsLog.log1 (LOG_TAG, "CREATED EFFECT Implementor:\"" + e.implementor + "\"\n" +
                " name:\"" + e.name + "\"\n" +
                " connectMode:\"" + e.connectMode + "\"\n" +
                " type:\"" + e.type.toString() + "\"\n" +
                " uuid:\"" + e.uuid.toString() + "\"\n" +
                " sessionID:\"" + audioSessionId + "\"");

        //
        // Send the definitions of AK parameters and settings
        //
        _setDefineParams();
        _setDefineSettings();
        audioSessionId_ = audioSessionId;
    }

    /**
     * Calls AudioEffect.release on the underlying AudioEffect.
     *
     * This implementation will not be required once Ds formally
     * extends from AudioEffect.
     */
    public void release()
    {
        audioEffect.release();
    }

    /**
     * Calls AudioEffect.setEnabled on the underlying AudioEffect.
     *
     * This implementation will not be required once Ds formally
     * extends from AudioEffect.
     *
     * @param enabled The new enabled state of the audio effect.
     * @return The value returned from AudioEffect.setEnabled.
     * @throws IllegalStateException
     */
    public int setEnabled(boolean enabled) throws IllegalStateException
    {
        return audioEffect.setEnabled(enabled);
    }

    /**
     * Calls AudioEffect.setEnabled on the underlying AudioEffect.
     *
     * This implementation will not be required once Ds formally
     * extends from AudioEffect.
     *
     * @return The enabled state of the AudioEffect.
     * @throws IllegalStateException
     */
    public boolean getEnabled() throws IllegalStateException
    {
        return audioEffect.getEnabled();
    }

    /**
     * Checks if this AudioEffect object is controlling the effect engine.

     * This implementation will not be required once Ds formally
     * extends from AudioEffect.
     *
     * @return true if this instance has control of effect engine, false
     *         otherwise.
     * @throws IllegalStateException
     */
    public boolean hasControl() throws IllegalStateException
    {
        return audioEffect.hasControl();
    }

    private byte[] intToByteArray(int value)
    {
        ByteBuffer converter = ByteBuffer.allocate(4);
        converter.order(ByteOrder.nativeOrder());
        converter.putInt(value);
        return converter.array();
    }

    private static byte[] IntArrayToByteArray (int [] src)
    {
        int srcLength = src.length;
        byte[]dst = new byte[srcLength << 2];

        for (int i=0; i<srcLength; i++)
        {
            int x = src[i];
            int j = i << 2;
            dst[j++] = (byte) ((x >>> 0) & 0xff);
            dst[j++] = (byte) ((x >>> 8) & 0xff);
            dst[j++] = (byte) ((x >>> 16) & 0xff);
            dst[j++] = (byte) ((x >>> 24) & 0xff);
        }
        return dst;
    }

    private static int SetInt16InByteArray(int value, byte[] dst, int index)
    {
        dst[index] = (byte)(value & 0xff);
        dst[index + 1] = (byte)((value >>> 8) & 0xff);
        return 2;
    }

    private static int SetInt32InByteArray(int value, byte[] dst, int index)
    {
        dst[index++] = (byte)(value & 0xff);
        dst[index++] = (byte)((value >>> 8) & 0xff);
        dst[index++] = (byte)((value >>> 16) & 0xff);
        dst[index] = (byte)((value >>> 24) & 0xff);
        return 4;
    }

    private static int Set4ChInByteArray(String src, byte[] dst, int index) throws IllegalArgumentException
    {
        int len = src.length();
        if (len > 4)
        {
            Log.e(LOG_TAG, "parameter name " + src + " contains more than 4 characters");
            throw new IllegalArgumentException("Wrong parameter name");
        }
        else
        {
            for (int i = 0; i < len; i++)
            {
                dst[index++] = (byte)(src.charAt(i));
            }
            if (len < 4)
                dst[index] = (byte)'\0';
        }
        return 4;
    }

    private static int ByteArrayToInt(byte[] ba)
    {
        return ((ba[3] & 0xff) << 24) | ((ba[2] & 0xff) << 16) | ((ba[1] & 0xff) << 8) | (ba[0] & 0xff);
    }

    private static int[] ByteArrayToIntArray(byte[] ba)
    {
        int srcLength = ba.length;
        int destLength = srcLength >> 2;
        int[] dest = new int[destLength];

        for (int i = 0; i < destLength; i++)
        {
            dest[i] = ((ba[i * 4 + 3] & 0xff) << 24) | ((ba[i * 4 + 2] & 0xff) << 16) | ((ba[i * 4 + 1] & 0xff) << 8) | (ba[i * 4] & 0xff);
        }
        return dest;
    }

    private static short[] ByteArrayToShortArray(byte[] ba)
    {
        int srcLength = ba.length;
        int destLength = srcLength >> 1;
        short[] dest = new short[destLength];

        for (int i = 0; i < destLength; i++)
        {
            dest[i] = (short)(((ba[i * 2 + 1] & 0xff) << 8) | (ba[i * 2] & 0xff));
        }
        return dest;
    }

    private static String ByteArrayToString(byte[] ba)
    {
        StringBuilder sb = new StringBuilder(3 + ba.length * 6);
        sb.append("HEX(");
        for (byte b : ba)
        {
            sb.append(Integer.toHexString((int)b));
            sb.append(' ');
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Set the parameter to the underlying Ds audio processing library.
     * @internal
     *
     * @param param The byte array containing the identifier of the parameter to set.
     * @param value The byte array containing the new value for the specified parameter.
     * @return AudioEffect.SUCCESS in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    private int _invokeSetParameter(byte[] baParam, byte[] baValue)
    {
        DsLog.log1(LOG_TAG, "_invokeSetParameter baParam:" + ByteArrayToString(baParam) + "\n baValue:" + ByteArrayToString(baValue));

        int iRet = AudioEffect.SUCCESS;
        try
        {
            iRet = (Integer)methodSetParameter.invoke(audioEffect, new Object[]{baParam, baValue});
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            return AudioEffect.ERROR_BAD_VALUE;
        }
        catch (IllegalAccessException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            return AudioEffect.ERROR_INVALID_OPERATION;
        }
        catch (InvocationTargetException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            return AudioEffect.ERROR_INVALID_OPERATION;
        }
        DsLog.log3(LOG_TAG, "_invokeSetParameter returning.");

        return iRet;
    }

    /**
     * Retrieve the parameter from the underlying Ds audio processing library.
     * @internal
     *
     * @param param The byte array containing the identifier of the parameter to get.
     * @param value The byte array to contain the value for the specified parameter.
     * @return The number of meaningful bytes retrieved in value array in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    private int _invokeGetParameter(byte[] baParam, byte[] baValue)
    {
        int count = 0;
        try
        {
            count = (Integer)methodGetParameter.invoke(audioEffect, new Object[]{baParam, baValue});
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            return AudioEffect.ERROR_BAD_VALUE;
        }
        catch (IllegalAccessException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            return AudioEffect.ERROR_INVALID_OPERATION;
        }
        catch (InvocationTargetException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
            return AudioEffect.ERROR_INVALID_OPERATION;
        }

        DsLog.log3(LOG_TAG, "_invokeGetParameter baParam:" + ByteArrayToString(baParam) + "\n baValue:" + ByteArrayToString(baValue));
        DsLog.log3(LOG_TAG, "_invokeGetParameter returning:" + count);
        return count;
    }

    /**
     * Retrieve the parameter from the underlying Ds audio processing library with an integer array.
     * @internal
     *
     * @param param The identifier of the parameter to get.
     * @param value The array to store the values retrieved.
     * @return The number of meaningful bytes retrieved in value array in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    private int _getIntArrayParameter(int param, int[] value)
    {
        int count = 0;
        byte[] baParam = intToByteArray(param);
        byte[] baValue = new byte[value.length << 2];
        count = _invokeGetParameter(baParam, baValue);
        if (count != (value.length << 2))
        {
            Log.e(LOG_TAG, "_getIntArrayParameter: Error in getting the parameter!");
        }
        else
        {
            int[] tmpValue = ByteArrayToIntArray(baValue);
            System.arraycopy(tmpValue, 0, value, 0, value.length);
        }
        return count;
    }

    /**
     * Retrieve the parameter from the underlying Ds audio processing library with an short integer array.
     * @internal
     *
     * @param param The identifier of the parameter to get.
     * @param value The array to store the values retrieved.
     * @return The number of meaningful bytes retrieved in value array in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    private int _getShortArrayParameter(int param, short[] value)
    {
        int count = 0;
        byte[] baParam = intToByteArray(param);
        byte[] baValue = new byte[value.length << 1];
        count = _invokeGetParameter(baParam, baValue);
        if (count != (value.length << 1))
        {
            DsLog.log2(LOG_TAG, "_getShortArrayParameter: Unexpected length");
        }
        else
        {
            short[] tmpValue = ByteArrayToShortArray(baValue);
            System.arraycopy(tmpValue, 0, value, 0, tmpValue.length);
        }
        return count;
    }

    private void _setDefineParams()
    {
        //
        //DS_PARAM_DEFINE_PARAMS
        //

        byte[] baParam = intToByteArray(DS_PARAM_DEFINE_PARAMS);
        //for each element, 1 byte for parameter and 2 bytes for offset
        byte[] baValue = new byte[2 + DsAkSettings.getNumOfParams() * 4];
        int index = 0;
        index += SetInt16InByteArray(DsAkSettings.getNumOfParams(), baValue, index);

        //getParamsDefinitions returns an array of String objects containing AK parameter names.
        String[] defns = DsAkSettings.getParamsDefinitions();
        for (int i = 0; i < defns.length; ++i)
        {
            index += Set4ChInByteArray(defns[i], baValue, index);
        }
        _invokeSetParameter(baParam, baValue);
    }

    private void _setDefineSettings()
    {
        //
        //DS_PARAM_DEFINE_SETTINGS
        //

        byte[] baParam = intToByteArray(DS_PARAM_DEFINE_SETTINGS);
        //for each element, 1 byte for parameter and 2 bytes for offset
        byte[] baValue = new byte[2 + DsAkSettings.getNumElementsPerDevice() * 3];
        int index = 0;
        index += SetInt16InByteArray(DsAkSettings.getNumElementsPerDevice(), baValue, index);

        //getSettingsDefinitions returns an array of SettingDefn objects. However, the
        //array is Object[], and each element needs to be re-cast.
        Object[] defns = DsAkSettings.getSettingsDefinitions();
        for (int i = 0; i < defns.length; ++i)
        {
            baValue[index] = (byte)((DsAkSettings.SettingDefn)defns[i]).parameter;
            ++index;
            index += SetInt16InByteArray(((DsAkSettings.SettingDefn)defns[i]).offset, baValue, index);
        }
        _invokeSetParameter(baParam, baValue);
    }

    //TODO: What is Tuning settings?
    public void    setTuningSettings(Map<String, int[]> settings)
    {
        DsLog.log1(LOG_TAG, "setTuningSettings");
    }

    /**
     * Set all the profile-only settings.
     * TODO: Currently this method is a simple wrapper of setAllSettings, and we will adopt it to set
     *       profile-only settings if we need to differentiate the AK settings for different endpoints.
     *
     * @param settings The new profile settings.
     * @return AudioEffect.SUCCESS in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    public int setAllProfileSettings(DsProfileSettings settings)
    {
        DsLog.log1(LOG_TAG, "setAllProfileSettings");
        return setAllSettings(settings.getAllSettings());
    }

    /**
     * Enable or disable the visualizer.
     * This enables or disables the visualizer in the underlying Ds library.
     * When disabled, visualizer data will not be updated and it therefore should not be retrieved.
     *
     * @param enable The new state of the visualizer.
     * @return AudioEffect.SUCCESS in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    public int setVisualizerOn(boolean enable)
    {
        DsLog.log1(LOG_TAG, "setVisualizerOn");
        int on = enable ? DsAkSettings.AK_DS1_FEATURE_ON : DsAkSettings.AK_DS1_FEATURE_OFF;

        //
        // Send EFFECT_CMD_SET_PARAM
        // DS_PARAM_VISUALIZER_ENABLE
        //
        byte[] baParam = intToByteArray(DS_PARAM_VISUALIZER_ENABLE);
        byte[] baValue = new byte[4];
        int index = 0;
        index += SetInt32InByteArray(on, baValue, index);
        return _invokeSetParameter(baParam, baValue);
    }

    /**
     * Get the enabled state of the visualizer.
     *
     * @return true if the visualizer is enabled. false otherwise.
     */
    public boolean getVisualizerOn()
    {
        DsLog.log1(LOG_TAG, "getVisualizerOn");
        boolean enabled = false;
        int count = 0;

        //
        // Send EFFECT_CMD_GET_PARAM
        // DS_PARAM_VISUALIZER_ENABLE
        //
        byte[] baParam = intToByteArray(DS_PARAM_VISUALIZER_ENABLE);
        byte[] baValue = new byte[4];
        count = _invokeGetParameter(baParam, baValue);
        if (count != 4)
        {
            Log.e(LOG_TAG, "getVisualizerOn: Error in getting the visualizer on/off state!");
        }
        else
        {
            int on = ByteArrayToInt(baValue);
            enabled = (on == DsAkSettings.AK_DS1_FEATURE_ON) ? true : false;
        }

        return enabled;
    }

    /**
     * Get the visualizer data from the underlying Ds audio processing library.
     * The retrieved visualizer data array starts with custom visualizer band gains, and followed by band excitations.
     * Band gains and excitations are concatenated, not interleaved.
     *
     * @return The visualizer data array retrieved on success, and null on failure.
     */
    public short[] getVisualizerData()
    {
        DsLog.log3(LOG_TAG, "getVisualizerData");
        int count = 0;

        //
        // Send EFFECT_CMD_GET_PARAM
        // DS_PARAM_VISUALIZER_DATA
        //
        int numVisualizerData = DsAkSettings.getParamArrayLength("vcbg") + DsAkSettings.getParamArrayLength("vcbe");
        short[] visualizerData = new short[numVisualizerData];

        count = _getShortArrayParameter(DS_PARAM_VISUALIZER_DATA, visualizerData);
        if (count != (visualizerData.length << 1))
        {
            return null;
        }

        return visualizerData;
    }

    /**
     * Set a single value for the specified parameter, for the specified output device.
     * Note: The beginning offset of the parameter is always expected to be 0 here, based on the implementation of EffectDs.cpp.
     *
     * The parameter and offset pair must be defined in DsAkSettings. The output device specified
     * should have already been included in a previous call to setAllSettings. If setAllSettings has been
     * called previously but did not include settings for the specified device, this method will have no effect.
     * If this method is called before setAllSettings, the effect will generate settings for the specified device.
     * This behaviour is provided for debugging purposes and should not be relied upon.
     * @param parameter The DS parameter.
     * @param offset The beginning offset of the parameter.
     * @param values The new value/values for the parameter setting.
     * @param device The output device that the new setting value is associated with.
     * @return AudioEffect.SUCCESS in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    public int setSingleSetting(int parameter, int offset, short[] values, AudioDevice device)
    {
        if (values.length == 1)
        {
            DsLog.log1(LOG_TAG, "setSingleSetting: device " + device + ", parameter "+ parameter + ", offset " + offset);
        }
        else
        {
            int offsetEnd = offset + values.length - 1;
            DsLog.log1(LOG_TAG, "setSingleSetting: device " + device + ", parameter "+ parameter + ", offset [" + offset + "-" + offsetEnd + "]");
        }
        int begin = DsAkSettings.getAkSettingIndex(parameter, offset);
        int end = DsAkSettings.getAkSettingIndex(parameter, offset + values.length - 1);
        if (begin == -1 || end == -1)
        {
            Log.e(LOG_TAG, "Attempt to set disallowed parameter and offset combination");
            return AudioEffect.ERROR_INVALID_OPERATION;
        }

        //
        //Send EFFECT_CMD_SET_PARAM
        //DS_PARAM_SINGLE_DEVICE_VALUE
        //

        byte[] baParam = intToByteArray(DS_PARAM_SINGLE_DEVICE_VALUE);
        byte[] baValue = new byte[4 + 2 + 2 + values.length * 2];
        int index = 0;
        index += SetInt32InByteArray(device.toInt(), baValue, index);
        index += SetInt16InByteArray(begin, baValue, index);
        index += SetInt16InByteArray(values.length, baValue, index);
        for (int j = 0; j < values.length; j++)
            index += SetInt16InByteArray(values[j], baValue, index);
        return _invokeSetParameter(baParam, baValue);
    }

    /**
     * Set all settings for one or more output devices.
     *
     * Calling this method erases the internal cache of settings values
     * previously set for all devices. DS will be immediately configured with the most appropriate settings
     * based on the active output device.
     * @param allSettings
     * @return AudioEffect.SUCCESS in case of success, AudioEffect.ERROR_BAD_VALUE,
     *         AudioEffect.ERROR_NO_MEMORY, AudioEffect.ERROR_INVALID_OPERATION or
     *         AudioEffect.ERROR_DEAD_OBJECT in case of failure.
     */
    public int setAllSettings(Map<AudioDevice, DsAkSettings> allSettings)
    {
        //Send EFFECT_CMD_SET_PARAM
        //DS_PARAM_ALL_VALUES
        //

        byte[] baParam = intToByteArray(DS_PARAM_ALL_VALUES);
        //get the devices count
        int nDevCount = allSettings.size();
        //calculate the total array length
        byte[] baValue = new byte[2 + nDevCount * (4 + DsAkSettings.getNumElementsPerDevice() * 2)];
        int index = 0;
        index += SetInt16InByteArray(nDevCount, baValue, index);
        for (AudioDevice device : allSettings.keySet())
        {
            DsAkSettings s = allSettings.get(device);
            //Store the device Id
            index += SetInt32InByteArray(device.toInt(), baValue, index);
            //Copy the settings
            short [] values = s.getValues();
            for (int i = 0; i < values.length; ++i)
                index += SetInt16InByteArray(values[i], baValue, index);
        }
        return _invokeSetParameter(baParam, baValue);
    }

    /**
     * Get the version of DS1AK library, the format is 4 numbers.
     *
     * @return The version of the Ds audio processing library.
     */
    public short[] getVersion()
    {
        //
        //Send EFFECT_CMD_GET_PARAM
        //
        int verLen = DsAkSettings.getParamArrayLength("ver");
        short[] version = new short[verLen];
        int count = 0;

        count = _getShortArrayParameter(DS_PARAM_VERSION, version);
        if (count != (version.length << 1))
        {
            Log.e(LOG_TAG, "getVersion(): Error in getting the version");
            for (int i = 0; i < verLen; i++)
            {
                version[i] = -1;
            }
        }
        return version;
    }
}
