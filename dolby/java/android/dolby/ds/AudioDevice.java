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
 * Enum used for associating DsProfileSettings with a specific audio device
 * (e.g. speaker, headset, etc).
 *
 * AudioDevice is used only in conjunction with instances of the lower-level
 * Ds.
 *
 * These values directly map Android's internal representation for the devices
 * in "enum audio_device_e" in "/frameworks/base/include/media/EffectApi.h".
 * Changes in that enum should be transfered here.
 */

package android.dolby.ds;

public enum AudioDevice
{
    DEVICE_EARPIECE (0x1),                      ///< earpiece
    DEVICE_SPEAKER (0x2),                       ///< speaker
    DEVICE_WIRED_HEADSET (0x4),                 ///< wired headset, with microphone
    DEVICE_WIRED_HEADPHONE (0x8),               ///< wired headphone, without microphone
    DEVICE_BLUETOOTH_SCO (0x10),                ///< generic bluetooth SCO
    DEVICE_BLUETOOTH_SCO_HEADSET (0x20),        ///< bluetooth SCO headset
    DEVICE_BLUETOOTH_SCO_CARKIT (0x40),         ///< bluetooth SCO car kit
    DEVICE_BLUETOOTH_A2DP (0x80),               ///< generic bluetooth A2DP
    DEVICE_BLUETOOTH_A2DP_HEADPHONES (0x100),   ///< bluetooth A2DP headphones
    DEVICE_BLUETOOTH_A2DP_SPEAKER (0x200),      ///< bluetooth A2DP speakers
    DEVICE_AUX_DIGITAL (0x400),                 ///< digital output
    DEVICE_ANLG_DOCK_HEADSET (0x800),           ///< analog dock headset
    DEVICE_DGTL_DOCK_HEADSET (0x1000),          ///< digital dock headset
    DEVICE_USB_ACCESSORY (0x2000),              ///< USB accessory
    DEVICE_USB_DEVICE (0x4000),                 ///< USB device
	DEVICE_REMOTE_SUBMIX (0x8000);	            ///< Miracast

    private int value;
    public int toInt() { return value; }
    private AudioDevice(int value) { this.value = value; }
}

