@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package app.sloplex.core.data

import android.content.Context
import android.net.wifi.WifiManager
import androidx.room.Room
import app.sloplex.core.model.*
import app.sloplex.core.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

data class Session(val profile: ServerProfile, val account: Account, val tokenId: Int, val client: ServerClient) {
    val scope get() = CacheScope(profile.machineId, account.id)
}

@Singleton class AuthenticationRepository @Inject constructor(private val vault: TokenVault, private val profiles: ProfileStore, private val cache: CacheDao) {
    private val mutable = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = mutable.asStateFlow()
    suspend fun restore(): ServerProfile? = withContext(Dispatchers.IO) {
        val profile = profiles.active() ?: return@withContext null
        restoreProfile(profile)
        profile
    }
    suspend fun restoreProfile(profile: ServerProfile) = withContext(Dispatchers.IO) {
        mutable.value = null
        val stored = vault.read(profile.machineId) ?: return@withContext
        val identity = createClient(profile).api.identity().container
        if(identity.machineIdentifier != profile.machineId) throw SloplexException(ErrorKind.INVALID_SOURCE,"Server identity changed. Reconnect before signing in.")
        val client = createClient(profile, stored.token)
        try {
            val me = client.api.account()
            if(me.id != stored.accountId || client.api.tokens().tokens.none { it.id == stored.tokenId }) throw SloplexException(ErrorKind.AUTHENTICATION, "Your device token was revoked. Sign in again.")
            mutable.value = Session(profile,me,stored.tokenId,client)
        } catch(e: Exception) { if(e.asSloplexError().kind == ErrorKind.AUTHENTICATION) { vault.clear(profile.machineId); cache.clearAccount(profile.machineId,stored.accountId) }; throw e }
    }
    suspend fun login(profile: ServerProfile, username: String, password: String, device: String) = withContext(Dispatchers.IO) {
        val response = createClient(profile).api.login(LoginRequest(username.trim(),password,device))
        require(response.token.token.isNotBlank()) { "Missing device token" }
        val client = createClient(profile,response.token.token)
        val me = client.api.account()
        if(me.id != response.account.id || client.api.tokens().tokens.none { it.id == response.token.id }) throw SloplexException(ErrorKind.AUTHENTICATION, "Could not validate the device session.")
        vault.save(profile.machineId,StoredSession(me.id,response.token.id,response.token.token))
        mutable.value = Session(profile,me,response.token.id,client)
    }
    /** Checks token presence as well as account ID, because trusted peers can fall back to owner. */
    suspend fun verified(): Session {
        val s = mutable.value ?: throw SloplexException(ErrorKind.AUTHENTICATION, "Sign in to continue.")
        verifyToken(s)
        if(mutable.value !== s) throw SloplexException(ErrorKind.AUTHENTICATION, "The account changed.")
        return s
    }
    /** Only permits final playback cleanup after local logout, before remote revocation. */
    suspend fun verifiedForLogout(s: Session): Session { verifyToken(s); return s }
    private suspend fun verifyToken(s: Session) {
        try {
            if(s.client.api.account().id != s.account.id || s.client.api.tokens().tokens.none { it.id == s.tokenId }) throw SloplexException(ErrorKind.AUTHENTICATION, "Your device session was revoked.")
        } catch(e: Exception) {
            if(e.asSloplexError().kind == ErrorKind.AUTHENTICATION) invalidate(s)
            throw e
        }
    }
    private suspend fun invalidate(s: Session) { if(mutable.value === s) mutable.value=null; withContext(NonCancellable + Dispatchers.IO) { vault.clearIfToken(s.profile.machineId,s.tokenId); cache.clearAccount(s.profile.machineId,s.account.id) } }
    suspend fun logout(beforeRemoteRevoke: suspend (Session) -> Unit = {}): Boolean {
        val s = mutable.value ?: return true
        invalidate(s)
        try { beforeRemoteRevoke(s) } catch(e: CancellationException) { throw e } catch(_: Exception) { /* Local sign-out is already complete; still revoke remotely. */ }
        return try { s.client.api.logout(); true } catch(e: CancellationException) { throw e } catch(_: Exception) { false }
    }
    suspend fun switchServer() { mutable.value=null }
}

@Singleton class LibraryRepository @Inject constructor(private val auth: AuthenticationRepository, private val cache: CacheDao) {
    suspend fun sections() = auth.verified().client.api.sections().container.directories
    private suspend fun fetch(bucket: String, call: suspend (SloplexApi) -> List<MediaItem>): List<MediaItem> {
        val s = auth.verified()
        val data = call(s.client.api)
        if(auth.session.value !== s) throw SloplexException(ErrorKind.AUTHENTICATION,"The account changed.")
        auth.verified()
        cache.replace(s.scope,bucket,data)
        return data
    }
    fun cached(bucket: String): Flow<List<MediaItem>> = auth.session.flatMapLatest { s -> if(s == null) flowOf(emptyList()) else cache.observe(s.profile.machineId,s.account.id,bucket).map { rows -> rows.map { wireJson.decodeFromString<MediaItem>(it.json) } } }
    suspend fun cachedAuthorized(bucket: String): List<MediaItem> {
        val s=auth.verified()
        val section=bucket.removePrefix("library:")
        if(!bucket.startsWith("library:") || s.client.api.sections().container.directories.none { it.key==section }) {
            cache.clearBucket(s.profile.machineId,s.account.id,bucket)
            throw SloplexException(ErrorKind.PERMISSION,"This library is no longer available to your account.")
        }
        val items=cached(bucket).first()
        if(auth.session.value !== s) throw SloplexException(ErrorKind.AUTHENTICATION,"The account changed.")
        return items
    }
    suspend fun recent() = fetch("recent") { it.recent().container.metadata }
    suspend fun deck() = fetch("deck") { it.onDeck().container.metadata }
    suspend fun library(id: String) = fetch("library:$id") { api -> collectPages { api.library(id,it) } }
    suspend fun search(query: String) = fetch("search") { api -> collectPages { api.search(query,it) } }
    suspend fun metadata(id: String): MediaItem {
        val s = auth.verified()
        val item=s.client.api.metadata(id).container.metadata.singleOrNull() ?: throw SloplexException(ErrorKind.NOT_FOUND,"Media was removed.")
        if(auth.session.value !== s) throw SloplexException(ErrorKind.AUTHENTICATION,"The account changed.")
        auth.verified()
        return item
    }
    suspend fun children(id: String): List<MediaItem> = fetch("children:$id") { api -> collectPages { api.children(id,it) } }
    suspend fun leaves(id: String) = fetch("leaves:$id") { api -> collectPages { api.leaves(id,it) } }
    suspend fun playlists() = fetch("playlists") { it.playlists().container.metadata }
    suspend fun collections(id: String) = fetch("collections:$id") { it.collections(id).container.metadata }
    suspend fun members(item: MediaItem) = fetch("members:${item.type}:${item.ratingKey}") { api -> if(item.type=="playlist") api.playlistItems(item.ratingKey).container.metadata else api.collectionItems(item.ratingKey).container.metadata }
    suspend fun watched(id: String, watched: Boolean) { val api = auth.verified().client.api; if(watched) api.watched(id) else api.unwatched(id) }
}

@Module @InstallIn(SingletonComponent::class) object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): SloplexDatabase = Room.databaseBuilder(context, SloplexDatabase::class.java,"sloplex.db").build()
    @Provides fun cache(database: SloplexDatabase): CacheDao = database.cache()
}
