@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.sloplex.ui.mobile

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import app.sloplex.core.data.Session
import app.sloplex.core.model.*
import app.sloplex.core.player.*
import app.sloplex.feature.session.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val colors=darkColorScheme(primary=Color(0xff55e6c1),secondary=Color(0xff9cb8ef),background=Color(0xff080e17),surface=Color(0xff101b2b),surfaceVariant=Color(0xff192a3c))

@Composable fun SloplexMobile(vm: SessionViewModel) {
    val s by vm.state.collectAsStateWithLifecycle(); val playback by vm.playback.collectAsStateWithLifecycle(); val session by vm.session.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val loader=remember(session) { ImageLoader.Builder(context).apply { session?.let { okHttpClient(it.client.http) }; diskCache(null) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) { val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations!=true) vm.background(); if(event==Lifecycle.Event.ON_START) vm.foreground() }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    BackHandler(s.showPlayer || s.screen in listOf(Screen.DETAILS,Screen.LIBRARY,Screen.SEARCH,Screen.SETTINGS)) { vm.back() }
    MaterialTheme(colorScheme=colors) {
        Surface(Modifier.fillMaxSize()) {
            if(s.showPlayer) MobilePlayer(vm,playback)
            else {
                val wide=LocalConfiguration.current.screenWidthDp>=600
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    if(wide && s.account!=null) NavigationRail { Spacer(Modifier.height(24.dp)); Text("S",style=MaterialTheme.typography.headlineLarge,color=colors.primary); Spacer(Modifier.height(32.dp)); NavigationItems(s,vm,true) }
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(s.screen !in listOf(Screen.HOME,Screen.CONNECTION,Screen.LOGIN)) IconButton(onClick=vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back") }
                            Text("SLOPLEX",style=MaterialTheme.typography.titleLarge,color=colors.primary,modifier=Modifier.weight(1f))
                            if(s.account!=null) IconButton(onClick=vm::refresh) { Icon(Icons.Default.Refresh,"Refresh") }
                        }
                        if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        s.error?.let { message -> Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(message,Modifier.weight(1f)); TextButton(onClick=vm::clearError) { Text("Dismiss") } } } }
                        val holder=rememberSaveableStateHolder()
                        Box(Modifier.weight(1f)) { holder.SaveableStateProvider("${s.profile?.machineId}:${s.account?.id}:${s.screen}:${s.selectedSection?.key}:${s.detail?.ratingKey}") {
                            when(s.screen) {
                                Screen.CONNECTION -> Connection(s,vm)
                                Screen.LOGIN -> Login(s,vm)
                                Screen.HOME -> Home(s,vm,session,loader)
                                Screen.LIBRARY -> Library(s,vm,session,loader)
                                Screen.DETAILS -> Details(s,vm,session,loader,wide)
                                Screen.SEARCH -> Search(s,vm,session,loader)
                                Screen.SETTINGS -> Settings(s,vm,playback)
                            }
                        } }
                        if(!wide && s.account!=null) NavigationBar { NavigationItems(s,vm,false) }
                    }
                }
            }
        }
    }
}

@Composable private fun NavigationItems(s: AppState,vm: SessionViewModel,rail: Boolean) {
    val tabs=listOf(Triple("Home",Icons.Default.Home, { vm.home() }),Triple("Movies",MovieIcon,{ s.sections.firstOrNull { it.type=="movie" }?.let(vm::browse) ?: vm.home() }),Triple("TV",TvIcon,{ s.sections.firstOrNull { it.type=="show" }?.let(vm::browse) ?: vm.home() }),Triple("Search",Icons.Default.Search,{ vm.searchScreen() }),Triple("Settings",Icons.Default.Settings,{ vm.settings() }))
    fun selected(i: Int) = when(i) { 0 -> s.screen==Screen.HOME; 1 -> s.screen==Screen.LIBRARY && s.selectedSection?.type=="movie"; 2 -> s.screen==Screen.LIBRARY && s.selectedSection?.type=="show"; 3 -> s.screen==Screen.SEARCH; else -> s.screen==Screen.SETTINGS }
    if(rail) tabs.forEachIndexed { i,(name,icon,action) -> NavigationRailItem(selected=selected(i),onClick=action,icon={Icon(icon,name)},label={Text(name)}) }
    else Row(Modifier.fillMaxWidth()) { tabs.forEachIndexed { i,(name,icon,action) -> NavigationBarItem(selected=selected(i),onClick=action,icon={Icon(icon,name)},label={Text(name)}) } }
}
@Composable private fun Connection(s: AppState,vm: SessionViewModel) {
    var url by rememberSaveable { mutableStateOf("") }; var local by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Your library. Your screen.",style=MaterialTheme.typography.headlineLarge); Text("Connect to your Sloplex server on your network or over HTTPS.",Modifier.padding(top=8.dp)) }
        item { OutlinedTextField(url,{url=it},label={Text("Server address")},placeholder={Text("192.168.1.20:32400 or https://…")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(local,{local=it}); Text("Allow HTTP on my private LAN. Credentials are unencrypted on that LAN.",style=MaterialTheme.typography.bodySmall) } }
        item { Button(onClick={vm.connect(url,local)},enabled=url.isNotBlank()&&!s.busy) { Text("Connect") } }
        item { Text("Saved servers",style=MaterialTheme.typography.titleLarge) }
        items(s.profiles,key={it.machineId}) { profile -> OutlinedButton(onClick={vm.connect(profile.url,profile.allowLanHttp,profile.machineId)},modifier=Modifier.fillMaxWidth(),enabled=!s.busy) { Text("${profile.name} · ${profile.url}") } }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Text("On your network",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge); TextButton(onClick=vm::discover,enabled=!s.discovering) { Text(if(s.discovering) "Searching…" else "Scan") } } }
        if(s.discovered.isEmpty()) item { Text("No server found yet. Manual connection also works with Tailscale HTTPS.") }
        items(s.discovered,key={it.machineId}) { found -> OutlinedButton(onClick={url=found.url},modifier=Modifier.fillMaxWidth()) { Text("${found.name} · ${found.url}") } }
    }
}
@Composable private fun Login(s: AppState,vm: SessionViewModel) {
    val keyboard=LocalSoftwareKeyboardController.current
    var name by rememberSaveable { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Welcome to your cinema",style=MaterialTheme.typography.headlineLarge)
        Text(s.profile?.url.orEmpty(),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(name,{name=it},label={Text("Username")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done,autoCorrectEnabled=false),keyboardActions=KeyboardActions(onDone={val value=password;password="";keyboard?.hide();if(name.isNotBlank()&&value.isNotBlank()&&!s.busy) vm.login(name,value)}),modifier=Modifier.fillMaxWidth())
        Button(onClick={val value=password; password="";keyboard?.hide();vm.login(name,value)},enabled=name.isNotBlank()&&password.isNotBlank()&&!s.busy) { Text("Sign in") }
        Text("Use a local Sloplex account with a password. Set up the owner's password in the server web app first.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick=vm::connectionScreen) { Text("Change server") }
    }
}
@Composable private fun Artwork(item: MediaItem,session: Session?,loader: ImageLoader,modifier: Modifier=Modifier,path: String=item.poster) {
    val context=LocalContext.current
    val request=remember(path,session) { runCatching { ImageRequest.Builder(context).data(session?.client?.url(path)?.toString()).memoryCacheKey("${session?.scope}:${session?.tokenId}:${path}").build() }.getOrNull() }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        if(path.isBlank()||request==null) Icon(MovieIcon,null,Modifier.size(32.dp))
        else AsyncImage(request,contentDescription=item.title,imageLoader=loader,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
    }
}
@Composable private fun Poster(item: MediaItem,vm: SessionViewModel,session: Session?,loader: ImageLoader,modifier: Modifier=Modifier) {
    Column(modifier.clickable { vm.details(item) }) {
        Artwork(item,session,loader,Modifier.fillMaxWidth().aspectRatio(2f/3f))
        Text(item.title,style=MaterialTheme.typography.titleSmall,maxLines=2,modifier=Modifier.padding(top=8.dp))
        if(item.viewOffset>0 && item.durationMs>0) LinearProgressIndicator(progress={ (item.viewOffset.toFloat()/item.durationMs).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().padding(top=4.dp))
        if(item.year>0) Text(item.year.toString(),style=MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun Rows(rows: List<MediaRow>,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    rows.forEach { row -> Text(row.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(16.dp)); LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(horizontal=16.dp)) { items(row.items,key={"${it.type}:${it.ratingKey}"}) { Poster(it,vm,session,loader,Modifier.width(140.dp)) } } }
}
@Composable private fun Home(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.padding(20.dp)) { Text("Tonight starts here",style=MaterialTheme.typography.headlineLarge); Text("${s.account?.name} · ${s.profile?.name}",color=colors.secondary) } }
        item { LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(s.sections,key={it.key}) { section -> FilledTonalButton(onClick={vm.browse(section)}) { Text(section.title) } } } }
        s.rows.firstOrNull()?.items?.firstOrNull { it.backdrop.isNotBlank() }?.let { featured ->
            item(key="featured") {
                Box(Modifier.fillMaxWidth().aspectRatio(16f/6f).clickable { vm.details(featured) }) {
                    Artwork(featured,session,loader,Modifier.fillMaxSize(),path=featured.backdrop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xee080e17)))))
                    Text(featured.title,Modifier.align(Alignment.BottomStart).padding(24.dp),style=MaterialTheme.typography.headlineLarge)
                }
            }
        }
        s.rows.forEach { row -> item(key=row.title) { Rows(listOf(row),vm,session,loader) } }
        if(s.rows.isEmpty()&&!s.busy) item { Text("Your server has no available media yet. Add movie or TV libraries in Sloplex.",Modifier.padding(24.dp)) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
@Composable private fun Library(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) {
    LazyVerticalGrid(columns=GridCells.Adaptive(130.dp),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) { Text(s.selectedSection?.title.orEmpty(),style=MaterialTheme.typography.headlineMedium) }
        if(s.rows.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Rows(s.rows,vm,session,loader) }
        items(s.items,key={it.ratingKey}) { Poster(it,vm,session,loader) }
        if(s.items.isEmpty()&&!s.busy) item(span={GridItemSpan(maxLineSpan)}) { Text("No media is available in this library.") }
    }
}
@Composable private fun Details(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader,wide: Boolean) {
    val item=s.detail ?: return
    var version by rememberSaveable(item.ratingKey) { mutableIntStateOf(0) }
    LazyColumn(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { if(wide) Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) { Artwork(item,session,loader,Modifier.width(220.dp).aspectRatio(2f/3f)); DetailText(item,Modifier.weight(1f)) }
        else Column { Artwork(item,session,loader,Modifier.width(170.dp).aspectRatio(2f/3f)); Spacer(Modifier.height(16.dp)); DetailText(item) } }
        s.nextEpisode?.let { next -> item { Button(onClick={vm.play(next)}) { Text("${if(next.viewOffset>0) "Resume" else "Next episode"}: ${next.title}") } } }
        if(item.playable) item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={vm.play(item,true,version)}) { Text(if(item.viewOffset>0) "Resume ${clock(item.viewOffset)}" else "Play") }; if(item.viewOffset>0) OutlinedButton(onClick={vm.play(item,false,version)}) { Text("Start over") } } }
        if(item.playable) item { OutlinedButton(onClick={vm.watched(item)}) { Text(if(item.viewCount>0) "Mark unwatched" else "Mark watched") } }
        if(item.versions.size>1) item { Column { Text("Available versions"); item.versions.forEachIndexed { i,v -> FilterChip(selected=version==i,onClick={version=i},label={Text("${v.videoResolution} ${v.videoCodec} · ${v.container}")}) } } }
        item { item.versions.getOrNull(version)?.let { media -> Text("${media.width} × ${media.height} · ${media.videoCodec.uppercase()} · ${media.audioCodec.uppercase()} · ${media.container.uppercase()}",style=MaterialTheme.typography.bodySmall); media.parts.flatMap { it.streams }.filter { it.streamType in 2..3 }.forEach { Text(it.extendedDisplayTitle.ifBlank { it.displayTitle },style=MaterialTheme.typography.bodySmall) } } }
        if(item.cast.isNotEmpty()) item { Text("Cast",style=MaterialTheme.typography.titleLarge); Text(item.cast.joinToString("  ·  ") { it.tag }) }
        if(s.children.isNotEmpty()) item { Text(if(item.type=="show") "Seasons" else "Episodes & items",style=MaterialTheme.typography.titleLarge) }
        items(s.children,key={"${it.type}:${it.ratingKey}"}) { child -> ListItem(headlineContent={Text(child.title)},supportingContent={Text(child.summary.take(180).ifBlank { if(child.viewCount>0) "Watched" else "Unwatched" })},leadingContent={Artwork(child,session,loader,Modifier.width(64.dp).height(90.dp))},modifier=Modifier.clickable { vm.details(child) }) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
@Composable private fun DetailText(item: MediaItem,modifier: Modifier=Modifier) { Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(item.title,style=MaterialTheme.typography.headlineLarge); Text(listOfNotNull(item.year.takeIf { it>0 }?.toString(),item.durationMs.takeIf { it>0 }?.let { "${it/60000} min" },item.contentRating.takeIf { it.isNotBlank() },item.rating.takeIf { it>0 }?.let { "★ $it" }).joinToString(" · "),color=colors.secondary); if(item.genres.isNotEmpty()) Text(item.genres.joinToString(" · ") { it.tag }); Text(item.summary.ifBlank { "No description is available for this title." }) } }
@Composable private fun Search(s: AppState,vm: SessionViewModel,session: Session?,loader: ImageLoader) { Column(Modifier.padding(16.dp)) { OutlinedTextField(s.query,vm::search,label={Text("Search your library")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(16.dp)); LazyVerticalGrid(GridCells.Adaptive(130.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) { items(s.items,key={it.ratingKey}) { Poster(it,vm,session,loader) } } } }
@Composable private fun Settings(s: AppState,vm: SessionViewModel,p: PlayerState) {
    LazyColumn(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Connected to ${s.profile?.name}",style=MaterialTheme.typography.headlineSmall); Text("${s.profile?.url}\nServer ${s.profile?.version}\nAndroid alpha 0.1.0\nAccount: ${s.account?.name}\nConnection: ${if(s.profile?.url?.startsWith("https:")==true) "HTTPS" else "Private LAN HTTP"}") }
        item { OutlinedButton(onClick=vm::connectionScreen) { Text("Switch server") }; OutlinedButton(onClick=vm::logout) { Text("Sign out") } }
        item { Text("Signed-in devices",style=MaterialTheme.typography.titleLarge) }
        items(s.tokens,key={it.id}) { token -> Row(verticalAlignment=Alignment.CenterVertically) { Text(token.device,Modifier.weight(1f)); TextButton(onClick={vm.revoke(token.id)}) { Text("Sign out device") } } }
        item { Text("Playback diagnostics",style=MaterialTheme.typography.titleLarge); Text("Mode: ${p.mode ?: "Idle"}\nVideo: ${p.videoCodec}\nAudio: ${p.audioCodec}\nResolution: ${p.resolution}\nBuffering: ${p.buffering}"); p.error?.let { Text(it) } }
    }
}
@Composable private fun MobilePlayer(vm: SessionViewModel,p: PlayerState) {
    // Read the player during composition so AndroidView updates when async preparation replaces it.
    val player = vm.engine.player
    var menus by remember { mutableStateOf(false) }; var position by remember { mutableFloatStateOf(p.positionMs.toFloat()) }; var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(p.positionMs) { if(!dragging) position=p.positionMs.toFloat() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory={PlayerView(it).apply { useController=false;keepScreenOn=true;setEnableComposeSurfaceSyncWorkaround(true) }},update={it.player=player},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically) { FilledTonalIconButton(onClick=vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Close player") }; Text(p.item?.title.orEmpty(),Modifier.padding(12.dp),style=MaterialTheme.typography.titleMedium) }
            Surface(color=Color(0xd9101b2b)) { Column(Modifier.padding(16.dp)) {
                if(p.buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
                p.syncError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            p.error?.let { Text(it); Button(onClick=vm::retryPlayback) { Text("Retry playback") } }
                Slider(value=position.coerceIn(0f,p.durationMs.coerceAtLeast(1).toFloat()),onValueChange={dragging=true;position=it},onValueChangeFinished={dragging=false;vm.seek(position.toLong())},valueRange=0f..p.durationMs.coerceAtLeast(1).toFloat())
                Text("${clock(p.positionMs)} / ${clock(p.durationMs)} · ${p.mode ?: "Preparing"}")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={vm.seek(p.positionMs-10000)}) { Text("−10") }
                    IconButton(onClick={vm.engine.toggle()}) { Icon(if(p.playing) PauseIcon else Icons.Default.PlayArrow,if(p.playing) "Pause" else "Play") }
                    IconButton(onClick={vm.seek(p.positionMs+30000)}) { Text("+30") }
                    IconButton(onClick={menus=true}) { Icon(Icons.Default.Settings,"Audio, subtitles, and quality") }
                }
            } }
        }
    }
    if(menus) AlertDialog(onDismissRequest={menus=false},confirmButton={TextButton(onClick={menus=false}) { Text("Done") }},title={Text("Playback options")},text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        val streams=p.item?.versions?.getOrNull(p.mediaIndex)?.parts?.flatMap { it.streams }.orEmpty()
        Text("Audio"); streams.filter { it.streamType==2 }.forEach { stream -> TextButton(onClick={vm.track(audio=stream.id);menus=false}) { Text(stream.displayTitle) } }
        Text("Subtitles"); TextButton(onClick={vm.track(subtitle=0);menus=false}) { Text("Off") }; streams.filter { it.streamType==3 }.forEach { stream -> TextButton(onClick={vm.track(subtitle=stream.id);menus=false}) { Text(stream.extendedDisplayTitle) } }
        Text("Quality"); Quality.entries.forEach { quality -> TextButton(onClick={vm.quality(quality);menus=false}) { Text(quality.name.replace('_',' ')) } }
    } })
}
private fun clock(ms: Long): String { val seconds=ms.coerceAtLeast(0)/1000; return "%d:%02d".format(seconds/60,seconds%60) }

private val MovieIcon = ImageVector.Builder("SloplexMovie",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(3f,4f);lineTo(21f,4f);lineTo(21f,20f);lineTo(3f,20f);close();moveTo(6f,7f);lineTo(18f,7f);lineTo(18f,17f);lineTo(6f,17f);close() }
}.build()
private val TvIcon = ImageVector.Builder("SloplexTv",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(2f,4f);lineTo(22f,4f);lineTo(22f,18f);lineTo(2f,18f);close();moveTo(4f,6f);lineTo(20f,6f);lineTo(20f,16f);lineTo(4f,16f);close();moveTo(8f,20f);lineTo(16f,20f);lineTo(16f,22f);lineTo(8f,22f);close() }
}.build()
private val PauseIcon = ImageVector.Builder("SloplexPause",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.Black)) { moveTo(6f,5f);lineTo(10f,5f);lineTo(10f,19f);lineTo(6f,19f);close();moveTo(14f,5f);lineTo(18f,5f);lineTo(18f,19f);lineTo(14f,19f);close() }
}.build()
