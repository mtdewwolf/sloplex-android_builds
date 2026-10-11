package app.sloplex.mobile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import app.sloplex.core.data.*
import app.sloplex.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

class StorageSecurityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun cachePartitionsServersAccountsAndClearsOnlySelectedAccount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SloplexDatabase::class.java).build()
        try {
            val cache=db.cache()
            val item=MediaItem("1",type="movie",title="Alice's film")
            cache.replace(CacheScope("server-a",2),"recent",listOf(item))
            cache.replace(CacheScope("server-b",2),"recent",listOf(item.copy(title="Other server")))
            assertEquals(1,cache.observe("server-a",2,"recent").first().size)
            assertTrue(cache.observe("server-a",3,"recent").first().isEmpty())
            cache.clearAccount("server-a",2)
            assertTrue(cache.observe("server-a",2,"recent").first().isEmpty())
            assertEquals(1,cache.observe("server-b",2,"recent").first().size)
        } finally { db.close() }
    }
    @Test fun keystoreCiphertextIsBoundToServerAndOldRevocationCannotDeleteNewLogin() {
        val vault=TokenVault(context)
        val old=StoredSession(2,50,"secret-test-token-old")
        val new=StoredSession(2,51,"secret-test-token-new")
        try {
            vault.save("security-test",old)
            assertEquals(old,vault.read("security-test"))
            val prefs=context.getSharedPreferences("session_ciphertext",0)
            val ciphertext=prefs.getString("security-test",null)!!
            assertFalse(ciphertext.contains(old.token))
            prefs.edit().putString("other-security-test",ciphertext).commit()
            assertNull(vault.read("other-security-test"))
            vault.save("security-test",new)
            vault.clearIfToken("security-test",old.tokenId)
            assertEquals(new,vault.read("security-test"))
            vault.clearIfToken("security-test",new.tokenId)
            assertNull(vault.read("security-test"))
        } finally { vault.clear("security-test"); vault.clear("other-security-test") }
    }
}
