@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.sloplex.mobile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import app.sloplex.core.data.*
import app.sloplex.core.model.*
import app.sloplex.core.player.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/** Synthetic H.264/AAC media; not a substitute for the real-server hardware matrix. */
class PlaybackIntegrationTest {
    @get:org.junit.Rule val activity=androidx.test.ext.junit.rules.ActivityScenarioRule(MainActivity::class.java)
    @Test fun authenticatedRangesTextSubtitlesAndHlsSeekUseSourceTimeline() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val assets=instrumentation.context.assets
        val db=Room.inMemoryDatabaseBuilder(context,SloplexDatabase::class.java).build()
        val vault=TokenVault(context)
        val auth=AuthenticationRepository(vault,ProfileStore(context),db.cache())
        val engine=withContext(Dispatchers.Main) { PlaybackEngine(context,auth) }
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        val server=MockWebServer()
        val part=MediaPart(11,"/library/parts/11/1/test.mp4",24000,"mp4",streams=listOf(
            MediaStream(21,2,"aac",displayTitle="English stereo",channels=2,selected=true),
            MediaStream(22,3,"subrip",displayTitle="English",extendedDisplayTitle="English text",languageCode="eng",key="/library/streams/22")))
        val item=MediaItem("1",type="movie",title="Synthetic clip",duration=24000,versions=listOf(MediaVersion(10,24000,320,180,600,"h264","aac",2,"mp4",parts=listOf(part))))
        val mp4=assets.open("test.mp4").use { it.readBytes() }
        fun json(body: String) = MockResponse().setHeader("Content-Type","application/json").setBody(body)
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path=request.requestUrl!!.encodedPath
                return when {
                    path=="/sloplex/auth/login" -> json("""{"account":{"id":2,"name":"alice"},"token":{"id":5,"token":"media-test-token"}}""")
                    path=="/sloplex/account" -> json("""{"id":2,"name":"alice"}""")
                    path=="/sloplex/tokens" -> json("""{"tokens":[{"id":5}]}""")
                    path.endsWith("/decision") -> json("""{"MediaContainer":{"generalDecisionCode":${if(request.requestUrl!!.queryParameter("directPlay")=="0") 1001 else 1000}}}""")
                    path.endsWith("/start.m3u8") -> MockResponse().setHeader("Content-Type","application/vnd.apple.mpegurl").setBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=700000,CODECS=\"avc1.42c00d,mp4a.40.2\"\n/video/:/transcode/universal/session/test/index.m3u8\n")
                    path.endsWith("/stop") -> MockResponse().setHeadersDelay(2,java.util.concurrent.TimeUnit.SECONDS)
                    path.endsWith("/index.m3u8") -> MockResponse().setHeader("Content-Type","application/vnd.apple.mpegurl").setBody(assets.open("index.m3u8").bufferedReader().use { it.readText() })
                    path.endsWith(".ts") -> MockResponse().setHeader("Content-Type","video/mp2t").setBody(Buffer().write(assets.open(path.substringAfterLast('/')).use { it.readBytes() }))
                    path.startsWith("/library/parts/") && path.endsWith(".mp4") -> {
                        val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        val end=request.getHeader("Range")?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(mp4.lastIndex) ?: mp4.lastIndex
                        MockResponse().setResponseCode(if(request.getHeader("Range")!=null) 206 else 200).setHeader("Content-Type","video/mp4")
                            .setHeader("Accept-Ranges","bytes").apply { if(request.getHeader("Range")!=null) setHeader("Content-Range","bytes $start-$end/${mp4.size}") }.setBody(Buffer().write(mp4.copyOfRange(start,end+1)))
                    }
                    path=="/library/streams/22" -> MockResponse().setHeader("Content-Type","text/vtt").setBody("WEBVTT\n\n00:00:00.000 --> 00:00:23.000\nSloplex subtitle fixture\n")
                    else -> MockResponse().setResponseCode(200)
                }
            }
        }
        server.start()
        suspend fun await(check: () -> Boolean) { withTimeout(30000) { while(!check()) { assertNull(engine.state.value.error); delay(250) } } }
        try {
            val profile=ServerProfile("playback-test",server.url("/").toString(),"fixture","0.0.1",true)
            auth.login(profile,"alice","test-password","instrumentation")
            engine.play(item,2000)
            await { engine.state.value.playing && engine.state.value.positionMs>=2000 }
            engine.changeTracks(subtitleId=22)
            await { requests.any { it.requestUrl?.encodedPath=="/library/streams/22" } }
            engine.seek(8000)
            await { engine.state.value.positionMs>=8000 }
            withContext(Dispatchers.Main) { engine.pause() }
            engine.changeQuality(Quality.HD)
            await { engine.state.value.mode==PlaybackMode.HLS && !engine.state.value.buffering && !engine.state.value.playing }
            engine.seek(15000)
            await { engine.state.value.positionMs>=15000 && !engine.state.value.buffering && !engine.state.value.playing }
            engine.changeTracks(subtitleId=0)
            await { !engine.state.value.buffering && !engine.state.value.playing }
            withContext(Dispatchers.Main) { engine.toggle() }
            await { engine.state.value.playing }
            coroutineScope {
                val background=async(Dispatchers.Main) { engine.background() }
                withTimeout(5000) { while(withContext(Dispatchers.Main) { engine.player!=null }) delay(25) }
                engine.foreground()
                background.await()
            }
            await { engine.state.value.playing && engine.state.value.positionMs>=15000 }
            assertTrue(auth.logout { detachedSession ->
                assertNull(vault.read("playback-test"))
                engine.stop(detachedSession)
            })
            val logoutIndex=requests.indexOfLast { it.requestUrl?.encodedPath=="/sloplex/auth/logout" }
            val finalProgressIndex=requests.indexOfLast { it.requestUrl?.encodedPath=="/:/timeline" }
            val finalStopIndex=requests.indexOfLast { it.requestUrl?.encodedPath?.endsWith("/stop")==true }
            assertTrue(finalProgressIndex in 0 until logoutIndex)
            assertTrue(finalStopIndex in 0 until logoutIndex)
            val starts=requests.filter { it.requestUrl?.encodedPath?.endsWith("/start.m3u8")==true }
            assertTrue(starts.size>=2)
            assertTrue(starts.any { it.requestUrl!!.queryParameter("offset")!!.toDouble()==15.0 })
            assertTrue(requests.any { it.getHeader("Range")!=null })
            assertTrue(requests.filter { it.requestUrl?.encodedPath?.startsWith("/library/")==true }.all { it.getHeader("X-Plex-Token")=="media-test-token" })
            assertTrue(requests.any { it.requestUrl?.queryParameter("time")?.toLongOrNull()?.let { time -> time>=15000 }==true })
            assertTrue(requests.any { it.requestUrl?.encodedPath?.endsWith("/stop")==true })
        } finally { withContext(Dispatchers.Main) { engine.stop(); engine.release() }; vault.clear("playback-test"); server.shutdown(); db.close() }
    }
}
