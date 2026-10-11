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

private val Context.profileData by preferencesDataStore("servers")
@Singleton class ProfileStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val profilesKey = stringPreferencesKey("profiles")
    private val activeKey = stringPreferencesKey("active")
    val profiles: Flow<List<ServerProfile>> = context.profileData.data.map { p -> p[profilesKey]?.let { wireJson.decodeFromString<List<ServerProfile>>(it) } ?: emptyList() }
    suspend fun save(profile: ServerProfile) { context.profileData.edit { p ->
        val all = p[profilesKey]?.let { wireJson.decodeFromString<List<ServerProfile>>(it) }.orEmpty()
        p[profilesKey] = wireJson.encodeToString((all.filterNot { it.machineId == profile.machineId } + profile).takeLast(20))
        p[activeKey] = profile.machineId
    } }
    suspend fun active(): ServerProfile? { val p = context.profileData.data.first(); return profiles.first().firstOrNull { it.machineId == p[activeKey] } }
}
