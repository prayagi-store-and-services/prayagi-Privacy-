package com.example
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.siteblock.DnsChoicesCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=Application::class)
class DnsChoicesTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 private fun show(){rule.setContent{MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){DnsChoicesCard()}}}}
 @Test fun choicesAreDistinctAndCopyExactProviderHostnames(){
  show()
  rule.onNodeWithTag("dns_copy_ads").performScrollTo().performClick()
  rule.runOnIdle{assertEquals("dns.adguard-dns.com",(rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())}
  rule.onNodeWithTag("dns_copy_family").performScrollTo().performClick()
  rule.runOnIdle{assertEquals("family.adguard-dns.com",(rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())}
  rule.onNodeWithText("Family DNS copied. Paste it in Android Private DNS settings.").performScrollTo().assertIsDisplayed()
 }
 @Test fun nativeDnsChoicesPixels(){
  show();rule.waitForIdle()
  rule.runOnIdle{val v=rule.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));java.io.File("build/outputs/dns-choices.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 }
}
