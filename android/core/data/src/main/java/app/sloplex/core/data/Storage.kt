package app.sloplex.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import app.sloplex.core.model.*
import app.sloplex.core.network.wireJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton


@Serializable data class StoredSession(val accountId: Int, val tokenId: Int, val token: String) {
    override fun toString() = "StoredSession(accountId=$accountId, tokenId=$tokenId)"
}
/** Passwords never enter persistent storage. Only AES-GCM ciphertext is persisted. */
@Singleton class TokenVault @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("session_ciphertext", Context.MODE_PRIVATE)
    private val alias = "sloplex.session.v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(machineId: String, session: StoredSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(machineId.toByteArray()) }
        val bytes = cipher.iv + cipher.doFinal(wireJson.encodeToString(session).toByteArray())
        check(prefs.edit().putString(machineId, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    @Synchronized fun read(machineId: String): StoredSession? {
        val value = prefs.getString(machineId, null) ?: return null
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))); updateAAD(machineId.toByteArray()) }
            wireJson.decodeFromString<StoredSession>(cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8))
        } catch (_: Exception) { clear(machineId); null }
    }
    @Synchronized fun clearIfToken(machineId: String, tokenId: Int) { if(read(machineId)?.tokenId == tokenId) clear(machineId) }
    @Synchronized fun clear(machineId: String) { check(prefs.edit().remove(machineId).commit()) }
}

@Entity(tableName = "metadata_cache", primaryKeys = ["machineId", "accountId", "bucket", "itemId"])
data class CacheEntry(val machineId: String, val accountId: Int, val bucket: String, val itemId: String, val ordinal: Int, val json: String, val cachedAt: Long)
@Dao abstract class CacheDao {
    @Query("SELECT * FROM metadata_cache WHERE machineId = :machine AND accountId = :account AND bucket = :bucket ORDER BY ordinal")
    abstract fun observe(machine: String, account: Int, bucket: String): Flow<List<CacheEntry>>
    @Query("DELETE FROM metadata_cache WHERE machineId = :machine AND accountId = :account AND bucket = :bucket") abstract suspend fun clearBucket(machine: String, account: Int, bucket: String)
    @Query("DELETE FROM metadata_cache WHERE machineId = :machine AND accountId = :account") abstract suspend fun clearAccount(machine: String, account: Int)
    @Query("DELETE FROM metadata_cache WHERE cachedAt < :before") abstract suspend fun expire(before: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insert(entries: List<CacheEntry>)
    @Query("DELETE FROM metadata_cache WHERE rowid IN (SELECT rowid FROM metadata_cache ORDER BY cachedAt DESC LIMIT -1 OFFSET 5000)") abstract suspend fun bound()
    @Transaction open suspend fun replace(scope: CacheScope, bucket: String, items: List<MediaItem>) {
        clearBucket(scope.machineId, scope.accountId, bucket)
        val now = System.currentTimeMillis()
        insert(items.take(2000).mapIndexed { i, item -> CacheEntry(scope.machineId, scope.accountId,bucket,item.ratingKey,i,wireJson.encodeToString(item),now) })
        expire(now - 7L*24*60*60*1000); bound()
    }
}
@Database(entities = [CacheEntry::class], version = 1, exportSchema = true)
abstract class SloplexDatabase : RoomDatabase() { abstract fun cache(): CacheDao }
