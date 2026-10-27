package com.example
import com.example.update.AppUpdater
import org.junit.Test
import org.junit.Assert.*
class UpdateCleanupTest {
 @Test fun cancelsKnownLegacyWorkerWithoutSchedulingReplacement() = assertEquals(listOf("netra_update_alert"),AppUpdater.LEGACY_WORK_NAMES)
}
