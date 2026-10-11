# Validation record — 2026-10-10

This is a working native alpha with release gates still open. Debug APK compilation does not establish successful installation, app launch, remote navigation or playback on a device. No physical-device testing occurred.

## Passing checks

| Check | Result |
|---|---|
| Audited Go revision | `go test -race ./...` passed before changes |
| Server hardening branch | Full `go test -race ./...`, `go vet ./...`, formatting and diff checks passed; [PR #4](https://github.com/mtdewwolf/sloplex/pull/4) merged during implementation |
| Native JVM contracts | 21 passed, 0 failed, 0 skipped with actual server runner: 7 timeline tests, 13 HTTP/JSON/security/pagination contracts, 1 actual Go server integration |
| Final Android build | Mobile and TV `assembleDebug` and `assembleDebugAndroidTest` passed |
| Final lint | Both `lintDebug` tasks passed, no errors |
| Merged manifests | Correct multicast permission, backups/transfer excluded, no extra exported test activity; AndroidX profile receiver protected by `android.permission.DUMP` |
| Review foundation, API and shared core | Independently compiled, including both debug and instrumentation APKs; API JVM run passed 20 checks with the opt-in server test skipped |
| Release build path | Both R8-optimized unsigned release APKs compile; release signing and device validation remain pending |

The actual server integration creates a temporary isolated server using the audited source plus the tested strict-auth change, a generated H.264/AAC movie and a disposable test account. Its source tree `7a87413ecacde9cb335a07ba88dde73094248ce6` exactly matches merged server revision `0eeef57fe834a541581a0d7709be022816b5dbb6`. It verifies identity, login, token listing, paginated library metadata, authenticated 206 ranges, direct-play decision, progress, watched/unwatched, search, second-client last-arrival-wins updates and revoked-token 401 after logout. The fixture password is intentionally public test data; no live server credentials are included.

Commands from the Android root:

```bash
./gradlew :core:model:test :core:network:test
./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug \
  :app-mobile:assembleDebugAndroidTest :app-tv:assembleDebugAndroidTest \
  :app-mobile:lintDebug :app-tv:lintDebug
python3 scripts/run-server-contracts.py --server-source /path/to/audited/sloplex
```

The ordinary JVM task skips the opt-in real-server test. Use the Python runner to execute all 21 checks against the Go implementation. The server runner requires Go 1.24+, ffmpeg, JDK 17 and the Android SDK.

## Device tests blocked here

Phone and Android TV API 28 software emulators booted. Hardware acceleration is unavailable (`/dev/kvm` absent). Phone APK installation failed with Android package service `Broken pipe`; TV installation hung and the emulator was stopped. No instrumentation result or app launch is claimed. Repeated software-emulation attempts did not resolve the infrastructure failure.

Compiled instrumentation covers mobile login/browse/details/watched state/activity recreation/Back/logout, TV login and D-pad focus/Back, session revocation and server-identity mismatch, Keystore/ciphertext and cache isolation, and generated direct/HLS playback with offsets, progress, subtitle and quality changes while playing and paused, rapid background/foreground changes, and final progress/session cleanup before remote logout. Rotation and changed window dimensions need separate device checks. Player operations enforce the main thread, and generation checks prevent stale callbacks from reporting or stopping a newer session. These are implementation controls, not a claim that the device tests passed. Run the tests on hardware-accelerated CI and devices before accepting the foundation launch or streaming gates.

```bash
./gradlew :app-mobile:connectedDebugAndroidTest
./gradlew :app-tv:connectedDebugAndroidTest
```

## Lint and release limits

Lint reports include newer dependency/target-SDK suggestions, the TV vector banner rasterization warning, the mobile touchscreen requirement suggestion, and the intentional dynamic-LAN cleartext declaration. Runtime HTTP consent, private-address DNS policy, exact-origin enforcement and disabled redirects constrain that declaration. TLS validation stays enabled. R8 reports a warning in the upstream Kotlinx Serialization keep rule; optimized builds complete successfully. Compile/target 35 must be reviewed against store policy before publication; the pinned stack is build-tested, not a claim to use the newest releases.

The initial alpha explicitly rejects multi-part playback. Background video, offline downloads, casting, server administration and TV pairing are not implemented. HDR is requested as server-tone-mapped SDR; multichannel audio plays directly when the device decodes or passes it through, and is otherwise converted to stereo while H.264 video is copied. Track ordinal mapping, image-subtitle burn-in, HLS rendition timing, capacity errors, network interruptions, tablet/foldable layouts, accessibility, process restoration and physical TV focus/remote behavior remain device acceptance gates. Signed release packaging and production rollout are pending those checks.

## Review and publication

Four sequential feature branches contain foundation, API/discovery, shared auth/cache/playback, and mobile/TV integration. Patches, Git bundle, source, debug APKs and reports are packaged together. The Android destination repository has no initial commit, so no Android PR can be opened until a base exists. The user's instruction prohibits default-branch implementation commits; initialization therefore awaits explicit authorization for a README-only base. No Android default branch has been changed.
