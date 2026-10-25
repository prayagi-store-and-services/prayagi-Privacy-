package com.example.update
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SelfUpdateProductionFixtureTest {
 @Test fun mergedManifestHasNoInstallerPermissionOrProvider() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val p=c.packageManager.getPackageInfo(c.packageName,android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_PROVIDERS)
  assertFalse(p.requestedPermissions.orEmpty().contains("android.permission.REQUEST_INSTALL_PACKAGES"))
  assertFalse(p.providers.orEmpty().any { it.authority==c.packageName+".updates" })
 }
 @Test fun persistedLegacyWorkerDoesNothing() {
  assertFalse(AppUpdater::class.java.declaredMethods.any { it.name in listOf("download","install","checkNow") })
 }
}
