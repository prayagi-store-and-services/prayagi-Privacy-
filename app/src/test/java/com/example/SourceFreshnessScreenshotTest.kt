package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.siteblock.SiteBlockingScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34], application = android.app.Application::class)
class SourceFreshnessScreenshotTest {
    @get:Rule val composeTestRule = createComposeRule()
    @Test fun sourceUnknownIsVisibleWithoutInventedFreshness() {
        composeTestRule.setContent { MaterialTheme(colorScheme = lightColorScheme()) { Surface(Modifier.fillMaxSize()) { SiteBlockingScreen(onBack = {}) } } }
        composeTestRule.onNodeWithText("Update lists now", substring = true).performScrollTo()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/source-freshness.png")
    }
}
