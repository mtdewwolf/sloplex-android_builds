package app.sloplex.mobile
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class FoundationLaunchTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun launcherRendersNativeApp() { compose.onNodeWithText("SLOPLEX").assertIsDisplayed() }
}
