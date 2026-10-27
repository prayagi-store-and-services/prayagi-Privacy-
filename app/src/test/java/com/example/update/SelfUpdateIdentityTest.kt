package com.example.update
import org.junit.Test
import org.junit.Assert.*
class SelfUpdateIdentityTest {
 @Test fun selfInstallCapabilityIsRemovedRatherThanBypassed() { assertFalse(AppUpdater::class.java.declaredMethods.any { it.name=="verifyArchive" || it.name=="install" }) }
}
