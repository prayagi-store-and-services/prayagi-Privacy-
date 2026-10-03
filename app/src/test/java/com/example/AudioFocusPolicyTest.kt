package com.example

import com.example.data.model.AudioLockMode
import com.example.service.shouldRequestAudioFocus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusPolicyTest {
    @Test
    fun hardwareModeNeverTakesAudioFocusSoMediaKeepsPlaying() {
        assertFalse(shouldRequestAudioFocus(AudioLockMode.EXCLUSIVE_LINE_RESERVATION))
    }

    @Test
    fun powerSaverModeStillUsesAudioFocus() {
        assertTrue(shouldRequestAudioFocus(AudioLockMode.AUDIO_FOCUS_ONLY))
    }
}
