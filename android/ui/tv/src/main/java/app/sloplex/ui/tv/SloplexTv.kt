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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.sloplex.ui.design.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription

private val tvColors = darkColorScheme(
    primary = SloplexPalette.Accent, onPrimary = SloplexPalette.OnAccent,
    primaryContainer = SloplexPalette.AccentContainer, onPrimaryContainer = SloplexPalette.Accent,
    background = SloplexPalette.Background, onBackground = SloplexPalette.Text,
    surface = SloplexPalette.Surface, onSurface = SloplexPalette.Text,
    surfaceVariant = SloplexPalette.Elevated, onSurfaceVariant = SloplexPalette.Muted,
    secondary = SloplexPalette.Muted, border = SloplexPalette.Border,
    inverseSurface = SloplexPalette.Accent, inverseOnSurface = SloplexPalette.OnAccent,
)

@Composable fun SloplexTv(vm: SessionViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p by vm.playback.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val loader = remember(session) { ImageLoader.Builder(context).apply { session?.let { okHttpClient(it.client.http) }; diskCache(null) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
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
        MaterialTheme(colorScheme = tvColors) {
            Box(Modifier.fillMaxSize().background(SloplexPalette.Background)) {
                if (s.showPlayer) TvPlayer(vm, p)
                else Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (s.account != null) TvNavigation(s, vm)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (s.busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        s.error?.let { message ->
                            Row(Modifier.fillMaxWidth().background(Color(0xFF3B2024), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = vm::clearError) { Text("Dismiss") }
                            }
                        }
                        val holder = rememberSaveableStateHolder()
                        holder.SaveableStateProvider("${s.profile?.machineId}:${s.account?.id}:${s.screen}:${s.selectedSection?.key}:${s.detail?.ratingKey}") {
                            when (s.screen) {
                                Screen.CONNECTION -> TvConnection(s, vm)
                                Screen.LOGIN -> TvLogin(s, vm)
                                Screen.HOME -> TvHome(s, vm, session, loader)
                                Screen.LIBRARY -> TvLibrary(s, vm, session, loader)
                                Screen.DETAILS -> TvDetails(s, vm, session, loader)
                                Screen.SEARCH -> TvSearch(s, vm, session, loader)
                                Screen.SETTINGS -> TvSettings(s, vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TvNavigation(s: AppState, vm: SessionViewModel) {
    Column(Modifier.width(154.dp).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { BrandMark(); Text("SLOPLEX", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp) }
        Spacer(Modifier.height(28.dp))
        LazyColumn(Modifier.weight(1f).focusGroup(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
            item { TvNavButton("Home", s.screen == Screen.HOME, vm::home) }
            items(s.sections, key = { it.key }) { section -> TvNavButton(section.title, s.screen == Screen.LIBRARY && s.selectedSection?.key == section.key) { vm.browse(section) } }
            item { TvNavButton("Search", s.screen == Screen.SEARCH, vm::searchScreen) }
            item { TvNavButton("Settings", s.screen == Screen.SETTINGS, vm::settings) }
        }
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.account?.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.profile?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun TvNavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        colors = ButtonDefaults.colors(
            containerColor = if (selected) SloplexPalette.AccentContainer else Color.Transparent,
            contentColor = if (selected) SloplexPalette.Accent else SloplexPalette.Muted,
            focusedContainerColor = SloplexPalette.Accent, focusedContentColor = SloplexPalette.OnAccent,
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable private fun TvHeading(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SloplexPalette.Muted)
    }
}

@Composable private fun TvEmpty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(SloplexPalette.Surface, RoundedCornerShape(20.dp)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = SloplexPalette.Accent)
        Text(subtitle, color = SloplexPalette.Muted)
    }
}

@Composable private fun TvConnection(s: AppState, vm: SessionViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    var url by rememberSaveable { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(0.85f).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { BrandMark(); Text("SLOPLEX", style = MaterialTheme.typography.titleLarge, letterSpacing = 2.sp) }
            Text("Your library.\nThe big screen.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("A front-row seat to your movies and shows. Connect your Sloplex server and settle in.", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodyLarge)
            Text("YOUR PERSONAL CINEMA", style = MaterialTheme.typography.labelMedium, color = SloplexPalette.Accent)
        }
        LazyColumn(Modifier.weight(1.15f).imePadding(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(12.dp)) {
            item { TvHeading("Connect a server", "HTTPS for remote access. Private IP for your LAN.") }
            item { OutlinedTextField(url, { url = it }, label = { androidx.compose.material3.Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() })) }
            item { Button(onClick = { local = !local }) { Text(if (local) "Private LAN HTTP allowed" else "Enable private LAN HTTP") }; Text("HTTP sends credentials unencrypted on your LAN. Use HTTPS for remote access.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted) }
            item { Button(onClick = { vm.connect(url, local) }, enabled = url.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth(), scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Connect") } }
            if (s.profiles.isNotEmpty()) item { Text("Saved servers", style = MaterialTheme.typography.titleLarge) }
            items(s.profiles, key = { it.machineId }) { profile -> Button(onClick = { vm.connect(profile.url, profile.allowLanHttp, profile.machineId) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth(), scale = ButtonDefaults.scale(focusedScale = 1f)) { Column { Text(profile.name); Text(profile.url, style = MaterialTheme.typography.bodySmall) } } }
            item { Text("On your network", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp)); Button(onClick = vm::discover, enabled = !s.discovering) { Text(if (s.discovering) "Searching…" else "Search network") } }
            if (s.discovered.isEmpty()) item { Text("No local servers found yet. Enter an address above or search again.", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall) }
            items(s.discovered, key = { it.machineId }) { found -> Button(onClick = { url = found.url }) { Column { Text(found.name); Text(found.url, style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}

@Composable private fun TvLogin(s: AppState, vm: SessionViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BrandMark()
            Text("Welcome to\nyour cinema", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Sign in to ${s.profile?.name.orEmpty()} and pick up where you left off.", style = MaterialTheme.typography.bodyLarge, color = SloplexPalette.Muted)
            Text(s.profile?.url.orEmpty(), color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TvHeading("Sign in to Sloplex")
            OutlinedTextField(user, { user = it }, label = { androidx.compose.material3.Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(password, { password = it }, label = { androidx.compose.material3.Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onDone = { val value = password; password = ""; keyboard?.hide(); if (user.isNotBlank() && value.isNotBlank() && !s.busy) vm.login(user, value) }), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Button(onClick = { val value = password; password = ""; keyboard?.hide(); vm.login(user, value) }, enabled = user.isNotBlank() && password.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth(), scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Sign in") }
            Button(onClick = vm::connectionScreen) { Text("Change server") }
        }
    }
}

@Composable private fun TvPoster(item: MediaItem, session: Session?, loader: ImageLoader, modifier: Modifier = Modifier, path: String = item.poster) {
    val context = LocalContext.current
    val request = remember(path, session) { runCatching { ImageRequest.Builder(context).data(session?.client?.url(path)?.toString()).memoryCacheKey("${session?.scope}:${session?.tokenId}:${path}").build() }.getOrNull() }
    Box(modifier.background(Brush.linearGradient(listOf(SloplexPalette.Elevated, SloplexPalette.Surface))), contentAlignment = Alignment.Center) {
        BrandMark()
        if (path.isNotBlank() && request != null) AsyncImage(request, null, imageLoader = loader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable private fun TvRow(row: MediaRow, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    var lastId by rememberSaveable { mutableStateOf<String?>(null) }
    val landscape = row.title == "Continue watching"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(row.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(12.dp)) {
            items(row.items, key = { "${it.type}:${it.ratingKey}" }) { item ->
                val focus = remember { FocusRequester() }
                LaunchedEffect(item.ratingKey) { if (lastId == item.ratingKey) focus.requestFocus() }
                Card(
                    onClick = { lastId = item.ratingKey; vm.details(item) },
                    modifier = Modifier.width(if (landscape) 248.dp else 144.dp).focusRequester(focus).onFocusChanged { if (it.isFocused) lastId = item.ratingKey },
                    shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    colors = CardDefaults.colors(containerColor = SloplexPalette.Surface, contentColor = SloplexPalette.Text, focusedContainerColor = SloplexPalette.Elevated, focusedContentColor = SloplexPalette.Text),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, SloplexPalette.Accent))),
                ) {
                    Column {
                        Box {
                            TvPoster(item, session, loader, Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f), path = if (landscape) item.backdrop.ifBlank { item.poster } else item.poster)
                            if (item.viewCount > 0) Text("✓", Modifier.align(Alignment.TopEnd).padding(8.dp).background(SloplexPalette.Background, CircleShape).padding(6.dp).semantics { contentDescription = "Watched" }, color = SloplexPalette.Accent)
                            if (item.viewOffset > 0 && item.durationMs > 0) androidx.compose.material3.LinearProgressIndicator(progress = { (item.viewOffset.toFloat() / item.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), trackColor = SloplexPalette.Border)
                        }
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            val caption = if (landscape && item.durationMs > item.viewOffset) "${(item.durationMs - item.viewOffset) / 60000} min left" else item.year.takeIf { it > 0 }?.toString().orEmpty()
                            if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodySmall, color = SloplexPalette.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TvHome(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(12.dp)) {
        item { TvHeading("Tonight starts here", "Welcome back, ${s.account?.name.orEmpty()}.") }
        s.rows.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.backdrop.isNotBlank() }?.let { featured ->
            item(key = "featured") {
                Card(onClick = { vm.details(featured) }, modifier = Modifier.fillMaxWidth(), shape = CardDefaults.shape(shape = RoundedCornerShape(20.dp)), scale = CardDefaults.scale(focusedScale = 1.02f), border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, SloplexPalette.Accent)))) {
                    Box(Modifier.fillMaxWidth()) {
                        TvPoster(featured, session, loader, Modifier.matchParentSize(), path = featured.backdrop)
                        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(SloplexPalette.Background.copy(alpha = 0.96f), SloplexPalette.Background.copy(alpha = 0.25f)))))
                        Column(Modifier.widthIn(max = 480.dp).heightIn(min = 240.dp).padding(28.dp), verticalArrangement = Arrangement.Bottom) {
                            Text("IN THE SPOTLIGHT", color = SloplexPalette.Accent, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(featured.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(tvMetadata(featured), Modifier.padding(top = 10.dp), color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall)
                            if (featured.summary.isNotBlank()) Text(featured.summary, Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("Explore title  →", Modifier.padding(top = 18.dp), color = SloplexPalette.Accent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        s.rows.forEachIndexed { i, row -> item(key = "row:$i:${row.title}") { TvRow(row, vm, session, loader) } }
        if (s.rows.isEmpty() && !s.busy) item { TvEmpty("Your cinema starts here", "Add movie or TV libraries on your Sloplex server. Your titles will appear here.") }
    }
}

@Composable private fun TvLibrary(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(12.dp)) {
        item { TvHeading(s.selectedSection?.title ?: "Library", "${s.items.size} titles in your library") }
        s.rows.forEachIndexed { i, row -> item(key = "row:$i:${row.title}") { TvRow(row, vm, session, loader) } }
        s.items.chunked(20).forEachIndexed { index, items -> item(key = "titles:$index") { TvRow(MediaRow(if (index == 0) "Browse library" else "More titles", items), vm, session, loader) } }
        if (s.items.isEmpty() && s.rows.isEmpty() && !s.busy) item { TvEmpty("Nothing here yet", "No media is available in this library.") }
    }
}

private fun tvMetadata(item: MediaItem) = listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.contentRating.takeIf { it.isNotBlank() }, item.durationMs.takeIf { it > 0 }?.let { "${it / 60000} min" }, item.rating.takeIf { it > 0 }?.let { "★ $it" }).joinToString(" · ")

@Composable private fun TvDetails(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    val item = s.detail ?: return
    var version by rememberSaveable(item.ratingKey) { mutableIntStateOf(0) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                TvPoster(item, session, loader, Modifier.width(170.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(item.type.uppercase(), style = MaterialTheme.typography.labelMedium, color = SloplexPalette.Accent)
                    Text(item.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(tvMetadata(item), color = SloplexPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                    if (item.genres.isNotEmpty()) Text(item.genres.joinToString(" · ") { it.tag }, color = SloplexPalette.Accent, style = MaterialTheme.typography.bodySmall)
                    Text(item.summary.ifBlank { "No description available." }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        s.nextEpisode?.let { next -> item { Button(onClick = { vm.play(next) }) { Text("${if (next.viewOffset > 0) "Resume" else "Next episode"}: ${next.title}") } } }
        if (item.playable) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                item { Button(onClick = { vm.play(item, true, version) }, colors = ButtonDefaults.colors(containerColor = SloplexPalette.AccentContainer, contentColor = SloplexPalette.Accent)) { Text(if (item.viewOffset > 0) "Resume" else "Play") } }
                if (item.viewOffset > 0) item { Button(onClick = { vm.play(item, false, version) }) { Text("Start over") } }
                item { Button(onClick = { vm.watched(item) }) { Text(if (item.viewCount > 0) "Mark unwatched" else "Mark watched") } }
            }
        }
        if (item.versions.size > 1) item {
            Text("Available versions", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) { items(item.versions.size) { i -> val media = item.versions[i]; Button(onClick = { version = i }) { Text("${if (version == i) "✓ " else ""}${media.videoResolution} · ${media.videoCodec}") } } }
        }
        if (item.cast.isNotEmpty()) item { Text("Cast", style = MaterialTheme.typography.titleLarge); Text(item.cast.joinToString(" · ") { it.tag }, Modifier.padding(top = 12.dp), color = SloplexPalette.Muted) }
        if (s.children.isNotEmpty()) item { TvRow(MediaRow(if (item.type == "show") "Seasons" else "Episodes & items", s.children), vm, session, loader) }
        item { Button(onClick = vm::back) { Text("Back") } }
    }
}

@Composable private fun TvSearch(s: AppState, vm: SessionViewModel, session: Session?, loader: ImageLoader) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(12.dp)) {
        TvHeading("Find your next favorite", "Search movies, shows, and more.")
        OutlinedTextField(s.query, vm::search, label = { androidx.compose.material3.Text("Search your library") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (s.items.isEmpty() && !s.busy) item { TvEmpty(if (s.query.isBlank()) "A whole library to explore" else "No matches yet", if (s.query.isBlank()) "Start with a title you love." else "Try another title or a shorter search.") }
            s.items.chunked(20).forEachIndexed { i, items -> item(key = i) { TvRow(MediaRow(if (i == 0) "${s.items.size} results" else "More results", items), vm, session, loader) } }
        }
    }
}

@Composable private fun TvSettings(s: AppState, vm: SessionViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(12.dp)) {
        item { TvHeading("Make yourself at home", "Your server, account, and devices.") }
        item {
            Column(Modifier.fillMaxWidth().background(SloplexPalette.Surface, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.account?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text("Connected to ${s.profile?.name}", color = SloplexPalette.Accent)
                Text("${s.profile?.url}\nServer ${s.profile?.version} · ${if (s.profile?.url?.startsWith("https:") == true) "HTTPS" else "Private LAN HTTP"}", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                    item { Button(onClick = vm::connectionScreen) { Text("Switch server") } }
                    item { Button(onClick = vm::logout) { Text("Sign out") } }
                }
            }
        }
        item { Text("Signed-in devices", style = MaterialTheme.typography.titleLarge) }
        items(s.tokens, key = { it.id }) { token -> Button(onClick = { vm.revoke(token.id) }) { Text("Sign out ${token.device}") } }
        item { Text("Sloplex TV · 0.1.0 alpha", color = SloplexPalette.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun TvPlayer(vm: SessionViewModel,p: PlayerState) {
    val player = vm.engine.player
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
        AndroidView(factory={PlayerView(it).apply { useController=false;keepScreenOn=true;isFocusable=false;isFocusableInTouchMode=false;setEnableComposeSurfaceSyncWorkaround(true) }},update={it.player=player},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(SloplexPalette.Surface.copy(alpha=0.94f),RoundedCornerShape(topStart=24.dp,topEnd=24.dp)).padding(32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
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
        if(options) Box(Modifier.fillMaxSize().background(SloplexPalette.Background.copy(alpha=0.97f)).padding(50.dp)) {
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
