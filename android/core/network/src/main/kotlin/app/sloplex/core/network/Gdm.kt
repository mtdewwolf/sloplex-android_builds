package app.sloplex.core.network

import java.net.InetAddress

data class DiscoveredServer(val machineId: String, val name: String, val version: String, val url: String)
object Gdm {
    val search = "M-SEARCH * HTTP/1.0\r\n\r\n".toByteArray(Charsets.US_ASCII)
    fun parse(payload: ByteArray, source: InetAddress): DiscoveredServer? {
        if (!SafeUrl.isPrivate(source) || payload.size > 2048) return null
        val lines = payload.toString(Charsets.UTF_8).split("\r\n")
        if (lines.firstOrNull() != "HTTP/1.0 200 OK") return null
        val headers = mutableMapOf<String, String>()
        for(line in lines.drop(1)) {
            val parts = line.split(':', limit = 2)
            if(parts.size == 2) {
                val key = parts[0].trim().lowercase()
                if(headers.containsKey(key)) return null
                headers[key] = parts[1].trim()
            }
        }
        if(headers["content-type"] != "plex/media-server") return null
        val id = headers["resource-identifier"]?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        val port = headers["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val host = source.hostAddress ?: return null
        return DiscoveredServer(id, headers["name"].orEmpty().ifBlank { "Sloplex" }, headers["version"].orEmpty(), "http://${if(':' in host) "[$host]" else host}:$port/")
    }
}

/** Never advance by the requested size: an implementation may return a shorter page. */
suspend fun collectPages(fetch: suspend (Int) -> app.sloplex.core.model.Envelope): List<app.sloplex.core.model.MediaItem> {
    val output = linkedMapOf<String, app.sloplex.core.model.MediaItem>()
    var offset = 0
    repeat(10000) {
        val page = fetch(offset).container
        if (page.metadata.isEmpty()) return output.values.toList()
        if (page.offset != null && page.offset != offset) throw app.sloplex.core.model.SloplexException(app.sloplex.core.model.ErrorKind.INCOMPATIBLE, "The server returned an invalid page offset.")
        page.metadata.forEach { output[it.ratingKey] = it }
        offset += page.metadata.size
        if(page.totalSize?.let { offset >= it } != false) return output.values.toList()
    }
    throw app.sloplex.core.model.SloplexException(app.sloplex.core.model.ErrorKind.INCOMPATIBLE, "The server did not finish pagination.")
}
