# Native Android architecture

## Source audit

Backend: `mtdewwolf/sloplex`, commit `0aa2e5f0c52f0d30cd38f4e9dadcfe274a184ce2`. The default branch at audit time was `ccr-fac3cf8e-8f1rqf`, not `main`. The new Android repository was empty at the start of implementation.

Inspected README, go.mod, startup/configuration, account storage/handlers, library stores/scanning/mapping, play-state storage/handlers, transcoder decisions/session manager, subtitle extraction/serving, web client, all spec pages, deployment/release docs, and existing CI workflows/tests. Discovery lives in `internal/gdm`; there is no `internal/lan`. HTTP endpoints are registered in `internal/server`, not the web static bundle.

The server is a Go 1.24 application using the standard library. `cmd/sloplex/main.go` wires local JSON-backed stores, scanner, ffprobe/ffmpeg services, artwork, accounts, watch state, playlists, updates, optional TrueNAS integration, and HTTP/GDM servers. Libraries are filesystem roots; metadata comes from local `.nfo` files. There is no hosted account provider or remote metadata/recommendation service. Sections contain movies or show→season→episode hierarchies. Leaf items hold versions→parts→streams. IDs and timestamps remain server-owned.

TrueNAS runs the existing Linux amd64 CPU container with persistent data and read-only media mounts. Optional Tailscale sidecar/Serve provides remote HTTPS. Android makes ordinary HTTP API calls; it requires no deployment rewrite or server-side Kotlin runtime. The server's CI runs gofmt, vet, race tests, Playwright browser suites and Docker/Compose smoke tests. Release CI publishes semver images to GHCR after checks. Android gets independent APK build/lint/test CI.

## Refined module boundaries

| Module | Responsibility |
|---|---|
| `core:model` | Serializable contract models, composite identities, typed errors, source/output timeline |
| `core:network` | Retrofit, origin-bound OkHttp, DTO parsing, safe URL rules, pure GDM parser, pagination |
| `core:data` | Authentication, Keystore AES-GCM vault, GDM sockets, profiles, Room cache, repositories, Hilt providers |
| `core:player` | Shared Media3 engine, decoder inspection, server decisions, range/HLS sources, watch reporting, conversion cleanup |
| `feature:session` | Hilt ViewModel, immutable application state, intent methods, session/navigation orchestration |
| `ui:mobile` | Material 3 phone/tablet layouts, adaptive rail/grid/details, touch controls |
| `ui:tv` | TV Material cards/buttons, side navigation, D-pad focus, TV player controls |
| `app-mobile`, `app-tv` | Separate application IDs, manifests, Hilt entrypoints, launcher assets |

Auth/database/discovery are packages inside `core:data`; initial features share one orchestration module. This avoids empty Gradle modules while preserving platform/network/player/UI isolation. Split repositories or features only when their independent complexity warrants it. Networking, authentication and playback are never duplicated across UIs.

## Data and state

UI receives `StateFlow<AppState>` and `StateFlow<PlayerState>`. Screen intents enter ViewModel coroutines; repositories validate the active session before server operations. Retrofit models accept forward-compatible extra JSON fields but retain required identity/item fields. Paging uses server `totalSize`, `offset`, and actual returned item count.

Room keys metadata by machine ID, account ID, bucket and item ID. Refresh replaces a bucket transactionally, removes missing items, expires entries after seven days, caps each bucket at 2,000 items and all rows at 5,000. UI data is fetched from the server before presentation; cached permissions never grant playback rights. Account logout clears that account's rows. Artwork loaders are scoped to the session, have no persistent disk cache, and shut down on account changes. Metadata cache Flow exists for later cache-first presentation after permission validation; current screens primarily present fresh server data.

Profiles (max 20) live in DataStore. One encrypted device session is remembered per server; switching accounts means logout and fresh login. Passwords only live briefly in the input/action call. SavedStateHandle restores the active detail ID after process recreation; Compose saveable holders retain grid/row scroll state during rotation. Full navigation-stack process restoration and hinge-aware separating layouts remain acceptance work.

## Playback

Fetch full item metadata, choose a version and its single part, evaluate installed video/audio decoders and video size, then call the actual decision endpoint. Direct sources use the returned part key and authenticated byte ranges. Conversion uses HLS with a fresh client label, seconds offset, conservative HDR disabled and stereo capability, resolution/bitrate constraints, and `directStream=0` to prevent unsupported copied codecs. Conversion selection does not start an ffmpeg process until Media3 requests the HLS start URL.

The server creates a different random HLS capability ID from the client's session label. Relative rendition URLs must remain on the configured origin. Stop/ping use the original label and authenticated account. Source progress equals conversion offset plus Media3 output progress; conversion seeks rebuild the source at the new offset. Text subtitles use the server's VTT conversion for direct sources or its HLS renditions; image subtitles trigger server burn-in.

A MediaSession supplies audio focus/media keys. This alpha is foreground video playback and pauses in the background; it does not advertise a background playback service. Rotation retains the ViewModel/player; UI controls and player views are separate for touch and D-pad input. Process death restores the server-saved resume point, not an old HLS session.

## Dependency selection

JDK 17 / Gradle 8.13 / AGP 8.11.1 / Kotlin + serialization + Compose compiler plugins 2.2.20; KSP 2.2.20-2.0.3, Hilt 2.57.2, Compose BOM 2025.08.01, TV Material 1.0.1, Media3 1.8.0, Room 2.7.2. These are pinned stable releases, not dynamic/latest dependencies. Kotlin's official compatibility table supports this AGP/Gradle range; AGP 8.11 requires Gradle 8.13/JDK 17. Sources: https://kotlinlang.org/docs/gradle-configure-project.html and https://developer.android.com/build/releases/agp-8-11-0-release-notes. Compose compiler matches Kotlin exactly per https://developer.android.com/jetpack/androidx/releases/compose-kotlin. Build verification is the final compatibility check.
