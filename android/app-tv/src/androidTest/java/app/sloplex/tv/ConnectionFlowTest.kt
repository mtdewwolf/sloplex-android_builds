package app.sloplex.tv

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class ConnectionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: MockWebServer
    @Before fun setup() {
        server=MockWebServer()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path=request.path!!.substringBefore('?')
                val body=when(path) {
                    "/identity" -> """{"MediaContainer":{"size":0,"machineIdentifier":"test-ui","version":"0.0.1"}}"""
                    "/sloplex/auth/login" -> """{"account":{"id":2,"name":"alice","admin":false},"token":{"id":5,"device":"UI test","token":"test-device-token"}}"""
                    "/sloplex/account" -> """{"id":2,"name":"alice","admin":false}"""
                    "/sloplex/tokens" -> """{"tokens":[{"id":5,"device":"UI test"}]}"""
                    "/library/sections" -> """{"MediaContainer":{"size":1,"Directory":[{"key":"1","title":"Movies","type":"movie"}]}}"""
                    "/library/recentlyAdded", "/library/sections/1/all", "/library/metadata/1" -> """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"1","type":"movie","title":"Fixture movie","year":2026,"duration":24000,"summary":"A verified-contract UI fixture.","Media":[{"id":10,"width":320,"height":180,"videoCodec":"h264","audioCodec":"aac","container":"mp4","Part":[{"id":11,"key":"/library/parts/11/1/test.mp4"}]}]}]}}"""
                    "/sloplex/auth/logout" -> return MockResponse().setResponseCode(204)
                    else -> """{"MediaContainer":{"size":0}}"""
                }
                return MockResponse().setHeader("Content-Type","application/json").setBody(body)
            }
        }
        server.start()
    }
    @After fun cleanup() { server.shutdown() }
    @Test fun connectionLoginNavigationAndLogout() {
        compose.onNodeWithText("Server URL").performTextInput(server.url("/").toString())
        compose.onNodeWithText("Enable private LAN HTTP").performClick()
        compose.onNodeWithText("Connect",useUnmergedTree=true).performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Username").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Username").performTextInput("alice")
        compose.onNodeWithText("Password").performTextInput("wonderland")
        compose.onNodeWithText("Password").performImeAction()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Tonight starts here").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText("Movies").assertIsFocused()
        compose.onAllNodesWithText("Movies",useUnmergedTree=true).onLast().performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Fixture movie").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Fixture movie").performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Mark watched").fetchSemanticsNodes().isNotEmpty() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Sign out",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sign out",useUnmergedTree=true).performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Username").fetchSemanticsNodes().isNotEmpty() }
    }
}
