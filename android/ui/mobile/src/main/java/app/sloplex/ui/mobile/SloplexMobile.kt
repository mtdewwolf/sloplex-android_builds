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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.sloplex.ui.design.*

@Composable fun SloplexMobile(vm: SessionViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loader = remember(session) { ImageLoader.Builder(context).apply { session?.let { okHttpClient(it.client.http) }; diskCache(null) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations != true) vm.background()
            if (event == Lifecycle.Event.ON_START) vm.foreground()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(s.showPlayer || s.screen in listOf(Screen.DETAILS, Screen.LIBRARY, Screen.SEARCH, Screen.SETTINGS)) { vm.back() }
    SloplexTheme {
        Surface(Modifier.fillMaxSize(), color = SloplexPalette.Background) {
            if (s.showPlayer) MobilePlayer(vm, playback)
            else {
                val wide = LocalConfiguration.current.screenWidthDp >= 600
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    if (wide && s.account != null) NavigationRail(containerColor = SloplexPalette.Surface) {
                        BrandMark(Modifier.padding(vertical = 24.dp))
                        NavigationItems(s, vm, true)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s.screen !in listOf(Screen.HOME, Screen.CONNECTION, Screen.LOGIN)) IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                            if (!wide || s.account == null) { BrandMark(); Spacer(Modifier.width(10.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("SLOPLEX", style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
                                s.profile?.let { Text(it.name, style = MaterialTheme.typography.labelSmall, color = SloplexPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (s.account != null) IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh", tint = SloplexPalette.Muted) }
                        }
                        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        s.error?.let { message ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                                    TextButton(onClick = vm::clearError) { Text("Dismiss") }
                                }
                            }
                        }
                        val holder = rememberSaveableStateHolder()
                        Box(Modifier.weight(1f)) {
                            holder.SaveableStateProvider("${s.profile?.machineId}:${s.account?.id}:${s.screen}:${s.selectedSection?.key}:${s.detail?.ratingKey}") {
                                when (s.screen) {
                                    Screen.CONNECTION -> Connection(s, vm)
                                    Screen.LOGIN -> Login(s, vm)
                                    Screen.HOME -> Home(s, vm, session, loader)
                                    Screen.LIBRARY -> Library(s, vm, session, loader)
                                    Screen.DETAILS -> Details(s, vm, session, loader, wide)
                                    Screen.SEARCH -> Search(s, vm, session, loader)
                                    Screen.SETTINGS -> Settings(s, vm, playback)
                                }
                            }
                        }
                        if (!wide && s.account != null) NavigationBar(containerColor = SloplexPalette.Surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) { NavigationItems(s, vm, false) }
                    }
                }
            }
        }
    }
}

@Composable private fun NavigationItems(s: AppState, vm: SessionViewModel, rail: Boolean) {
    val tabs = listOf(Triple("Home", Icons.Default.Home, { vm.home() }), Triple("Movies", MovieIcon, { s.sections.firstOrNull { it.type == "movie" }?.let(vm::browse) ?: vm.home() }), Triple("TV", TvIcon, { s.sections.firstOrNull { it.type == "show" }?.let(vm::browse) ?: vm.home() }), Triple("Search", Icons.Default.Search, { vm.searchScreen() }), Triple("Settings", Icons.Default.Settings, { vm.settings() }))
    fun selected(i: Int) = when (i) { 0 -> s.screen == Screen.HOME; 1 -> s.screen == Screen.LIBRARY && s.selectedSection?.type == "movie"; 2 -> s.screen == Screen.LIBRARY && s.selectedSection?.type == "show"; 3 -> s.screen == Screen.SEARCH; else -> s.screen == Screen.SETTINGS }
    if (rail) tabs.forEachIndexed { i, (name, icon, action) ->
        NavigationRailItem(selected = selected(i), onClick = action, icon = { Icon(icon, null) }, label = { Text(name) }, colors = NavigationRailItemDefaults.colors(indicatorColor = SloplexPalette.AccentContainer, selectedIconColor = SloplexPalette.Accent, selectedTextColor = SloplexPalette.Accent, unselectedIconColor = SloplexPalette.Muted, unselectedTextColor = SloplexPalette.Muted))
    } else Row(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, (name, icon, action) ->
            NavigationBarItem(selected = selected(i), onClick = action, icon = { Icon(icon, null) }, label = { Text(name, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(indicatorColor = SloplexPalette.AccentContainer, selectedIconColor = SloplexPalette.Accent, selectedTextColor = SloplexPalette.Accent, unselectedIconColor = SloplexPalette.Muted, unselectedTextColor = SloplexPalette.Muted))
        }
    }
}

@Composable private fun PageHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle.isNotBlank()) Text(subtitle, color = SloplexPalette.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun EmptyState(title: String, message: String, icon: ImageVector = MovieIcon) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = SloplexPalette.Accent)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, color = SloplexPalette.Muted, textAlign = TextAlign.Center)
    }
}

@Composable private fun Connection(s: AppState, vm: SessionViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Text("YOUR PERSONAL CINEMA", color = SloplexPalette.Accent, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(12.dp)); PageHeading("Your library.\nYour screen.", "All your movies and shows, in one place. Connect to your Sloplex server to get started.") }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = SloplexPalette.Surface) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Connect a server", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(url, { url = it }, label = { Text("Server address") }, placeholder = { Text("https://sloplex.example.com") }, supportingText = { Text("HTTPS hostname or private LAN IP and port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(local, { local = it }); Text("Allow HTTP on my private LAN. Credentials are unencrypted on that LAN.", style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted) }
                        Button(onClick = { vm.connect(url, local) }, enabled = url.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Connect") }
                    }
                }
            }
            if (s.profiles.isNotEmpty()) item { Text("Saved servers", style = MaterialTheme.typography.titleLarge) }
            items(s.profiles, key = { it.machineId }) { profile ->
                ServerCard(profile.name, profile.url, !s.busy) { vm.connect(profile.url, profile.allowLanHttp, profile.machineId) }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("On your network", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = vm::discover, enabled = !s.discovering) { Text(if (s.discovering) "Searching…" else "Scan") } } }
            if (s.discovered.isEmpty()) item { Text("No server found yet. Enter an address above, or scan your network again.", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodyMedium) }
            items(s.discovered, key = { it.machineId }) { found -> ServerCard(found.name, found.url, !s.busy) { url = found.url } }
        }
    }
}

@Composable private fun ServerCard(name: String, address: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = SloplexPalette.Surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(TvIcon, null, tint = SloplexPalette.Accent)
            Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium); Text(address, color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun Login(s: AppState, vm: SessionViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("MAKE YOURSELF AT HOME", style = MaterialTheme.typography.labelSmall, color = SloplexPalette.Accent)
            PageHeading("Welcome to\nyour cinema", "Sign in to ${s.profile?.name ?: "your Sloplex server"}.")
            Surface(shape = MaterialTheme.shapes.large, color = SloplexPalette.Surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onDone = { val value = password; password = ""; keyboard?.hide(); if (name.isNotBlank() && value.isNotBlank() && !s.busy) vm.login(name, value) }), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    Button(onClick = { val value = password; password = ""; keyboard?.hide(); vm.login(name, value) }, enabled = name.isNotBlank() && password.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Sign in") }
                }
            }
            Text("Use a local Sloplex account with a password. Set up the owner's password in the server web app first.", style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted)
            Text(s.profile?.url.orEmpty(), style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted)
            TextButton(onClick = vm::connectionScreen) { Text("Change server") }
        }
    }
}

@Composable private fun Artwork(item: MediaItem, session: Session?, loader: ImageLoader, modifier: Modifier = Modifier, path: String = item.poster) {
    val context = LocalContext.current
    val request = remember(path, session) { runCatching { ImageRequest.Builder(context).data(session?.client?.url(path)?.toString()).memoryCacheKey("${session?.scope}:${session?.tokenId}:${path}").build() }.getOrNull() }
    Box(modifier.background(Brush.linearGradient(listOf(SloplexPalette.Elevated, SloplexPalette.Surface))), contentAlignment = Alignment.Center) {
        Icon(MovieIcon, null, Modifier.size(36.dp), tint = SloplexPalette.Muted.copy(alpha = 0.45f))
        if (path.isNotBlank() && request != null) AsyncImage(request, contentDescription = null, imageLoader = loader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable private fun Poster(item: MediaItem, vm: SessionViewModel, session: Session?, loader: ImageLoader, modifier: Modifier = Modifier, landscape: Boolean = false) {
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "View ${item.title}") { vm.details(item) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(MaterialTheme.shapes.medium)) {
            Artwork(item, session, loader, Modifier.fillMaxSize(), path = if (landscape) item.backdrop.ifBlank { item.poster } else item.poster)
            if (item.viewCount > 0) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = SloplexPalette.Background.copy(alpha = 0.9f), shape = CircleShape) { Icon(Icons.Default.Check, "Watched", Modifier.padding(4.dp).size(16.dp), tint = SloplexPalette.Accent) }
            if (item.viewOffset > 0 && item.durationMs > 0) LinearProgressIndicator(progress = { (item.viewOffset.toFloat() / item.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), trackColor = SloplexPalette.Border)
        }
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val caption = if (landscape && item.durationMs > item.viewOffset) "${(item.durationMs - item.viewOffset) / 60000} min left" else listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.contentRating.takeIf { it.isNotBlank() }).joinToString(" · ")
        if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted)
    }
}

@Composable private fun Rows(rows: List<MediaRow>, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    rows.forEach { row ->
        val landscape = row.title == "Continue watching"
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(row.items, key = { "${it.type}:${it.ratingKey}" }) { Poster(it, vm, session, loader, Modifier.width(if (landscape) 248.dp else 140.dp), landscape) }
            }
        }
    }
}

@Composable private fun Featured(item: MediaItem, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(MaterialTheme.shapes.large).clickable(onClickLabel = "View ${item.title}") { vm.details(item) }) {
        Artwork(item, session, loader, Modifier.matchParentSize(), path = item.backdrop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(SloplexPalette.Background.copy(alpha = 0.1f), SloplexPalette.Background.copy(alpha = 0.98f)))))
        Column(Modifier.fillMaxWidth().heightIn(min = if (wide) 340.dp else 300.dp).padding(24.dp), verticalArrangement = Arrangement.Bottom) {
            Text("IN THE SPOTLIGHT", style = MaterialTheme.typography.labelSmall, color = SloplexPalette.Accent)
            Spacer(Modifier.height(12.dp))
            Text(item.title, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(mediaMetadata(item), color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall)
            if (item.summary.isNotBlank()) Text(item.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp).widthIn(max = 520.dp))
            Spacer(Modifier.height(18.dp))
            Button(onClick = { vm.details(item) }) { Icon(Icons.Default.Info, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Explore title") }
        }
    }
}

@Composable private fun Home(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item { PageHeading("Tonight starts here", "Welcome back, ${s.account?.name.orEmpty()}.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
        if (s.sections.isNotEmpty()) item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(s.sections, key = { it.key }) { section -> OutlinedButton(onClick = { vm.browse(section) }) { Icon(if (section.type == "show") TvIcon else MovieIcon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(section.title) } } } }
        s.rows.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.backdrop.isNotBlank() }?.let { featured -> item(key = "featured") { Featured(featured, vm, session, loader) } }
        s.rows.forEachIndexed { index, row -> item(key = "row:$index:${row.title}") { Rows(listOf(row), vm, session, loader) } }
        if (s.rows.isEmpty() && !s.busy) item { EmptyState("Your cinema starts here", "Add movie or TV libraries on your Sloplex server. Your titles will appear here.") }
    }
}

@Composable private fun Library(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    LazyVerticalGrid(columns = GridCells.Adaptive(128.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHeading(s.selectedSection?.title ?: "Library", "${s.items.size} titles in your library") }
        if (s.rows.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { Rows(s.rows, vm, session, loader) } }
        items(s.items, key = { "${it.type}:${it.ratingKey}" }) { Poster(it, vm, session, loader) }
        if (s.items.isEmpty() && s.rows.isEmpty() && !s.busy) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Nothing here yet", "No media is available in this library.") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Details(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader, wide: Boolean) {
    val item = s.detail ?: return
    var version by rememberSaveable(item.ratingKey) { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (item.backdrop.isNotBlank()) item {
            Box(Modifier.fillMaxWidth().height(if (wide) 260.dp else 180.dp).clip(MaterialTheme.shapes.large)) {
                Artwork(item, session, loader, Modifier.fillMaxSize(), path = item.backdrop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, SloplexPalette.Background.copy(alpha = 0.8f)))))
            }
        }
        item {
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Artwork(item, session, loader, Modifier.width(200.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium))
                DetailText(item, Modifier.weight(1f))
            } else if (item.poster.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Artwork(item, session, loader, Modifier.width(104.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium))
                    DetailText(item, Modifier.weight(1f), showSummary = false)
                }
                Text(item.summary.ifBlank { "No description is available for this title." }, style = MaterialTheme.typography.bodyLarge)
            } else DetailText(item)
        }
        s.nextEpisode?.let { next -> item { Button(onClick = { vm.play(next) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("${if (next.viewOffset > 0) "Resume" else "Next episode"}: ${next.title}") } } }
        if (item.playable) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.play(item, true, version) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (item.viewOffset > 0) "Resume ${clock(item.viewOffset)}" else "Play") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.viewOffset > 0) OutlinedButton(onClick = { vm.play(item, false, version) }) { Text("Start over") }
                    OutlinedButton(onClick = { vm.watched(item) }) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (item.viewCount > 0) "Mark unwatched" else "Mark watched") }
                }
            }
        }
        if (item.versions.size > 1) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Available versions", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.versions.forEachIndexed { i, v -> FilterChip(selected = version == i, onClick = { version = i }, label = { Text("${v.videoResolution} ${v.videoCodec} · ${v.container}") }) } }
            }
        }
        item { item.versions.getOrNull(version)?.let { media ->
            Surface(shape = MaterialTheme.shapes.medium, color = SloplexPalette.Surface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Media information", style = MaterialTheme.typography.titleSmall)
                    Text("${media.width} × ${media.height} · ${media.videoCodec.uppercase()} · ${media.audioCodec.uppercase()} · ${media.container.uppercase()}", style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted)
                    media.parts.flatMap { it.streams }.filter { it.streamType in 2..3 }.forEach { Text(it.extendedDisplayTitle.ifBlank { it.displayTitle }, style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted) }
                }
            }
        } }
        if (item.cast.isNotEmpty()) item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Cast", style = MaterialTheme.typography.titleLarge); Text(item.cast.joinToString("  ·  ") { it.tag }, color = SloplexPalette.Muted) } }
        if (s.children.isNotEmpty()) item { Text(if (item.type == "show") "Seasons" else "Episodes & items", style = MaterialTheme.typography.titleLarge) }
        items(s.children, key = { "${it.type}:${it.ratingKey}" }) { child ->
            Surface(onClick = { vm.details(child) }, shape = MaterialTheme.shapes.medium, color = SloplexPalette.Surface) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(child, session, loader, Modifier.width(64.dp).height(90.dp).clip(MaterialTheme.shapes.small))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(child.title, style = MaterialTheme.typography.titleMedium); Text(child.summary.ifBlank { if (child.viewCount > 0) "Watched" else "Unwatched" }, maxLines = 3, overflow = TextOverflow.Ellipsis, color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private fun mediaMetadata(item: MediaItem) = listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.durationMs.takeIf { it > 0 }?.let { "${it / 60000} min" }, item.contentRating.takeIf { it.isNotBlank() }, item.rating.takeIf { it > 0 }?.let { "★ $it" }).joinToString(" · ")

@Composable private fun DetailText(item: MediaItem, modifier: Modifier = Modifier, showSummary: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(item.type.uppercase(), style = MaterialTheme.typography.labelSmall, color = SloplexPalette.Accent)
        Text(item.title, style = MaterialTheme.typography.headlineLarge)
        Text(mediaMetadata(item), color = SloplexPalette.Muted, style = MaterialTheme.typography.bodyMedium)
        if (item.genres.isNotEmpty()) Text(item.genres.joinToString(" · ") { it.tag }, color = SloplexPalette.Accent, style = MaterialTheme.typography.bodySmall)
        if (showSummary) Text(item.summary.ifBlank { "No description is available for this title." }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun Search(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    LazyVerticalGrid(columns = GridCells.Adaptive(128.dp), modifier = Modifier.imePadding(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHeading("Find your next favorite", "Search movies, shows, and more.") }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(s.query, vm::search, label = { Text("Search your library") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (s.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(Icons.Default.Close, "Clear search") } }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium)
        }
        if (s.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("${s.items.size} results", style = MaterialTheme.typography.labelLarge, color = SloplexPalette.Muted) }
        items(s.items, key = { "${it.type}:${it.ratingKey}" }) { Poster(it, vm, session, loader) }
        if (s.items.isEmpty() && !s.busy) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(if (s.query.isBlank()) "A whole library to explore" else "No matches yet", if (s.query.isBlank()) "Start with a title you love." else "Try another title or a shorter search.", Icons.Default.Search) }
    }
}

@Composable private fun Settings(s: AppState, vm: SessionViewModel, p: PlayerState) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { PageHeading("Make yourself at home", "Your server, account, and devices.") }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = SloplexPalette.Surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = CircleShape, color = SloplexPalette.AccentContainer) { Text(s.account?.name?.take(1)?.uppercase().orEmpty(), Modifier.padding(16.dp), color = SloplexPalette.Accent, style = MaterialTheme.typography.titleLarge) }
                        Column { Text(s.account?.name.orEmpty(), style = MaterialTheme.typography.titleLarge); Text("Connected to ${s.profile?.name}", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider(color = SloplexPalette.Border)
                    Text(s.profile?.url.orEmpty(), color = SloplexPalette.Muted)
                    Text("${if (s.profile?.url?.startsWith("https:") == true) "HTTPS" else "Private LAN HTTP"} · Server ${s.profile?.version}", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = vm::connectionScreen, modifier = Modifier.fillMaxWidth()) { Text("Switch server") }
                    TextButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { Text("Signed-in devices", style = MaterialTheme.typography.titleLarge) }
        items(s.tokens, key = { it.id }) { token -> Surface(shape = MaterialTheme.shapes.medium, color = SloplexPalette.Surface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(token.device, Modifier.weight(1f)); TextButton(onClick = { vm.revoke(token.id) }) { Text("Sign out device") } } } }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = SloplexPalette.Surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Playback diagnostics", style = MaterialTheme.typography.titleMedium); Text("Mode: ${p.mode ?: "Idle"}\nVideo: ${p.videoCodec}\nAudio: ${p.audioCodec}\nResolution: ${p.resolution}\nBuffering: ${p.buffering}", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall); p.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        }
        item { Text("Sloplex for Android · 0.1.0 alpha", color = SloplexPalette.Muted, style = MaterialTheme.typography.labelSmall) }
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
            Surface(color=SloplexPalette.Surface.copy(alpha=0.94f),shape=MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp)) {
                if(p.buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
                p.syncError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            p.error?.let { Text(it); Button(onClick=vm::retryPlayback) { Text("Retry playback") } }
                Slider(value=position.coerceIn(0f,p.durationMs.coerceAtLeast(1).toFloat()),onValueChange={dragging=true;position=it},onValueChangeFinished={dragging=false;vm.seek(position.toLong())},valueRange=0f..p.durationMs.coerceAtLeast(1).toFloat())
                Text("${clock(p.positionMs)} / ${clock(p.durationMs)} · ${p.mode ?: "Preparing"}")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={vm.seek(p.positionMs-10000)}) { Text("−10") }
                    FilledIconButton(onClick={vm.engine.toggle()},modifier=Modifier.size(56.dp)) { Icon(if(p.playing) PauseIcon else Icons.Default.PlayArrow,if(p.playing) "Pause" else "Play") }
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
