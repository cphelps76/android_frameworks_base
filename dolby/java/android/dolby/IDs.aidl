/******************************************************************************
 *  This program is protected under international and U.S. copyright laws as
 *  an unpublished work. This program is confidential and proprietary to the
 *  copyright owners. Reproduction or disclosure, in whole or in part, or the
 *  production of derivative works therefrom without the express permission of
 *  the copyright owners is prohibited.
 *
 *                 Copyright (C) 2011-2012 by Dolby Laboratories,
 *                             All rights reserved.
 ******************************************************************************/

/*
 * IDs.aidl
 *
 * Interface definition for the remote Service running in another process
 */

package android.dolby;

import android.dolby.DsClientSettings;
import android.dolby.IDsServiceCallbacks;

interface IDs
{
    int setDsOn(boolean on);

    int getDsOn(out boolean[] on);

    int setNonPersistentMode(boolean on);

    int getProfileCount(out int[] count);

    int getProfileNames(out String[] names);

    int getBandCount(out int[] count);

    int getBandFrequencies(out int[] frequencies);

    int setSelectedProfile(int profile);

    int getSelectedProfile(out int[] profile);

    int setProfileSettings(int profile, in DsClientSettings settings);

    int getProfileSettings(int profile, out DsClientSettings[] settings);

    int resetProfile(int profile);

    int setProfileName(int profile, String name);

    int getDsApVersion(out String[] version);

    int getDsVersion(out String[] version);

    int setIeqPreset(int profile, int preset);

    int getIeqPreset(int profile, out int[] preset);

    int isProfileModified(int profile, out boolean[] isModified);

    int setGeq (int profile, int preset, in float[] geqBandGains);

    int getGeq (int profile, int preset, out float[] geqBandGains);

    int setDsApParam(String param, in int[] values);

    int getDsApParam(String param, out int[] values);

    int getDsApParamLength(String param, out int[] len);

    void registerDsApParamEvents(int handle);

    void unregisterDsApParamEvents(int handle);

    void registerCallback(IDsServiceCallbacks cb, int handle);

    void unregisterCallback(IDsServiceCallbacks cb);

    void setClientHandle(int handle);

    void registerVisualizerData(int handle);

    void unregisterVisualizerData(int handle);
}
