package com.example
import com.example.update.AppUpdater
import org.junit.Test
import org.junit.Assert.*
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[34],application=android.app.Application::class)
class UpdateCheckClassifyTest {
 @Test fun metadataIsNotificationOnly(){
  assertNull(AppUpdater.parseNewer("""{"versionCode":1,"versionName":"1"}""",1))
  assertEquals(2L,AppUpdater.parseNewer("""{"versionCode":2,"versionName":"2"}""",1)!!.code)
  assertFalse(AppUpdater::class.java.declaredMethods.any{it.name=="download"||it.name=="install"})
 }
}
