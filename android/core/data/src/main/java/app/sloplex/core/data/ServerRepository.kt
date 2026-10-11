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

@Singleton class ServerRepository @Inject constructor(private val profiles: ProfileStore, @param:ApplicationContext private val context: Context) {
    val saved = profiles.profiles
    suspend fun connect(input: String, allowLanHttp: Boolean, expectedMachineId: String? = null): ServerProfile = withContext(Dispatchers.IO) {
        val url = SafeUrl.parse(input)
        val draft = ServerProfile("", url.toString(), url.host, "", allowLanHttp)
        val identity = createClient(draft).api.identity().container
        if (identity.machineIdentifier.isBlank() || identity.version.isBlank()) throw SloplexException(ErrorKind.INCOMPATIBLE, "This address did not return a Sloplex server identity.")
        if(expectedMachineId != null && identity.machineIdentifier != expectedMachineId) throw SloplexException(ErrorKind.INVALID_SOURCE, "Server identity changed. Check the address before signing in.")
        draft.copy(machineId=identity.machineIdentifier, version=identity.version).also { profiles.save(it) }
    }
    suspend fun discover(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("sloplex-discovery")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true; socket.soTimeout = 250
                val targets = mutableSetOf("239.0.0.250", "255.255.255.255")
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { it.isUp && !it.isLoopback }.forEach { n -> n.interfaceAddresses.mapNotNull { it.broadcast }.forEach { targets.add(it.hostAddress!!) } }
                targets.forEach { address -> runCatching { socket.send(DatagramPacket(Gdm.search, Gdm.search.size, InetAddress.getByName(address),32414)) } }
                val found = linkedMapOf<String, DiscoveredServer>()
                val until = System.nanoTime()+3_000_000_000L
                while(System.nanoTime() < until) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(ByteArray(2049),2049)
                    try { socket.receive(packet); Gdm.parse(packet.data.copyOf(packet.length),packet.address)?.let { found[it.machineId]=it } } catch (_: SocketTimeoutException) { }
                }
                found.values.toList()
            }
        } finally { if(lock?.isHeld == true) lock.release() }
    }
}
