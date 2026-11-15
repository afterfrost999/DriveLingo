package com.drivelingo.core.audio

import android.media.AudioAttributes
import android.media.AudioManager
import com.drivelingo.core.vehicle.AudioFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEnvironmentRulesTest {

    @Test
    fun `focus changes map to app model`() {
        assertEquals(AudioFocus.GAINED, AudioEnvironmentRules.focusFromChange(AudioManager.AUDIOFOCUS_GAIN))
        assertEquals(AudioFocus.LOST, AudioEnvironmentRules.focusFromChange(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(AudioFocus.LOST_TRANSIENT, AudioEnvironmentRules.focusFromChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(
            AudioFocus.LOST_TRANSIENT_CAN_DUCK,
            AudioEnvironmentRules.focusFromChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK),
        )
        assertNull(AudioEnvironmentRules.focusFromChange(12345))
    }

    @Test
    fun `delayed focus waits like a transient loss, failed focus needs the user`() {
        assertEquals(AudioFocus.GAINED, AudioEnvironmentRules.focusFromRequest(AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
        assertEquals(AudioFocus.LOST_TRANSIENT, AudioEnvironmentRules.focusFromRequest(AudioManager.AUDIOFOCUS_REQUEST_DELAYED))
        assertEquals(AudioFocus.LOST, AudioEnvironmentRules.focusFromRequest(AudioManager.AUDIOFOCUS_REQUEST_FAILED))
    }

    @Test
    fun `navigation guidance is detected from playback usage`() {
        assertTrue(AudioEnvironmentRules.navGuidanceActive(setOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)))
        assertFalse(AudioEnvironmentRules.navGuidanceActive(setOf(AudioAttributes.USAGE_MEDIA)))
    }

    @Test
    fun `phone call is detected from audio mode or call playback`() {
        val none = emptySet<Int>()
        assertTrue(AudioEnvironmentRules.phoneCallActive(none, AudioManager.MODE_RINGTONE))
        assertTrue(AudioEnvironmentRules.phoneCallActive(none, AudioManager.MODE_IN_CALL))
        assertTrue(AudioEnvironmentRules.phoneCallActive(none, AudioManager.MODE_IN_COMMUNICATION))
        assertTrue(AudioEnvironmentRules.phoneCallActive(setOf(AudioAttributes.USAGE_VOICE_COMMUNICATION), AudioManager.MODE_NORMAL))
        assertFalse(AudioEnvironmentRules.phoneCallActive(setOf(AudioAttributes.USAGE_MEDIA), AudioManager.MODE_NORMAL))
    }
}
