@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.sloplex.core.player

import android.content.Context
import android.media.MediaCodecList
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import app.sloplex.core.data.*
import app.sloplex.core.model.*
import app.sloplex.core.model.MediaItem
import app.sloplex.core.network.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

data class PlayerState(val item: MediaItem? = null, val mode: PlaybackMode? = null, val positionMs: Long = 0,
    val durationMs: Long = 0, val playing: Boolean = false, val buffering: Boolean = false, val error: String? = null,
    val videoCodec: String = "", val audioCodec: String = "", val resolution: String = "", val mediaIndex: Int = 0, val syncError: String? = null)
enum class Quality(val resolution: String?, val bitrate: Int?) { ORIGINAL(null,null), FULL_HD("1920x1080",8000), HD("1280x720",4000), SD("854x480",1500) }

class PlaybackEngine @Inject constructor(@param:ApplicationContext private val context: Context, private val auth: AuthenticationRepository) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val progressMutex = Mutex()
    private val mutable = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = mutable.asStateFlow()
    var player: ExoPlayer? = null; private set
    private var mediaSession: MediaSession? = null
    private var connected: Session? = null
    private var label: String? = null
    private var timeline = PlaybackTimeline()
    private var current: MediaItem? = null
    private var version = 0
    private var audio: Int? = null
    private var subtitle: Int? = null
    private var quality = Quality.ORIGINAL
    private var lastReport = 0L
    private var ticker: Job? = null
    private var generation = 0L
    private var reloading = false
    private var backgrounded = false
    private var resumePlaying = false

    suspend fun play(item: MediaItem, resumeMs: Long = item.viewOffset, mediaIndex: Int = 0) = withContext(Dispatchers.Main.immediate) {
        backgrounded=false; version = mediaIndex; audio = null; subtitle = null; quality = Quality.ORIGINAL
        start(item,resumeMs)
    }
    private fun decoders(media: MediaVersion): DeviceCapabilities {
        val map = mapOf("h264" to "video/avc", "hevc" to "video/hevc", "vp9" to "video/x-vnd.on2.vp9", "av1" to "video/av01")
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        val videos = map.filter { (_, mime) -> codecs.any { c ->
            runCatching { val v = c.getCapabilitiesForType(mime).videoCapabilities; v != null && v.isSizeSupported(media.width.coerceAtLeast(16), media.height.coerceAtLeast(16)) }.getOrDefault(false)
        } }.keys
        val audios = mapOf("aac" to "audio/mp4a-latm", "mp3" to "audio/mpeg").filter { (_,mime) -> codecs.any { c -> c.supportedTypes.any { it.equals(mime,true) } } }.keys
        return DeviceCapabilities(videos,audios,if(media.videoCodec in videos) media.width.coerceAtLeast(1920) else 1920,if(media.videoCodec in videos) media.height.coerceAtLeast(1080) else 1080)
    }
    private suspend fun start(item: MediaItem, position: Long, forceHls: Boolean = false, shouldPlay: Boolean = true) = withContext(Dispatchers.Main.immediate) {
        val requestGeneration = ++generation
        resumePlaying=shouldPlay
        val boundedPosition = position.coerceAtLeast(0).let { if(item.durationMs > 0) it.coerceAtMost(item.durationMs) else it }
        reloading = true
        try {
            cleanup(current != null)
            if(generation != requestGeneration) return@withContext
            current=item; connected=null
            mutable.value=PlayerState(item=item,positionMs=boundedPosition,durationMs=item.durationMs,buffering=true,mediaIndex=version)
            val s = auth.verified()
            if(generation != requestGeneration) return@withContext
            val media = item.versions.getOrNull(version) ?: throw SloplexException(ErrorKind.INVALID_SOURCE,"No media version is available.")
            if(media.parts.size != 1) throw SloplexException(ErrorKind.INVALID_SOURCE,"Multi-part playback is not available in this alpha.")
            val part = media.parts.single()
            val selectedAudio = part.streams.firstOrNull { it.id == audio } ?: part.streams.firstOrNull { it.streamType == 2 && it.selected }
            val selectedSub = if(subtitle == 0) null else part.streams.firstOrNull { it.id == subtitle } ?: part.streams.firstOrNull { it.streamType == 3 && it.selected }
            val caps = decoders(media)
            val parameters = mutableMapOf("path" to "/library/metadata/${item.ratingKey}", "mediaIndex" to version.toString(), "partIndex" to "0", "protocol" to "hls",
                "containers" to "mp4,mkv,matroska,webm,mpegts,ts,m4v", "videoCodecs" to caps.videoCodecs.joinToString(",").ifBlank { "none" },
                "audioCodecs" to caps.audioCodecs.joinToString(",").ifBlank { "none" }, "maxAudioChannels" to "2", "hdr" to "0", "directStream" to "0",
                "videoResolution" to (quality.resolution ?: "${caps.width}x${caps.height}"), "directPlay" to if(forceHls || quality != Quality.ORIGINAL || media.videoCodec.isBlank() || media.width<=0 || media.height<=0) "0" else "1")
            audio?.let { parameters["audioStreamID"] = it.toString() }; subtitle?.let { parameters["subtitleStreamID"] = it.toString() }
            quality.bitrate?.let { parameters["maxVideoBitrate"] = it.toString() }
            val decision = s.client.api.decision(parameters).container
            if(generation != requestGeneration) return@withContext
            val mode = when(decision.generalDecisionCode) { 1000 -> PlaybackMode.DIRECT; 1001 -> PlaybackMode.HLS; else -> throw SloplexException(ErrorKind.TRANSCODE_UNAVAILABLE,decision.transcodeDecisionText.ifBlank { "This device cannot play the media and conversion is unavailable." }) }
            connected=s; current=item
            if(backgrounded) { mutable.value=mutable.value.copy(mode=mode,buffering=false); return@withContext }
            label = if(mode == PlaybackMode.HLS) UUID.randomUUID().toString() else null
            timeline = PlaybackTimeline(if(mode == PlaybackMode.HLS) boundedPosition else 0,item.durationMs)
            val uri = if(mode == PlaybackMode.DIRECT) s.client.url(part.key) else {
                parameters["session"] = label!!; parameters["offset"] = timeline.offsetSeconds
                parameters["abr"] = "1"
                s.client.url("/video/:/transcode/universal/start.m3u8").newBuilder().apply { parameters.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
            }
            val source = androidx.media3.common.MediaItem.Builder().setUri(uri.toString()).setMediaId(item.ratingKey)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
            if(mode == PlaybackMode.HLS) source.setMimeType(MimeTypes.APPLICATION_M3U8)
            if(mode == PlaybackMode.DIRECT && selectedSub != null && selectedSub.key.isNotBlank()) {
                val subUri = s.client.url(selectedSub.key).newBuilder().addQueryParameter("format","vtt").build()
                source.setSubtitleConfigurations(listOf(androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUri.toString()))
                    .setId(selectedSub.id.toString()).setMimeType(MimeTypes.TEXT_VTT).setLabel(selectedSub.displayTitle).setLanguage(selectedSub.languageCode)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            }
            val dataSource = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(s.client.http))
            player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
                setHandleAudioBecomingNoisy(true)
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,selectedSub == null).build()
                addListener(object: Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if(!reloading) scope.launch { safeReport(if(isPlaying) "playing" else "paused",expectedGeneration=requestGeneration) } }
                    override fun onPlaybackStateChanged(playbackState: Int) { if(playbackState == Player.STATE_ENDED) scope.launch { safeReport("stopped",item.durationMs,requestGeneration) } }
                    override fun onTracksChanged(tracks: Tracks) {
                        if(mode == PlaybackMode.DIRECT && selectedAudio != null) {
                            val ordinal = part.streams.filter { it.streamType == 2 }.indexOfFirst { it.id == selectedAudio.id }
                            val audioTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group -> (0 until group.length).map { group to it } }
                            audioTracks.getOrNull(ordinal)?.let { (g,i) -> if(g.isTrackSupported(i)) {
                                val next = trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,i)).build()
                                if(next != trackSelectionParameters) trackSelectionParameters=next
                            } }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if(generation != requestGeneration) return
                        val failedLabel=label
                        mutable.value=mutable.value.copy(error=playbackMessage(error),playing=false,buffering=false)
                        scope.launch { runCatching { report("stopped",expectedGeneration=requestGeneration) }; failedLabel?.let { runCatching { s.client.api.stop(it) } } }
                    }
                })
                setMediaItem(source.build()); prepare()
                seekTo(if(mode == PlaybackMode.DIRECT) boundedPosition else 0L)
                playWhenReady=shouldPlay
            }
            mediaSession = MediaSession.Builder(context,player!!).build()
            val output = decision.metadata.firstOrNull()?.versions?.firstOrNull() ?: media
            mutable.value=PlayerState(item,mode,boundedPosition,item.durationMs,buffering=true,videoCodec=output.videoCodec,audioCodec=output.audioCodec,resolution="${output.width}×${output.height}",mediaIndex=version)
            safeReport("paused",boundedPosition)
            ticker = scope.launch {
                var seconds=0
                while(isActive) {
                    delay(1000)
                    val p = player ?: break
                    mutable.value=mutable.value.copy(positionMs=timeline.sourcePosition(p.currentPosition),playing=p.isPlaying,buffering=p.playbackState==Player.STATE_BUFFERING)
                    seconds++
                    if(seconds%10 == 0) {
                        try { auth.verified(); if(p.isPlaying || state.value.syncError!=null) safeReport(if(p.isPlaying) "playing" else "paused") } catch(e: Exception) {
                            if(e is CancellationException) throw e
                            mutable.value=mutable.value.copy(error=e.asSloplexError().message)
                            if(e.asSloplexError().kind == ErrorKind.AUTHENTICATION) { p.pause(); label?.let { runCatching { s.client.api.stop(it) } }; break }
                        }
                    }
                    if(seconds%30 == 0) label?.let { runCatching { s.client.api.ping(it) } }
                }
            }
        } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.value=mutable.value.copy(item=item,error=e.asSloplexError().message,buffering=false) }
        finally { if(generation==requestGeneration) reloading=false }
    }
    suspend fun seek(sourceMs: Long) = withContext(Dispatchers.Main.immediate) {
        val item=current ?: return@withContext
        val target = sourceMs.coerceIn(0,item.durationMs.coerceAtLeast(0))
        if(state.value.mode == PlaybackMode.HLS) start(item,target,shouldPlay=player?.playWhenReady ?: state.value.playing) else { player?.seekTo(target); safeReport("paused",target) }
    }
    suspend fun changeTracks(audioId: Int? = null, subtitleId: Int? = null) = withContext(Dispatchers.Main.immediate) {
        val item=current ?: return@withContext; val part=item.versions[version].parts.single()
        val position = player?.let { timeline.sourcePosition(it.currentPosition) } ?: state.value.positionMs
        auth.verified().client.api.selectStreams(part.id,audioId,subtitleId)
        if(audioId != null) audio=audioId
        if(subtitleId != null) subtitle=subtitleId
        start(item,position,shouldPlay=player?.playWhenReady ?: state.value.playing)
    }
    suspend fun changeQuality(value: Quality) = withContext(Dispatchers.Main.immediate) { quality=value; current?.let { start(it,player?.let { p -> timeline.sourcePosition(p.currentPosition) } ?: state.value.positionMs,shouldPlay=player?.playWhenReady ?: state.value.playing) } }
    suspend fun retry() = withContext(Dispatchers.Main.immediate) { current?.let { start(it,state.value.positionMs,forceHls=state.value.mode==PlaybackMode.DIRECT) } }
    fun toggle() { scope.launch { player?.let { if(it.isPlaying) it.pause() else it.play() } } }
    fun pause() { scope.launch { player?.pause() } }
    suspend fun background() = withContext(Dispatchers.Main.immediate) {
        backgrounded=true
        val p=player ?: return@withContext
        resumePlaying=p.playWhenReady
        val position=timeline.sourcePosition(p.currentPosition)
        val backgroundGeneration=++generation; reloading=true
        mutable.value=mutable.value.copy(positionMs=position,playing=false,buffering=false)
        cleanup(true)
        if(generation==backgroundGeneration) reloading=false
    }
    suspend fun foreground() = withContext(Dispatchers.Main.immediate) {
        if(!backgrounded) return@withContext
        backgrounded=false
        current?.let { start(it,state.value.positionMs,shouldPlay=resumePlaying) }
    }
    private suspend fun safeReport(state: String, position: Long? = null, expectedGeneration: Long? = null, detachedSession: Session? = null) {
        try { if(report(state,position,expectedGeneration,detachedSession) && (expectedGeneration==null || expectedGeneration==generation)) mutable.value=mutable.value.copy(syncError=null) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { if(expectedGeneration==null || expectedGeneration==generation) mutable.value=mutable.value.copy(syncError="Watch progress could not sync. It will retry when the server is reachable.") }
    }
    private suspend fun report(state: String, position: Long? = null, expectedGeneration: Long? = null, detachedSession: Session? = null) = progressMutex.withLock {
        if(expectedGeneration!=null && expectedGeneration!=generation) return@withLock false
        val s=connected ?: return@withLock false; val item=current ?: return@withLock false
        if((if(detachedSession === s) auth.verifiedForLogout(s) else auth.verified()) !== s) return@withLock false
        if(expectedGeneration!=null && expectedGeneration!=generation) return@withLock false
        val now=android.os.SystemClock.elapsedRealtime()
        if(state=="playing" && now-lastReport < 9000 && mutable.value.syncError==null) return@withLock false
        val pos=position ?: player?.let { timeline.sourcePosition(it.currentPosition) } ?: mutable.value.positionMs
        s.client.api.timeline(item.ratingKey,pos,item.durationMs,state); lastReport=now; true
    }
    private suspend fun cleanup(reportStop: Boolean, detachedSession: Session? = null) {
        val cleanupGeneration=generation
        ticker?.cancel(); ticker=null
        val position=player?.let { timeline.sourcePosition(it.currentPosition) } ?: state.value.positionMs
        val s=connected; val oldLabel=label
        mediaSession?.release(); mediaSession=null; player?.release(); player=null; label=null
        if(reportStop) withTimeoutOrNull(5000) { safeReport("stopped",position,cleanupGeneration,detachedSession) }
        if(s != null && oldLabel != null) withTimeoutOrNull(5000) { runCatching { s.client.api.stop(oldLabel) } }
    }
    suspend fun stop(detachedSession: Session? = null) = withContext(Dispatchers.Main.immediate) { val stopGeneration=++generation; reloading=true; cleanup(true,detachedSession); if(generation!=stopGeneration) return@withContext; current=null; connected=null; backgrounded=false; mutable.value=PlayerState(); reloading=false }
    fun release() { scope.launch { stop(); scope.cancel() } }
    private fun playbackMessage(e: PlaybackException): String {
        val response=generateSequence<Throwable>(e) { it.cause }.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        return when(response?.responseCode) { 401 -> "Your session expired. Sign in again."; 404 -> "The media or conversion session is no longer available."; 503 -> if(response.headerFields.keys.any { it.equals("Retry-After",true) }) "All conversion slots are busy. Try again shortly." else "Conversion is unavailable on the server."; else -> "Playback was interrupted. Check your connection or retry with conversion." }
    }
}
