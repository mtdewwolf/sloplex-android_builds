@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.sloplex.ui.tv

import android.app.Activity
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import app.sloplex.core.data.Session
import app.sloplex.core.model.*
import app.sloplex.core.player.*
import app.sloplex.feature.session.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable fun SloplexTv(vm: SessionViewModel) {
    val s by vm.state.collectAsStateWithLifecycle(); val p by vm.playback.collectAsStateWithLifecycle(); val session by vm.session.collectAsStateWithLifecycle()
    val context=LocalContext.current; val lifecycle=LocalLifecycleOwner.current.lifecycle
    val loader=remember(session) { ImageLoader.Builder(context).apply { session?.let { okHttpClient(it.client.http) }; diskCache(null) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    DisposableEffect(lifecycle) { val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations!=true) vm.background(); if(event==Lifecycle.Event.ON_START) vm.foreground() }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    BackHandler(s.showPlayer || s.screen in listOf(Screen.DETAILS,Screen.LIBRARY,Screen.SEARCH,Screen.SETTINGS)) { vm.back() }
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xff55e6c1),background=Color(0xff080e17),surface=Color(0xff142338))) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if(s.showPlayer) TvPlayer(vm,p)
            else Row(Modifier.fillMaxSize().padding(36.dp)) {
                if(s.account!=null) Column(Modifier.width(170.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("SLOPLEX",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick=vm::home) { Text("Home") }
                    s.sections.forEach { section -> Button(onClick={vm.browse(section)}) { Text(section.title) } }
                    Button(onClick=vm::searchScreen) { Text("Search") }
                    Button(onClick=vm::settings) { Text("Settings") }
                }
                Column(Modifier.weight(1f).padding(start=if(s.account!=null) 24.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(s.busy) Text("Loading…",color=MaterialTheme.colorScheme.primary)
                    s.error?.let { Text(it,color=Color(0xffffb4ab)); Button(onClick=vm::clearError) { Text("Dismiss") } }
                    val holder=rememberSaveableStateHolder()
                    holder.SaveableStateProvider("${s.profile?.machineId}:${s.account?.id}:${s.screen}:${s.selectedSection?.key}:${s.detail?.ratingKey}") {
                        when(s.screen) {
                            Screen.CONNECTION -> TvConnection(s,vm)
                            Screen.LOGIN -> TvLogin(s,vm)
                            Screen.HOME -> TvHome(s,vm,session,loader)
                            Screen.LIBRARY -> TvLibrary(s,vm,session,loader)
                            Screen.DETAILS -> TvDetails(s,vm,session,loader)
                            Screen.SEARCH -> TvSearch(s,vm,session,loader)
                            Screen.SETTINGS -> TvSettings(s,vm)
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun TvConnection(s: AppState,vm: SessionViewModel) {
    var url by rememberSaveable { mutableStateOf("") }; var local by rememberSaveable { mutableStateOf(false) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("SLOPLEX",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.displayMedium); Text("Bring your library to the big screen",style=MaterialTheme.typography.headlineSmall) }
        item { androidx.compose.material3.MaterialTheme(colorScheme=androidx.compose.material3.darkColorScheme()) { OutlinedTextField(url,{url=it},label={androidx.compose.material3.Text("Server URL")},singleLine=true,modifier=Modifier.width(580.dp)) } }
        item { Button(onClick={local=!local}) { Text(if(local) "Private LAN HTTP allowed" else "Enable private LAN HTTP") }; Text("HTTP sends credentials unencrypted on your LAN. Use HTTPS for remote access.",style=MaterialTheme.typography.bodySmall) }
        item { Button(onClick={vm.connect(url,local)},enabled=url.isNotBlank()&&!s.busy) { Text("Connect") } }
        items(s.profiles,key={it.machineId}) { profile -> Button(onClick={vm.connect(profile.url,profile.allowLanHttp,profile.machineId)}) { Text("${profile.name} · ${profile.url}") } }
        item { Text("Local servers",style=MaterialTheme.typography.titleLarge); Button(onClick=vm::discover,enabled=!s.discovering) { Text(if(s.discovering) "Searching…" else "Search network") } }
        items(s.discovered,key={it.machineId}) { found -> Button(onClick={url=found.url}) { Text("${found.name} · ${found.url}") } }
    }
}
@Composable private fun TvLogin(s: AppState,vm: SessionViewModel) {
    val keyboard=LocalSoftwareKeyboardController.current
    var user by rememberSaveable { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max=650.dp).imePadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("Sign in to Sloplex",style=MaterialTheme.typography.displaySmall); Text(s.profile?.url.orEmpty())
        androidx.compose.material3.MaterialTheme(colorScheme=androidx.compose.material3.darkColorScheme()) {
            Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                OutlinedTextField(user,{user=it},label={androidx.compose.material3.Text("Username")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},label={androidx.compose.material3.Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done,autoCorrectEnabled=false),keyboardActions=KeyboardActions(onDone={val value=password;password="";keyboard?.hide();if(user.isNotBlank()&&value.isNotBlank()&&!s.busy) vm.login(user,value)}),modifier=Modifier.fillMaxWidth())
            }
        }
        Button(onClick={val value=password;password="";keyboard?.hide();vm.login(user,value)},enabled=user.isNotBlank()&&password.isNotBlank()&&!s.busy) { Text("Sign in") }
        Button(onClick=vm::connectionScreen) { Text("Change server") }
    }
}
@Composable private fun TvPoster(item: MediaItem,session: Session?,loader: ImageLoader,modifier: Modifier=Modifier,path: String=item.poster) {
    val context=LocalContext.current
    val request=remember(path,session) { runCatching { ImageRequest.Builder(context).data(session?.client?.url(path)?.toString()).memoryCacheKey("${session?.scope}:${session?.tokenId}:${path}").build() }.getOrNull() }
    Box(modifier.background(Color(0xff203449)),contentAlignment=Alignment.Center) {
        if(path.isBlank()||request==null) Text("S",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.displayMedium)
        else AsyncImage(request,item.title,imageLoader=loader,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
    }
}
@Composable private fun TvRow(row: MediaRow,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    var lastId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(row.title,style=MaterialTheme.typography.headlineSmall)
        LazyRow(Modifier.focusRestorer(),horizontalArrangement=Arrangement.spacedBy(24.dp),contentPadding=PaddingValues(8.dp)) {
            items(row.items,key={"${it.type}:${it.ratingKey}"}) { item ->
                val focus=remember { FocusRequester() }
                LaunchedEffect(item.ratingKey) { if(lastId==item.ratingKey) focus.requestFocus() }
                Card(onClick={lastId=item.ratingKey;vm.details(item)},modifier=Modifier.width(150.dp).focusRequester(focus).onFocusChanged { if(it.isFocused) lastId=item.ratingKey }) {
                    Column { TvPoster(item,session,loader,Modifier.fillMaxWidth().height(210.dp)); Text(item.title,maxLines=2,modifier=Modifier.padding(10.dp),style=MaterialTheme.typography.titleMedium); if(item.viewOffset>0) Text("Resume ${item.viewOffset/60000} min",Modifier.padding(horizontal=10.dp),style=MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
@Composable private fun TvHome(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(32.dp)) {
        item { Text("Tonight starts here",style=MaterialTheme.typography.displaySmall); Text("${s.account?.name} · ${s.profile?.name}",color=MaterialTheme.colorScheme.primary) }
        s.rows.firstOrNull()?.items?.firstOrNull { it.backdrop.isNotBlank() }?.let { featured ->
            item(key="featured") {
                Card(onClick={vm.details(featured)},modifier=Modifier.fillMaxWidth().aspectRatio(16f/6f)) {
                    Box(Modifier.fillMaxSize()) {
                    TvPoster(featured,session,loader,Modifier.fillMaxSize(),path=featured.backdrop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xee080e17)))))
                    Text(featured.title,Modifier.align(Alignment.BottomStart).padding(24.dp),style=MaterialTheme.typography.headlineLarge)
                    }
                }
            }
        }
        s.rows.forEach { row -> item(key=row.title) { TvRow(row,vm,session,loader) } }
        if(s.rows.isEmpty()&&!s.busy) item { Text("Add media libraries on your Sloplex server to get started.") }
    }
}
@Composable private fun TvLibrary(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(30.dp)) {
        item { Text(s.selectedSection?.title.orEmpty(),style=MaterialTheme.typography.displaySmall) }
        s.rows.forEach { row -> item { TvRow(row,vm,session,loader) } }
        s.items.chunked(20).forEachIndexed { index,items -> item(key=index) { TvRow(MediaRow(if(index==0) "Browse library" else "More titles",items),vm,session,loader) } }
        if(s.items.isEmpty()&&!s.busy) item { Text("No media is available in this library.") }
    }
}
@Composable private fun TvDetails(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    val item=s.detail ?: return
    var version by rememberSaveable(item.ratingKey) { mutableIntStateOf(0) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(28.dp)) {
        item { Row(horizontalArrangement=Arrangement.spacedBy(32.dp)) {
            TvPoster(item,session,loader,Modifier.width(190.dp).height(280.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(item.title,style=MaterialTheme.typography.displaySmall)
                Text(listOf(item.year.takeIf { it>0 }?.toString().orEmpty(),item.contentRating,"${item.durationMs/60000} min").filter { it.isNotBlank() }.joinToString(" · "),color=MaterialTheme.colorScheme.primary)
                Text(item.summary.ifBlank { "No description available." },maxLines=5,style=MaterialTheme.typography.bodyLarge)
                s.nextEpisode?.let { next -> Button(onClick={vm.play(next)}) { Text("${if(next.viewOffset>0) "Resume" else "Next episode"}: ${next.title}") } }
                if(item.playable) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) { Button(onClick={vm.play(item,true,version)}) { Text(if(item.viewOffset>0) "Resume" else "Play") }; Button(onClick={vm.watched(item)}) { Text(if(item.viewCount>0) "Mark unwatched" else "Mark watched") } }
                if(item.viewOffset>0) Button(onClick={vm.play(item,false,version)}) { Text("Start over") }
            }
        } }
        if(item.versions.size>1) item { LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp)) { items(item.versions.size) { i -> val media=item.versions[i]; Button(onClick={version=i}) { Text("${if(version==i) "✓ " else ""}${media.videoResolution} · ${media.videoCodec}") } } } }
        if(s.children.isNotEmpty()) item { TvRow(MediaRow(if(item.type=="show") "Seasons" else "Episodes & items",s.children),vm,session,loader) }
        item { Button(onClick=vm::back) { Text("Back") } }
    }
}
@Composable private fun TvSearch(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    Column(verticalArrangement=Arrangement.spacedBy(32.dp)) {
        androidx.compose.material3.MaterialTheme(colorScheme=androidx.compose.material3.darkColorScheme()) { OutlinedTextField(s.query,vm::search,label={androidx.compose.material3.Text("Search your library")},modifier=Modifier.width(580.dp),singleLine=true) }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(28.dp)) { s.items.chunked(20).forEach { items -> item { TvRow(MediaRow("Results",items),vm,session,loader) } } }
    }
}
@Composable private fun TvSettings(s: AppState,vm: SessionViewModel) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Text("Server & account",style=MaterialTheme.typography.displaySmall); Text("${s.profile?.url}\nServer ${s.profile?.version}\nSloplex TV 0.1.0 alpha\nAccount: ${s.account?.name}") }
        item { Button(onClick=vm::connectionScreen) { Text("Switch server") } }
        item { Button(onClick=vm::logout) { Text("Sign out") } }
        items(s.tokens,key={it.id}) { token -> Button(onClick={vm.revoke(token.id)}) { Text("Sign out ${token.device}") } }
    }
}
@Composable private fun TvPlayer(vm: SessionViewModel,p: PlayerState) {
    var options by remember { mutableStateOf(false) }; var lastKey by remember { mutableLongStateOf(0) }
    val first=remember { FocusRequester() }
    LaunchedEffect(p.item?.ratingKey) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown) false else when(event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if(event.nativeKeyEvent.repeatCount==0) vm.engine.toggle();true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_MEDIA_REWIND -> { val now=System.currentTimeMillis(); if(now-lastKey>400) { lastKey=now; vm.seek(p.positionMs+if(event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) 30000 else -10000) };true }
            else -> false
        }
    }) {
        AndroidView(factory={PlayerView(it).apply { useController=false;keepScreenOn=true;isFocusable=false;isFocusableInTouchMode=false }},update={it.player=vm.engine.player},modifier=Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xdc101b2b)).padding(32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text(p.item?.title.orEmpty(),style=MaterialTheme.typography.headlineSmall)
            Text("${p.positionMs/60000}:${(p.positionMs/1000%60).toString().padStart(2,'0')} / ${p.durationMs/60000} min · ${p.mode ?: "Preparing"}${if(p.buffering) " · Buffering" else ""}")
            p.syncError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            p.error?.let { Text(it); Button(onClick=vm::retryPlayback) { Text("Retry playback") } }
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Button(onClick={vm.seek(p.positionMs-10000)}) { Text("−10 sec") }
                Button(onClick={vm.engine.toggle()},modifier=Modifier.focusRequester(first)) { Text(if(p.playing) "Pause" else "Play") }
                Button(onClick={vm.seek(p.positionMs+30000)}) { Text("+30 sec") }
                Button(onClick={options=true}) { Text("Tracks & quality") }
                Button(onClick=vm::back) { Text("Close") }
            }
        }
        if(options) Box(Modifier.fillMaxSize().background(Color(0xf0101b2b)).padding(50.dp)) {
            BackHandler { options=false }
            LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item { Text("Audio & subtitles",style=MaterialTheme.typography.headlineMedium); Button(onClick={options=false}) { Text("Done") } }
                val streams=p.item?.versions?.getOrNull(p.mediaIndex)?.parts?.flatMap { it.streams }.orEmpty()
                items(streams.filter { it.streamType==2 },key={it.id}) { stream -> Button(onClick={vm.track(audio=stream.id);options=false}) { Text(stream.displayTitle) } }
                item { Button(onClick={vm.track(subtitle=0);options=false}) { Text("Subtitles off") } }
                items(streams.filter { it.streamType==3 },key={it.id}) { stream -> Button(onClick={vm.track(subtitle=stream.id);options=false}) { Text(stream.extendedDisplayTitle) } }
                items(Quality.entries) { quality -> Button(onClick={vm.quality(quality);options=false}) { Text(quality.name.replace('_',' ')) } }
            }
        }
    }
}
