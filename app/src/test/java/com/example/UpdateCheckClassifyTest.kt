package com.example
import com.example.update.AppUpdater
import org.junit.Test
import org.junit.Assert.*
class UpdateCheckClassifyTest {
 @Test fun perAppReleaseMetadataCannotTriggerDownload() { assertFalse(AppUpdater::class.java.declaredMethods.any { it.name=="classify" }) }
}
