package app.sloplex.core.network

import app.sloplex.core.model.*
import retrofit2.http.*

interface SloplexApi {
    @GET("identity") suspend fun identity(): Envelope
    @POST("sloplex/auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("sloplex/auth/logout") suspend fun logout()
    @GET("sloplex/account") suspend fun account(): Account
    @GET("sloplex/tokens") suspend fun tokens(): TokenList
    @DELETE("sloplex/tokens/{id}") suspend fun revoke(@Path("id") id: Int)
    @GET("library/sections") suspend fun sections(): Envelope
    @GET("library/sections/{id}/all") suspend fun library(@Path("id") id: String, @Query("X-Plex-Container-Start") start: Int, @Query("X-Plex-Container-Size") size: Int = 100, @Query("sort") sort: String = "titleSort"): Envelope
    @GET("library/recentlyAdded") suspend fun recent(@Query("X-Plex-Container-Start") start: Int = 0, @Query("X-Plex-Container-Size") size: Int = 50): Envelope
    @GET("library/onDeck") suspend fun onDeck(@Query("X-Plex-Container-Start") start: Int = 0, @Query("X-Plex-Container-Size") size: Int = 50): Envelope
    @GET("library/metadata/{id}") suspend fun metadata(@Path("id") id: String): Envelope
    @GET("library/metadata/{id}/children") suspend fun children(@Path("id") id: String, @Query("X-Plex-Container-Start") start: Int = 0, @Query("X-Plex-Container-Size") size: Int = 100): Envelope
    @GET("library/metadata/{id}/allLeaves") suspend fun leaves(@Path("id") id: String, @Query("X-Plex-Container-Start") start: Int = 0, @Query("X-Plex-Container-Size") size: Int = 100): Envelope
    @GET("search") suspend fun search(@Query("query") query: String, @Query("X-Plex-Container-Start") start: Int = 0, @Query("X-Plex-Container-Size") size: Int = 100): Envelope
    @GET("library/sections/{id}/collections") suspend fun collections(@Path("id") id: String): Envelope
    @GET("library/collections/{id}/children") suspend fun collectionItems(@Path("id") id: String): Envelope
    @GET("playlists") suspend fun playlists(): Envelope
    @GET("playlists/{id}/items") suspend fun playlistItems(@Path("id") id: String): Envelope
    @PUT("library/parts/{id}") suspend fun selectStreams(@Path("id") id: Int, @Query("audioStreamID") audio: Int? = null, @Query("subtitleStreamID") subtitle: Int? = null)
    @PUT(":/timeline") suspend fun timeline(@Query("ratingKey") id: String, @Query("time") time: Long, @Query("duration") duration: Long, @Query("state") state: String)
    @PUT(":/scrobble") suspend fun watched(@Query("ratingKey") id: String)
    @PUT(":/unscrobble") suspend fun unwatched(@Query("ratingKey") id: String)
    @GET("video/:/transcode/universal/decision") suspend fun decision(@QueryMap parameters: Map<String, String>): Envelope
    @PUT("video/:/transcode/universal/stop") suspend fun stop(@Query("session") session: String)
    @PUT("video/:/transcode/universal/ping") suspend fun ping(@Query("session") session: String)
}
