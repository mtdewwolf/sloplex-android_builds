package app.sloplex.core.network

import app.sloplex.core.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*

/** Opt in with scripts/run-server-contracts.py; ordinary unit runs report this as skipped. */
class RealServerIntegrationTest {
    @Test fun actualGoServerIdentityAuthLibraryRangesDecisionAndWatchState() = runTest {
        val base=System.getenv("SLOPLEX_TEST_SERVER")
        assumeTrue("Use the real-server contract runner",base!=null)
        val draft=ServerProfile("",base!!,"fixture","",true)
        val public=createClient(draft)
        val identity=public.api.identity().container
        assertTrue(identity.machineIdentifier.isNotBlank())
        val profile=draft.copy(machineId=identity.machineIdentifier,version=identity.version)
        val login=public.api.login(LoginRequest("android-fixture","sloplex-local-contract-test-password","JUnit contract fixture"))
        val client=createClient(profile,login.token.token)
        try {
            assertEquals(login.account.id,client.api.account().id)
            assertTrue(client.api.tokens().tokens.any { it.id==login.token.id })
            val sections=client.api.sections().container.directories
            assertEquals(1,sections.size)
            val items=collectPages { client.api.library(sections.single().key,it,1) }
            assertEquals(1,items.size)
            val item=client.api.metadata(items.single().ratingKey).container.metadata.single()
            assertTrue(item.durationMs>0)
            assertEquals("h264",item.versions.single().videoCodec)
            val part=item.versions.single().parts.single()
            client.http.newCall(Request.Builder().url(client.url(part.key)).header("Range","bytes=0-31").build()).execute().use {
                assertEquals(206,it.code)
                assertTrue(it.header("Content-Range")!!.startsWith("bytes 0-31/"))
                assertEquals(32,it.body!!.bytes().size)
            }
            val decision=client.api.decision(mapOf("path" to "/library/metadata/${item.ratingKey}","protocol" to "hls","directPlay" to "1","containers" to "mp4","videoCodecs" to "h264","audioCodecs" to "aac","maxAudioChannels" to "2","hdr" to "0" )).container
            assertEquals(1000,decision.generalDecisionCode)
            client.api.timeline(item.ratingKey,5432,item.durationMs,"paused")
            assertEquals(5432,client.api.metadata(item.ratingKey).container.metadata.single().viewOffset)
            val secondLogin=public.api.login(LoginRequest("android-fixture","sloplex-local-contract-test-password","Second client"))
            val second=createClient(profile,secondLogin.token.token)
            try {
                second.api.timeline(item.ratingKey,6000,item.durationMs,"paused")
                client.api.timeline(item.ratingKey,5500,item.durationMs,"paused")
                // Existing server is last-arrival-wins, including backwards seeks.
                assertEquals(5500,second.api.metadata(item.ratingKey).container.metadata.single().viewOffset)
            } finally { second.api.logout() }
            client.api.watched(item.ratingKey)
            assertTrue(client.api.metadata(item.ratingKey).container.metadata.single().viewCount>0)
            client.api.unwatched(item.ratingKey)
            assertEquals(0,client.api.metadata(item.ratingKey).container.metadata.single().viewCount)
            assertTrue(client.api.search("Sloplex Fixture").container.metadata.isNotEmpty())
        } finally { client.api.logout() }
        assertEquals(401,runCatching { client.api.account(); 200 }.getOrElse { (it as retrofit2.HttpException).code() })
    }
}
