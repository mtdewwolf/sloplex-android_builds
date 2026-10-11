package app.sloplex.core.network

import app.sloplex.core.model.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

object SafeUrl {
    fun isPrivate(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isMulticastAddress) return false
        if (address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress) return true
        val b = address.address
        return b.size == 16 && (b[0].toInt() and 0xfe) == 0xfc
    }
    fun parse(input: String): HttpUrl {
        val raw = input.trim()
        val url = if ("://" in raw) raw.toHttpUrlOrNull() else {
            val probe = "http://$raw".toHttpUrlOrNull() ?: throw SloplexException(ErrorKind.INVALID_URL, "Enter a valid server address.")
            val literal = probe.host.matches(Regex("[0-9.]+")) || ':' in probe.host
            val local = literal && runCatching { isPrivate(InetAddress.getByName(probe.host)) }.getOrDefault(false)
            val explicitPort = probe.port != 80 || Regex(":80(/|$)").containsMatchIn(raw)
            // Only the LAN HTTP listener defaults to 32400; HTTPS hostnames (Tailscale Serve, reverse proxies) use 443.
            probe.newBuilder().scheme(if (local) "http" else "https").port(if (explicitPort) probe.port else if (local) 32400 else 443).build()
        } ?: throw SloplexException(ErrorKind.INVALID_URL, "Use an HTTP or HTTPS server URL.")
        if (url.username.isNotEmpty() || url.password.isNotEmpty())
            throw SloplexException(ErrorKind.INVALID_URL, "Use the server origin without credentials, a path, or query parameters.")
        // Accept the web app address copied from a browser (e.g. https://host/web/index.html#!/) and keep only its origin.
        val webApp = url.encodedPath == "/web" || url.encodedPath.startsWith("/web/")
        if (!webApp && (url.query != null || url.fragment != null || url.encodedPath != "/"))
            throw SloplexException(ErrorKind.INVALID_URL, "Use the server origin without credentials, a path, or query parameters.")
        return if (webApp) url.newBuilder().encodedPath("/").query(null).fragment(null).build() else url
    }
    fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
    fun resolve(base: HttpUrl, path: String): HttpUrl {
        val url = base.resolve(path) ?: throw SloplexException(ErrorKind.INVALID_SOURCE, "Invalid media URL.")
        if (!sameOrigin(base, url) || url.username.isNotEmpty() || url.password.isNotEmpty() || url.queryParameter("X-Plex-Token") != null)
            throw SloplexException(ErrorKind.INVALID_SOURCE, "The server returned an unsafe media URL.")
        return url
    }
}

class ServerClient(val base: HttpUrl, val http: OkHttpClient, val api: SloplexApi) {
    fun url(path: String) = SafeUrl.resolve(base, path)
}

fun createClient(profile: ServerProfile, token: String? = null, deviceId: String = "sloplex-android"): ServerClient {
    val base = SafeUrl.parse(profile.url)
    if (!base.isHttps && !profile.allowLanHttp) throw SloplexException(ErrorKind.INVALID_URL, "Confirm local HTTP access or use HTTPS.")
    if (!base.isHttps && (base.host.matches(Regex("[0-9.]+")) || ':' in base.host) && !SafeUrl.isPrivate(InetAddress.getByName(base.host))) throw SloplexException(ErrorKind.INVALID_URL,"HTTP credentials are only allowed on a private LAN.")
    val dns = object : Dns { override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (!base.isHttps && addresses.any { !SafeUrl.isPrivate(it) }) throw IOException("HTTP is permitted only on a private local network")
        return addresses
    } }
    val client = OkHttpClient.Builder().dns(dns).followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            if (!SafeUrl.sameOrigin(base, req.url)) throw IOException("Cross-origin request refused")
            val builder = req.newBuilder().header("Accept", "application/json").header("X-Plex-Product", "Sloplex Android")
                .header("X-Plex-Client-Identifier", deviceId).header("X-Plex-Platform", "Android")
            if (!token.isNullOrBlank()) builder.header("X-Plex-Token", token)
            chain.proceed(builder.build())
        }.build()
    val api = Retrofit.Builder().baseUrl(base).client(client).addConverterFactory(wireJson.asConverterFactory("application/json".toMediaType())).build().create(SloplexApi::class.java)
    return ServerClient(base, client, api)
}

fun Throwable.asSloplexError(): SloplexException = when (this) {
    is SloplexException -> this
    is SSLException -> SloplexException(ErrorKind.TLS, "The server certificate could not be verified.", this)
    is SocketTimeoutException -> SloplexException(ErrorKind.TIMEOUT, "The server took too long to respond.", this)
    is HttpException -> {
        val kind = when (code()) { 401 -> ErrorKind.AUTHENTICATION; 403 -> ErrorKind.PERMISSION; 404 -> ErrorKind.NOT_FOUND; 429 -> ErrorKind.RATE_LIMIT; 503 -> if(response()?.headers()?.get("Retry-After") != null) ErrorKind.TRANSCODE_CAPACITY else ErrorKind.TRANSCODE_UNAVAILABLE; else -> ErrorKind.UNAVAILABLE }
        val message = when(kind) { ErrorKind.AUTHENTICATION -> "Sign in again. Your session is no longer valid."; ErrorKind.PERMISSION -> "Your account does not have access."; ErrorKind.NOT_FOUND -> "This item was removed or is unavailable to your account."; ErrorKind.RATE_LIMIT -> "Too many login attempts. Try again later."; ErrorKind.TRANSCODE_CAPACITY -> "All conversion slots are busy. Try again shortly."; ErrorKind.TRANSCODE_UNAVAILABLE -> "Conversion is unavailable on this server."; else -> "The server could not complete the request." }
        SloplexException(kind, message, this)
    }
    is kotlinx.serialization.SerializationException -> SloplexException(ErrorKind.INCOMPATIBLE, "The server returned an incompatible response.", this)
    else -> SloplexException(ErrorKind.UNAVAILABLE, "Cannot reach the server. Check the address and network.", this)
}
