# Sloplex Android API and discovery

PR 2 adds a verified Retrofit/Kotlinx Serialization client, identity validation, multiple saved DataStore server profiles, manual HTTPS/private LAN connections, GDM discovery and typed network errors to the native launchers. Authentication and media playback UI follow in the stacked branches.

Build with JDK 17/SDK 35 in `android/`: `./gradlew :core:model:test :core:network:test :app-mobile:assembleDebug :app-tv:assembleDebug`. The optional actual-Go-server contract test runs with `python3 scripts/run-server-contracts.py --server-source /path/to/sloplex`.

See `docs/android/` for exact endpoint schemas, security implications and the implementation plan.
