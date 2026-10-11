package app.sloplex.core.network

import app.sloplex.core.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import retrofit2.HttpException

class ContractTest {
    private fun profile(server: MockWebServer) = ServerProfile("machine",server.url("/").toString(),"test","0.1.0",true)
    @Test fun identityRequestsJsonAndParsesTheEnvelope() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"MediaContainer":{"size":0,"machineIdentifier":"abc123","version":"0.0.1"}}"""))
            val client=createClient(profile(server))
            assertEquals("abc123",client.api.identity().container.machineIdentifier)
            val request=server.takeRequest(); assertEquals("/identity",request.path);assertEquals("application/json",request.getHeader("Accept"));assertNull(request.getHeader("X-Plex-Token"))
        }
    }
    @Test fun accountJsonIsNotWrappedAndTokenStaysInHeaders() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":2,"name":"alice","admin":false,"libraries":null,"hasPassword":true}"""))
            val me=createClient(profile(server),"device-secret").api.account()
            assertEquals(2,me.id);assertNull(me.libraries)
            val request=server.takeRequest();assertEquals("device-secret",request.getHeader("X-Plex-Token"));assertFalse(request.path!!.contains("device-secret"))
        }
    }
    @Test fun omittedArraysAndJsonBooleansParse() {
        val envelope=wireJson.decodeFromString<Envelope>("""{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"7","title":"Movie","type":"movie","Media":[{"id":1,"Part":[{"id":2,"Stream":[{"id":3,"streamType":3,"codec":"subrip","selected":true,"forced":true}]}]}]}]}}""")
        assertTrue(envelope.container.metadata.single().versions.single().parts.single().streams.single().forced)
        assertTrue(wireJson.decodeFromString<Envelope>("""{"MediaContainer":{"size":0}}""").container.metadata.isEmpty())
    }
    @Test fun redirectsNeverForwardToken() = runTest {
        MockWebServer().use { origin -> MockWebServer().use { other ->
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location",other.url("/secret")))
            val client=createClient(profile(origin),"device-secret")
            val failure=runCatching { client.api.account() }.exceptionOrNull()
            assertTrue(failure is HttpException);assertEquals(0,other.requestCount)
        } }
    }
    @Test fun rangeHeadersSurviveAuthentication() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 4-7/10").setBody("4567"))
            val client=createClient(profile(server),"t")
            client.http.newCall(okhttp3.Request.Builder().url(client.url("/library/parts/1/0/file.mp4")).header("Range","bytes=4-7").build()).execute().use { assertEquals(206,it.code);assertEquals("4567",it.body!!.string()) }
            assertEquals("bytes=4-7",server.takeRequest().getHeader("Range"))
        }
    }
    @Test fun errorKindsMatchStatusAndRetryAfter() = runTest {
        MockWebServer().use { server ->
            for((status,kind) in listOf(401 to ErrorKind.AUTHENTICATION,403 to ErrorKind.PERMISSION,404 to ErrorKind.NOT_FOUND,503 to ErrorKind.TRANSCODE_CAPACITY,429 to ErrorKind.RATE_LIMIT)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Retry-After","10").setBody("error"))
                assertEquals(kind,runCatching { createClient(profile(server),"t").api.account() }.exceptionOrNull()!!.asSloplexError().kind)
            }
        }
    }
    @Test fun malformedJsonIsIncompatible() = runTest {
        MockWebServer().use { server -> server.enqueue(MockResponse().setBody("{bad")); assertEquals(ErrorKind.INCOMPATIBLE,runCatching { createClient(profile(server)).api.identity() }.exceptionOrNull()!!.asSloplexError().kind) }
    }
    @Test fun paginationUsesReturnedLengthAndStopsAtTotal() = runTest {
        val offsets=mutableListOf<Int>()
        val result=collectPages { start -> offsets.add(start);Envelope(MediaContainer(totalSize=3,offset=start,metadata=(start until minOf(start+2,3)).map { MediaItem(it.toString(),type="movie",title="t") })) }
        assertEquals(listOf(0,2),offsets);assertEquals(3,result.size)
    }
    @Test fun emptyPageTerminatesAndIncorrectOffsetFails() = runTest {
        assertTrue(collectPages { Envelope(MediaContainer(totalSize=5)) }.isEmpty())
        assertTrue(runCatching { collectPages { Envelope(MediaContainer(totalSize=5,offset=9,metadata=listOf(MediaItem("1",type="movie",title="t")))) } }.exceptionOrNull() is SloplexException)
    }
    @Test fun gdmUsesPacketSourceAndValidatesHeaders() {
        val data="HTTP/1.0 200 OK\r\nContent-Type: plex/media-server\r\nResource-Identifier: abc\r\nName: Sloplex\r\nPort: 32400\r\nVersion: 0.1.0\r\n\r\n".toByteArray()
        assertEquals("http://192.168.1.10:32400/",Gdm.parse(data,InetAddress.getByName("192.168.1.10"))!!.url)
        assertNull(Gdm.parse(data,InetAddress.getByName("8.8.8.8")))
        assertNull(Gdm.parse(data.toString(Charsets.UTF_8).replace("32400","99999").toByteArray(),InetAddress.getByName("192.168.1.10")))
    }
    @Test fun unsafeUrlsAndForeignArtworkAreRejected() {
        listOf("https://user:pass@example.com/","https://example.com/?token=t","file:///tmp/a","https://example.com/path").forEach { assertTrue(runCatching { SafeUrl.parse(it) }.isFailure) }
        val base=SafeUrl.parse("https://example.com/")
        assertTrue(runCatching { SafeUrl.resolve(base,"https://evil.example/poster") }.isFailure)
        assertTrue(runCatching { SafeUrl.resolve(base,"http://example.com/file") }.isFailure)
        assertEquals("http://192.168.1.10:32400/",SafeUrl.parse("192.168.1.10").toString())
        assertEquals("http://192.168.1.10:8080/",SafeUrl.parse("192.168.1.10:8080").toString())
        assertEquals("http://192.168.1.10/",SafeUrl.parse("192.168.1.10:80").toString())
        listOf("https://user:pass@example.com/web/","https://example.com/webhooks","https://example.com/api/web/").forEach { assertTrue(it,runCatching { SafeUrl.parse(it) }.isFailure) }
    }
    @Test fun tailscaleServeAndWebAppAddressesNormalizeToTheOrigin() {
        val origin="https://sloplex.tail850cd1.ts.net/"
        listOf("https://sloplex.tail850cd1.ts.net/web/","https://sloplex.tail850cd1.ts.net/web","https://sloplex.tail850cd1.ts.net/web/index.html#!/","https://sloplex.tail850cd1.ts.net/web/?x=1"," sloplex.tail850cd1.ts.net/web/","sloplex.tail850cd1.ts.net","https://sloplex.tail850cd1.ts.net").forEach { assertEquals(it,origin,SafeUrl.parse(it).toString()) }
        assertEquals("https://sloplex.tail850cd1.ts.net:32400/",SafeUrl.parse("sloplex.tail850cd1.ts.net:32400").toString())
    }
    @Test fun remoteHttpRefusesCredentialsAndCleartextNeedsConsent() {
        val s=ServerProfile("x","http://8.8.8.8:32400/","bad","v",true)
        assertTrue(runCatching { createClient(s,"secret") }.isFailure)
        assertTrue(runCatching { createClient(s.copy(url="http://192.168.1.1/",allowLanHttp=false),"secret") }.isFailure)
    }
    @Test fun watchPositionIsMillisecondsAndEmptyResponseSucceeds() = runTest {
        MockWebServer().use { server -> server.enqueue(MockResponse());createClient(profile(server),"t").api.timeline("7",90125,180000,"paused"); val request=server.takeRequest();assertEquals("PUT",request.method);assertTrue(request.path!!.startsWith("/:/timeline?"));assertTrue(request.path!!.contains("time=90125")) }
    }
}
