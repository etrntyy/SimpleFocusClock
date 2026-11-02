package com.focusclock.app

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadphonesTest {
    @Test
    fun noHeadphonesMeansNothingToPickWhenOnlyTheSpeakerAndEarpieceAreThere() {
        assertNull(Headphones.pick(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
        assertNull(Headphones.pick(emptyList()))
    }

    @Test
    fun wiredAndBluetoothHeadphonesAreFoundWhateverComesFirst() {
        assertEquals(1, Headphones.pick(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertEquals(1, Headphones.pick(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertEquals(0, Headphones.pick(listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertEquals(2, Headphones.pick(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_USB_HEADSET)))
    }

    @Test
    fun aPhoneCallLinkIsNotTreatedAsHeadphonesForMusic() {
        assertNull(Headphones.pick(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
    }
}
