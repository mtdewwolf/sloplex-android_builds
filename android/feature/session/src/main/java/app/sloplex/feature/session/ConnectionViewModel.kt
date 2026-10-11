package app.sloplex.feature.session
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sloplex.core.data.ServerRepository
import app.sloplex.core.model.ServerProfile
import app.sloplex.core.network.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ConnectionState(val busy: Boolean=false,val error: String?=null,val profile: ServerProfile?=null,val saved: List<ServerProfile> = emptyList(),val discovered: List<DiscoveredServer> = emptyList())
@HiltViewModel class ConnectionViewModel @Inject constructor(private val servers: ServerRepository): ViewModel() {
    private val mutable=MutableStateFlow(ConnectionState())
    val state=mutable.asStateFlow()
    init { viewModelScope.launch { servers.saved.collect { profiles -> mutable.update { it.copy(saved=profiles) } } }; discover() }
    fun discover() { viewModelScope.launch { try { val found=servers.discover();mutable.update { it.copy(discovered=found) } } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(error=e.asSloplexError().message) } } } }
    fun connect(address: String,allowHttp: Boolean,expected: String?=null) {
        viewModelScope.launch { mutable.update { it.copy(busy=true,error=null) }
            try { val profile=servers.connect(address,allowHttp,expected);mutable.update { it.copy(profile=profile) } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(error=e.asSloplexError().message) } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
}
