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
 @Test fun retirementCancelsPersistedWorkAndClearsOnlyUpdaterData() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val wm=androidx.work.WorkManager.getInstance(c)
  val req=androidx.work.PeriodicWorkRequest.Builder(UpdateAlertWorker::class.java,15,java.util.concurrent.TimeUnit.MINUTES)
   .setInitialDelay(1,java.util.concurrent.TimeUnit.DAYS).build()
  wm.enqueueUniquePeriodicWork("netra_update_alert",androidx.work.ExistingPeriodicWorkPolicy.REPLACE,req).result.get(10,java.util.concurrent.TimeUnit.SECONDS)
  listOf("netra_update_alert","netra_app_update","verified_update_artifacts").forEach { c.getSharedPreferences(it,0).edit().putString("old","record").commit() }
  val unrelated=c.getSharedPreferences("retirement_unrelated_test",0);unrelated.edit().putString("keep","yes").commit()
  val nm=c.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
  nm.createNotificationChannel(android.app.NotificationChannel("netra_app_update","Legacy updates",3))
  AppUpdater.retireLegacyUpdater(c)
  val limit=System.currentTimeMillis()+10000
  while(wm.getWorkInfoById(req.id).get()?.state!=androidx.work.WorkInfo.State.CANCELLED && System.currentTimeMillis()<limit) Thread.sleep(100)
  assertEquals(androidx.work.WorkInfo.State.CANCELLED,wm.getWorkInfoById(req.id).get()?.state)
  assertNull(nm.getNotificationChannel("netra_app_update"))
  listOf("netra_update_alert","netra_app_update","verified_update_artifacts").forEach { assertTrue(c.getSharedPreferences(it,0).all.isEmpty()) }
  assertEquals("yes",unrelated.getString("keep",null));unrelated.edit().clear().commit()
 }
 @Test fun persistedLegacyWorkerHasNoDownloadEntryPoints() {
  assertFalse(AppUpdater::class.java.declaredMethods.any { it.name in listOf("download","install","checkNow") })
 }
}
