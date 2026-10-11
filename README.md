# Sloplex Android foundation

Native Kotlin/Compose mobile and TV launchers with Hilt, a shared module graph, pinned dependencies, Gradle wrapper verification and Android CI. This branch contains PR 1; API connection and streaming arrive in the stacked feature branches.

Open `android/` in Android Studio. Use JDK 17 and SDK 35, then run `./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug` in that directory.

See `docs/android/` for the audited Go contract, architecture, security review and implementation plan.
