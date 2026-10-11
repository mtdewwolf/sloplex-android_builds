package app.sloplex.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class Envelope(@SerialName("MediaContainer") val container: MediaContainer)
@Serializable data class MediaContainer(
    val size: Int = 0, val totalSize: Int? = null, val offset: Int? = null,
    val machineIdentifier: String = "", val version: String = "", val friendlyName: String = "",
    @SerialName("Directory") val directories: List<LibrarySection> = emptyList(),
    @SerialName("Metadata") val metadata: List<MediaItem> = emptyList(),
    val generalDecisionCode: Int = 0, val generalDecisionText: String = "",
    val directPlayDecisionCode: Int = 0, val transcodeDecisionCode: Int = 0,
    val transcodeDecisionText: String = "",
)
@Serializable data class LibrarySection(val key: String, val title: String, val type: String = "", val uuid: String = "")
@Serializable data class Tag(val tag: String, val role: String = "")
@Serializable data class MediaItem(
    val ratingKey: String, val key: String = "", val type: String, val title: String,
    val year: Int = 0, val duration: Long = 0, val summary: String = "", val contentRating: String = "",
    val rating: Double = 0.0, val index: Int? = null, val parentIndex: Int? = null,
    val thumb: String = "", val art: String = "", val grandparentThumb: String = "", val grandparentArt: String = "",
    val grandparentTitle: String = "", val parentTitle: String = "", val parentRatingKey: String = "",
    val librarySectionID: Int = 0, val viewOffset: Long = 0, val viewCount: Int = 0,
    val lastViewedAt: Long = 0, val addedAt: Long = 0,
    @SerialName("Media") val versions: List<MediaVersion> = emptyList(),
    @SerialName("Genre") val genres: List<Tag> = emptyList(),
    @SerialName("Role") val cast: List<Tag> = emptyList(),
) {
    val playable get() = type == "movie" || type == "episode"
    val poster get() = thumb.ifBlank { grandparentThumb }
    val backdrop get() = art.ifBlank { grandparentArt }
    val durationMs get() = duration.takeIf { it > 0 } ?: versions.firstOrNull()?.duration ?: 0L
}
@Serializable data class MediaVersion(
    val id: Int, val duration: Long = 0, val width: Int = 0, val height: Int = 0,
    val bitrate: Int = 0, val videoCodec: String = "", val audioCodec: String = "",
    val audioChannels: Int = 0, val container: String = "", val videoResolution: String = "",
    @SerialName("Part") val parts: List<MediaPart> = emptyList(),
)
@Serializable data class MediaPart(
    val id: Int, val key: String = "", val duration: Long = 0, val container: String = "",
    val decision: String = "", @SerialName("Stream") val streams: List<MediaStream> = emptyList(),
)
@Serializable data class MediaStream(
    val id: Int, val streamType: Int, val codec: String, val index: Int? = null,
    val displayTitle: String = "", val extendedDisplayTitle: String = "", val language: String = "",
    val languageCode: String = "", val channels: Int = 0, val width: Int = 0, val height: Int = 0,
    val selected: Boolean = false, val default: Boolean = false, val forced: Boolean = false,
    val hearingImpaired: Boolean = false, val key: String = "",
)
@Serializable data class Account(val id: Int, val name: String, val admin: Boolean = false, val libraries: List<Int>? = null)
@Serializable data class DeviceToken(val id: Int, val device: String = "", val createdAt: Long = 0, val token: String = "") {
    override fun toString() = "DeviceToken(id=$id, device=$device)"
}
@Serializable data class LoginRequest(val username: String, val password: String, val device: String) {
    override fun toString() = "LoginRequest(username=[redacted], device=$device)"
}
@Serializable data class LoginResponse(val account: Account, val token: DeviceToken)
@Serializable data class TokenList(val tokens: List<DeviceToken>)
@Serializable data class ServerProfile(val machineId: String, val url: String, val name: String, val version: String, val allowLanHttp: Boolean = false)
data class CacheScope(val machineId: String, val accountId: Int)
enum class ErrorKind { UNAVAILABLE, AUTHENTICATION, PERMISSION, NOT_FOUND, UNSUPPORTED_CODEC, TRANSCODE_UNAVAILABLE, TRANSCODE_CAPACITY, INVALID_SOURCE, TIMEOUT, TLS, INTERRUPTION, INCOMPATIBLE, INVALID_URL, RATE_LIMIT }
class SloplexException(val kind: ErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)

/** Source time and Media3 output time are distinct for offset HLS sessions. */
data class PlaybackTimeline(val sourceOffsetMs: Long = 0, val durationMs: Long = 0) {
    init { require(sourceOffsetMs >= 0 && durationMs >= 0) }
    fun sourcePosition(outputMs: Long): Long = (sourceOffsetMs + outputMs.coerceAtLeast(0)).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }
    fun outputPosition(sourceMs: Long): Long = (sourceMs - sourceOffsetMs).coerceAtLeast(0)
    val offsetSeconds: String get() = java.math.BigDecimal.valueOf(sourceOffsetMs, 3).toPlainString()
}
enum class PlaybackMode { DIRECT, HLS }
data class DeviceCapabilities(val videoCodecs: Set<String>, val audioCodecs: Set<String>, val width: Int = 1920, val height: Int = 1080, val maxAudioChannels: Int = 2)
