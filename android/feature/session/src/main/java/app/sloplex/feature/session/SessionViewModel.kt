package app.sloplex.feature.session

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sloplex.core.data.*
import app.sloplex.core.model.*
import app.sloplex.core.network.*
import app.sloplex.core.player.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class Screen { CONNECTION, LOGIN, HOME, LIBRARY, DETAILS, SEARCH, SETTINGS }
data class MediaRow(val title: String, val items: List<MediaItem>)
data class AppState(val screen: Screen = Screen.CONNECTION, val busy: Boolean = false, val error: String? = null,
    val profile: ServerProfile? = null, val account: Account? = null, val profiles: List<ServerProfile> = emptyList(),
    val discovered: List<DiscoveredServer> = emptyList(), val discovering: Boolean = false,
    val sections: List<LibrarySection> = emptyList(), val rows: List<MediaRow> = emptyList(), val items: List<MediaItem> = emptyList(),
    val selectedSection: LibrarySection? = null, val detail: MediaItem? = null, val children: List<MediaItem> = emptyList(),
    val nextEpisode: MediaItem? = null, val query: String = "", val showPlayer: Boolean = false, val tokens: List<DeviceToken> = emptyList())

@HiltViewModel class SessionViewModel @Inject constructor(
    private val servers: ServerRepository, private val auth: AuthenticationRepository,
    private val library: LibraryRepository, val engine: PlaybackEngine, private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    val playback = engine.state
    val session = auth.session
    private var operation: Job? = null
    private var searchJob: Job? = null
    private var requestGeneration = 0L
    private val history = mutableListOf<AppState>()
    init {
        viewModelScope.launch { servers.saved.collect { profiles -> mutable.update { it.copy(profiles=profiles) } } }
        viewModelScope.launch {
            auth.session.collect { session ->
                if(session==null && auth.session.value==null && state.value.account!=null) {
                    requestGeneration++; operation?.cancel(); searchJob?.cancel()
                    history.clear(); saved["detailId"]=null
                    mutable.update { AppState(screen=Screen.LOGIN,profile=it.profile,profiles=it.profiles,error="Your device session ended. Sign in again.") }
                    engine.stop()
                }
            }
        }
        discover()
        task {
            val profile = auth.restore()
            mutable.update { it.copy(profile=profile,account=auth.session.value?.account) }
            if(auth.session.value != null) {
                loadHome()
                saved.get<String>("detailId")?.let { loadDetail(it) }
            } else if(profile != null) mutable.update { it.copy(screen=Screen.LOGIN) }
        }
    }
    private fun task(block: suspend () -> Unit) {
        val generation=++requestGeneration
        operation?.cancel()
        searchJob?.cancel()
        operation=viewModelScope.launch {
            mutable.update { it.copy(busy=true,error=null) }
            try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) {
                val error=e.asSloplexError()
                if(error.kind==ErrorKind.AUTHENTICATION && auth.session.value==null) { engine.stop(); history.clear(); mutable.update { AppState(screen=Screen.LOGIN,profile=it.profile,profiles=it.profiles,error=error.message) } }
                else mutable.update { it.copy(error=error.message) }
            } finally { if(generation==requestGeneration) mutable.update { it.copy(busy=false) } }
        }
    }
    fun clearError() { mutable.update { it.copy(error=null) } }
    fun discover() { viewModelScope.launch {
        mutable.update { it.copy(discovering=true) }
        try { val result=servers.discover(); mutable.update { it.copy(discovered=result) } }
        catch(e: CancellationException) { throw e } catch(_: Exception) { mutable.update { it.copy(error="Local discovery is unavailable. Enter your server address manually.") } }
        finally { mutable.update { it.copy(discovering=false) } }
    } }
    fun connect(url: String, allowHttp: Boolean, expected: String? = null) = task {
        mutable.update { it.copy(account=null) }; engine.stop(); auth.switchServer(); history.clear(); saved["detailId"]=null
        val profile=servers.connect(url,allowHttp,expected)
        mutable.update { AppState(screen=Screen.LOGIN,profile=profile,profiles=it.profiles,busy=true) }
        auth.restoreProfile(profile)
        if(auth.session.value!=null) loadHome()
    }
    fun login(username: String,password: String) = task {
        val profile=state.value.profile ?: return@task
        auth.login(profile,username,password,"${Build.MANUFACTURER} ${Build.MODEL}")
        history.clear(); loadHome()
    }
    private fun rememberRoute() {
        history.add(state.value.copy(busy=false,error=null,showPlayer=false))
    }
    private suspend fun loadHome() = coroutineScope {
        saved["detailId"]=null
        val sections=async { library.sections() }; val deck=async { library.deck() }; val recent=async { library.recent() }; val playlists=async { library.playlists() }
        val recentItems=recent.await()
        val sectionItems=sections.await()
        val likedGenres=recentItems.filter { it.lastViewedAt>0 }.flatMap { it.genres }.map { it.tag }.toSet()
        val recommendations=recentItems.filter { it.type=="movie" && it.viewCount==0 && it.genres.any { g -> g.tag in likedGenres } }
            .sortedWith(compareByDescending<MediaItem> { it.genres.count { g -> g.tag in likedGenres } }.thenByDescending { it.addedAt }.thenBy { it.ratingKey }).take(12)
        val rows=listOf(MediaRow("Continue watching",deck.await()),MediaRow("Recently added movies",recentItems.filter { it.type=="movie" }),MediaRow("Recently added episodes",recentItems.filter { it.type=="episode" }),
            MediaRow("Recently watched",recentItems.filter { it.lastViewedAt>0 }.sortedByDescending { it.lastViewedAt }),MediaRow("From genres you watched",recommendations),MediaRow("Playlists",playlists.await())).filter { it.items.isNotEmpty() }
        mutable.update { it.copy(screen=Screen.HOME,account=auth.session.value?.account,sections=sectionItems,rows=rows,detail=null,selectedSection=null) }
    }
    fun home() = task { history.clear(); loadHome() }
    fun browse(section: LibrarySection) = task {
        rememberRoute(); saved["detailId"]=null
        mutable.update { it.copy(screen=Screen.LIBRARY,selectedSection=section,detail=null,items=emptyList(),children=emptyList()) }
        val cached=library.cachedAuthorized("library:${section.key}")
        mutable.update { it.copy(items=cached) }
        val items=library.library(section.key); val collections=library.collections(section.key)
        mutable.update { it.copy(items=items,rows=if(collections.isNotEmpty()) listOf(MediaRow("Collections",collections)) else emptyList()) }
    }
    private suspend fun loadDetail(id: String) {
        val item=library.metadata(id)
        val children=if(item.playable) emptyList() else library.children(id)
        val episodes=if(item.type=="show") library.leaves(id) else children.filter { it.type=="episode" }
        val next=episodes.filter { it.viewCount==0 }.sortedWith(compareByDescending<MediaItem> { it.viewOffset>0 }.thenBy { it.parentIndex ?: 0 }.thenBy { it.index ?: 0 }).firstOrNull()
        saved["detailId"]=id
        mutable.update { it.copy(screen=Screen.DETAILS,detail=item,children=children,items=emptyList(),nextEpisode=next) }
    }
    fun details(item: MediaItem) = task {
        rememberRoute()
        if(item.type in listOf("playlist","collection")) { val members=library.members(item); mutable.update { it.copy(screen=Screen.DETAILS,detail=item,children=members,nextEpisode=null) } }
        else loadDetail(item.ratingKey)
    }
    fun play(item: MediaItem, resume: Boolean = true, version: Int = 0) = task {
        val detail=library.metadata(item.ratingKey)
        mutable.update { it.copy(showPlayer=true) }
        engine.play(detail,if(resume) detail.viewOffset else 0,version)
    }
    fun watched(item: MediaItem) = task { library.watched(item.ratingKey,item.viewCount==0); loadDetail(item.ratingKey) }
    fun searchScreen() { operation?.cancel(); rememberRoute(); saved["detailId"]=null; mutable.update { it.copy(screen=Screen.SEARCH,items=emptyList(),query="",busy=false,error=null) } }
    fun search(query: String) {
        mutable.update { it.copy(query=query) }; searchJob?.cancel()
        if(query.isBlank()) { mutable.update { it.copy(items=emptyList(),busy=false) }; return }
        searchJob=viewModelScope.launch {
            delay(350)
            mutable.update { it.copy(busy=true,error=null) }
            try { val items=library.search(query.take(200)); if(state.value.screen==Screen.SEARCH && state.value.query==query) mutable.update { it.copy(items=items) } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(error=e.asSloplexError().message) } }
            finally { if(state.value.query==query) mutable.update { it.copy(busy=false) } }
        }
    }
    fun settings() = task { rememberRoute(); mutable.update { it.copy(screen=Screen.SETTINGS,tokens=auth.verified().client.api.tokens().tokens) } }
    fun revoke(id: Int) = task { val s=auth.verified(); s.client.api.revoke(id); if(id==s.tokenId) { mutable.update { it.copy(account=null) }; engine.stop(); auth.logout(); mutable.update { AppState(screen=Screen.LOGIN,profile=it.profile,profiles=it.profiles) } } else mutable.update { it.copy(tokens=s.client.api.tokens().tokens) } }
    fun logout() = task {
        mutable.update { AppState(screen=Screen.LOGIN,profile=it.profile,profiles=it.profiles,busy=true) }
        history.clear(); saved["detailId"]=null
        engine.pause()
        val revoked=auth.logout { detachedSession -> engine.stop(detachedSession) }
        mutable.update { AppState(screen=Screen.LOGIN,profile=it.profile,profiles=it.profiles,error=if(revoked) null else "Signed out locally. The server was unreachable; revoke this device token from another signed-in device.") }
    }
    fun connectionScreen() = task { mutable.update { it.copy(account=null) }; engine.stop(); auth.switchServer(); history.clear(); saved["detailId"]=null; mutable.update { AppState(profiles=it.profiles) }; discover() }
    fun refresh() = when(state.value.screen) { Screen.HOME -> home(); Screen.LIBRARY -> state.value.selectedSection?.let(::browse); Screen.DETAILS -> state.value.detail?.let(::details); Screen.SEARCH -> search(state.value.query); Screen.SETTINGS -> settings(); else -> discover() }
    fun back() {
        searchJob?.cancel()
        if(state.value.showPlayer) { task { engine.stop(); mutable.update { it.copy(showPlayer=false) }; state.value.detail?.let { loadDetail(it.ratingKey) } }; return }
        val previous=history.removeLastOrNull() ?: return
        operation?.cancel()
        saved["detailId"]=previous.detail?.takeUnless { it.type in listOf("playlist","collection") }?.ratingKey
        mutable.update { previous.copy(profiles=it.profiles,account=it.account,profile=it.profile) }
    }
    fun background() { viewModelScope.launch { engine.background() } }
    fun foreground() { viewModelScope.launch { if(state.value.showPlayer) engine.foreground() } }
    fun seek(ms: Long) = task { engine.seek(ms) }
    fun track(audio: Int? = null, subtitle: Int? = null) = task { engine.changeTracks(audio,subtitle) }
    fun quality(quality: Quality) = task { engine.changeQuality(quality) }
    fun retryPlayback() = task { engine.retry() }
    override fun onCleared() { engine.release(); super.onCleared() }
}
