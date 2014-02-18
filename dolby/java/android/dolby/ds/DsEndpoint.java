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
 * Defines the audio end-points (audio output devices) for use by the
 * DsProfileSettings profile defined in Ds.
 *
 * A DsEndpoint is defined in terms of the underlying AudioDevice
 * values.
 */

package android.dolby.ds;

public enum DsEndpoint
{
    /* Single, generic end-point for Ds. Note that Ds will automatically
     * configure feature settings for the actual end-point internally. Specific settings for
     * different end-points are not required. Hence only a single end-point is required.
     */
    GENERIC(AudioDevice.DEVICE_WIRED_HEADPHONE);

    private AudioDevice _device;
    private DsEndpoint(AudioDevice device) { _device = device; }

    /**
     * @return The AudioDevice enum for this end-point.
     */
    public AudioDevice toDevice() { return _device; }
}
