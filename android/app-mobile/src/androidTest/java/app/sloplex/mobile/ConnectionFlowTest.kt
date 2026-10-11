@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.sloplex.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.sloplex.feature.session.SessionViewModel
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*

class ConnectionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: MockWebServer
    @Before fun setup() {
        server=MockWebServer()
        val mp4=InstrumentationRegistry.getInstrumentation().context.assets.open("test.mp4").use { it.readBytes() }
        val watched=java.util.concurrent.atomic.AtomicBoolean(false)
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path=request.path!!.substringBefore('?')
                if(path=="/library/parts/11/1/test.mp4") {
                    val range=request.getHeader("Range")
                    val start=range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                    val end=range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(mp4.lastIndex) ?: mp4.lastIndex
                    return MockResponse().setResponseCode(if(range!=null) 206 else 200)
                        .setHeader("Content-Type","video/mp4").setHeader("Accept-Ranges","bytes")
                        .apply { if(range!=null) setHeader("Content-Range","bytes $start-$end/${mp4.size}") }
                        .setBody(Buffer().write(mp4.copyOfRange(start,end+1)))
                }
                if(path.endsWith("/decision")) {
                    // Keep the player screen visible with a null player before async preparation completes.
                    return MockResponse().setHeader("Content-Type","application/json")
                        .setBody("""{"MediaContainer":{"generalDecisionCode":1000}}""")
                        .setBodyDelay(500,java.util.concurrent.TimeUnit.MILLISECONDS)
                }
                val body=when(path) {
                    "/identity" -> """{"MediaContainer":{"size":0,"machineIdentifier":"test-ui","version":"0.0.1"}}"""
                    "/sloplex/auth/login" -> """{"account":{"id":2,"name":"alice","admin":false},"token":{"id":5,"device":"UI test","token":"test-device-token"}}"""
                    "/sloplex/account" -> """{"id":2,"name":"alice","admin":false}"""
                    "/sloplex/tokens" -> """{"tokens":[{"id":5,"device":"UI test"}]}"""
                    "/library/sections" -> """{"MediaContainer":{"size":1,"Directory":[{"key":"1","title":"Movies","type":"movie"}]}}"""
                    "/library/recentlyAdded", "/library/sections/1/all", "/library/metadata/1" -> """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"1","type":"movie","title":"Fixture movie","year":2026,"duration":24000,"viewCount":${if(watched.get()) 1 else 0},"summary":"A verified-contract UI fixture.","Media":[{"id":10,"width":320,"height":180,"videoCodec":"h264","audioCodec":"aac","container":"mp4","Part":[{"id":11,"key":"/library/parts/11/1/test.mp4"}]}]}]}}"""
                    "/:/scrobble" -> { watched.set(true); return MockResponse().setResponseCode(200) }
                    "/:/unscrobble" -> { watched.set(false); return MockResponse().setResponseCode(200) }
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
        compose.onNodeWithText("Server address").performTextInput(server.url("/").toString())
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Connect",useUnmergedTree=true).performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Username").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Username").performTextInput("alice")
        compose.onNodeWithText("Password").performTextInput("wonderland")
        compose.onNodeWithText("Password").performImeAction()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Tonight starts here").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Movies",useUnmergedTree=true).onLast().performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Fixture movie").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Fixture movie").performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Mark watched").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mark watched").performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Mark unwatched").fetchSemanticsNodes().isNotEmpty() }
        lateinit var vm: SessionViewModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[SessionViewModel::class.java] }
        compose.onNodeWithText("Play",substring=false).performScrollTo().performClick()
        // Advancing audio/time alone must not pass: the actual Compose player view must render video.
        compose.waitUntil(30000) {
            var rendered=false
            compose.runOnUiThread {
                assertNull(vm.playback.value.error)
                rendered=(vm.engine.player?.videoDecoderCounters?.renderedOutputBufferCount ?: 0)>0
            }
            rendered
        }
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Mark unwatched").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Mark unwatched").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Search",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Search your library").performTextInput("no-matching-title")
        compose.waitUntil(30000) { compose.onAllNodesWithText("No matches yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("A whole library to explore").assertExists()
        compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Sign out",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        val vault=app.sloplex.core.data.TokenVault(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        org.junit.Assert.assertNotNull(vault.read("test-ui"))
        compose.onNodeWithText("Sign out",useUnmergedTree=true).performClick()
        compose.waitUntil(45000) { compose.onAllNodesWithText("Username").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(45000) { vault.read("test-ui")==null }
    }
}
