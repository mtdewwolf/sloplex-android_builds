package app.sloplex.ui.tv
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sloplex.feature.session.ConnectionViewModel
import androidx.tv.material3.*
@Composable fun SloplexTv(vm: ConnectionViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf("") }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xff55e6c1),background=Color(0xff080e17))) {
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item { Text("SLOPLEX",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.displaySmall) }
            item { androidx.compose.material3.MaterialTheme(colorScheme=androidx.compose.material3.darkColorScheme()) {
                androidx.compose.material3.OutlinedTextField(address,{address=it},label={androidx.compose.material3.Text("Server address")},singleLine=true,modifier=Modifier.fillMaxWidth())
            } }
            item { Button(onClick={allowHttp=!allowHttp}) { Text(if(allowHttp) "Private LAN HTTP allowed" else "Enable private LAN HTTP") } }
            item { Text("HTTP sends credentials unencrypted on your private LAN. Use HTTPS for remote access.",color=Color.White) }
            item { Button(onClick={vm.connect(address,allowHttp)},enabled=address.isNotBlank()&&!state.busy) { Text(if(state.busy) "Connecting…" else "Connect") } }
            state.error?.let { message -> item { Text(message,color=Color(0xffffb4ab)) } }
            state.profile?.let { profile -> item { Text("Server identity verified",color=Color.White,style=MaterialTheme.typography.titleLarge);Text("${profile.url}\n${profile.machineId}\nVersion ${profile.version}",color=Color.White) } }
            item { Text("Saved servers",color=Color.White) }
            items(state.saved,key={it.machineId}) { profile -> Button(onClick={vm.connect(profile.url,profile.allowLanHttp,profile.machineId)}) { Text(profile.name) } }
            item { Button(onClick=vm::discover) { Text("Search local network") } }
            items(state.discovered,key={it.machineId}) { found -> Button(onClick={address=found.url}) { Text("${found.name} · ${found.url}") } }
        }
    }
}
