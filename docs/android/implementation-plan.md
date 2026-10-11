# Implementation and review plan

The user selected a separate Android repository, `mtdewwolf/sloplex-android_builds`. Keep Go changes in `mtdewwolf/sloplex`, with the server revision pinned in Android docs. No implementation commits go directly to either default branch.

At task start the destination had no commits or branches. GitHub's Git-tree API rejects an empty repository with 409, so publication requires an initial review base. Do not silently bypass the no-default-branch-commit instruction to create one. Prepared code remains locally reviewable until the repository is initialized.

## Review sequence

1. `feat/android-foundation`: Gradle Kotlin DSL, catalog/wrapper, mobile/TV apps, launcher/theme assets, core/UI module boundaries, APK/test CI. Gate: both APKs compile; both launch in emulator CI.
2. `feat/android-api-client`: verified DTOs/Retrofit/origin-bound OkHttp, identity/URL validation, GDM parsing/discovery, actual API contract and error mapping. Gate: contract/MockWebServer tests including redirects, malformed JSON, pagination, range headers, milliseconds and discovery validation.
3. `feat/android-playback-core`: Keystore token vault, session restoration/revocation, account-scoped Room cache and repositories, shared Media3 decisions/sources, source-time seeks, tracks, quality, watch reporting and cleanup. Gate: account/token checks, instrumented cache/Keystore tests, and generated-media direct/HLS playback tests.
4. `feat/android-streaming`: connected adaptive phone/tablet and distinct TV flows, full metadata/children/search, next unwatched episode, deterministic local recommendations, player controls and diagnostics. Gate: mobile/TV UI, rotation, Back and D-pad focus checks.
5. `fix/strict-trusted-token-auth` in server repo: opt-in invalid-token fail-closed mode with unchanged legacy default and Go race tests. Independent of Android scaffold and deployment.

Each later Android PR should target the earlier feature branch until its prerequisite merges, then retarget to the default branch. The final source package includes working alpha functionality beyond the first two requested PRs; do not describe the foundation as the complete product.

## Remaining production work

| Work | Acceptance |
|---|---|
| Navigation | Back stack across home/library/show/season/player, focus restoration, process death, rotation and preserved scroll tested on mobile/tablet/TV |
| Playback | Direct H.264 MP4/MKV, HEVC/device-dependent, HDR tone mapping, AAC/multichannel conversion, source-time seek/resume, direct/HLS text/image subtitle switching |
| Recovery | Network loss, server restart, auth revocation, busy conversion slots, foreground/background interruption and process death |
| Show UX | Next unwatched episode/resume, seasons/episodes with actual server metadata; multiple libraries selectable |
| UX polish | Featured backdrop placement, empty/error/skeleton layouts, TalkBack, large fonts, TV overscan and physical remote testing |
| Release | Signed release APK/AAB policy, real hardware matrix, versioned compatibility policy, release checklist and documented rollback |

Recommendations rank unwatched recent movies by overlap with genres of actually watched recent items, then added time and stable ID. The row is omitted when no evidence exists; it is not a server recommendation service. Recently watched derives only from returned recent items and is not a full history API. Collections and playlists are read-only browsing in this alpha. Offline downloads, server administration, short-code pairing, casting and background playback are out of scope until streaming is reliable. Multi-part movies are explicitly rejected rather than partially played.

Four sequential local commits and branches are prepared. Android PR publication is blocked by the empty destination repository; the independent server hardening change was merged as [PR #4](https://github.com/mtdewwolf/sloplex/pull/4) during implementation. See [compatibility matrix](compatibility-matrix.md) for acceptance gates; the final streaming branch adds the complete validation report.

Update `compatibility-matrix.md` with actual evidence after each check. Compile success is not playback success. Emulator success is not physical hardware success. Fix failed tests before claiming an acceptance gate.
