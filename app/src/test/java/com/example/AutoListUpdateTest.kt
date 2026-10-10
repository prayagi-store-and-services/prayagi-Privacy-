package com.example

import com.example.siteblock.AutoListUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoListUpdateTest {
    private val fmt: (Long) -> String = { "T" + it }

    @Test
    fun offSaysNothingIsDownloaded() {
        val t = AutoListUpdate.statusText(false, 0L, 0L, null, fmt)
        assertTrue(t.contains("off"))
        assertFalse(t.contains("worked"))
    }

    @Test
    fun onButNeverRunIsHonest() {
        val t = AutoListUpdate.statusText(true, 0L, 0L, null, fmt)
        assertTrue(t.contains("Has not run yet"))
        assertTrue(t.contains("built-in list"))
    }

    @Test
    fun failureShowsTheReason() {
        val t = AutoListUpdate.statusText(true, 0L, 5L, "Could not reach GitHub. The built-in list is still in use.", fmt)
        assertTrue(t.contains("did not work"))
        assertTrue(t.contains("Could not reach GitHub"))
    }

    @Test
    fun successShowsListDate() {
        val t = AutoListUpdate.statusText(true, 9L, 9L, null, fmt)
        assertTrue(t.contains("It worked."))
        assertTrue(t.contains("T9"))
    }
}
