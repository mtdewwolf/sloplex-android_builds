package app.sloplex.mobile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import app.sloplex.core.data.*
import app.sloplex.core.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class AuthenticationContractTest {
    @Test fun trustedOwnerFallbackWithRevokedDeviceNeverRestoresSession() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,SloplexDatabase::class.java).build()
        val server=MockWebServer()
        val vault=TokenVault(context)
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type","application/json").setBody(when(request.path) {
                "/identity" -> """{"MediaContainer":{"machineIdentifier":"auth-test","version":"0.0.1"}}"""
                "/sloplex/account" -> """{"id":1,"name":"owner","admin":true}"""
                else -> """{"tokens":[]}"""
            })
        }
        server.start()
        try {
            // Even an owner token must appear in the owner's device-token list.
            vault.save("auth-test",StoredSession(1,55,"revoked-owner-device-token"))
            val auth=AuthenticationRepository(vault,ProfileStore(context),db.cache())
            val error=runCatching { auth.restoreProfile(ServerProfile("auth-test",server.url("/").toString(),"test","0.0.1",true)) }.exceptionOrNull()
            assertEquals(ErrorKind.AUTHENTICATION,(error as SloplexException).kind)
            assertNull(auth.session.value)
            assertNull(vault.read("auth-test"))
        } finally { vault.clear("auth-test"); server.shutdown(); db.close() }
    }
    @Test fun identityChangeIsRejectedBeforeStoredTokenLeavesDevice() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,SloplexDatabase::class.java).build()
        val server=MockWebServer()
        val vault=TokenVault(context)
        server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"MediaContainer":{"machineIdentifier":"impostor","version":"0.0.1"}}"""))
        server.start()
        try {
            vault.save("identity-test",StoredSession(2,44,"stored-test-token"))
            val auth=AuthenticationRepository(vault,ProfileStore(context),db.cache())
            val error=runCatching { auth.restoreProfile(ServerProfile("identity-test",server.url("/").toString(),"test","0.0.1",true)) }.exceptionOrNull()
            assertEquals(ErrorKind.INVALID_SOURCE,(error as SloplexException).kind)
            assertNull(server.takeRequest().getHeader("X-Plex-Token"))
            assertEquals(1,server.requestCount)
        } finally { vault.clear("identity-test"); server.shutdown(); db.close() }
    }
}
