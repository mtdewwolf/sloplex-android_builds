# Sloplex for Android

Native Kotlin clients for phones, tablets, foldables, and Android TV. The existing [Sloplex Go server](https://github.com/mtdewwolf/sloplex) remains the backend. This repository does not fork or replace that server.

This is an **alpha implementation**, not a production release. See [the implementation plan](docs/android/implementation-plan.md) and [compatibility matrix](docs/android/compatibility-matrix.md) for remaining acceptance gates and actual test evidence.

## Build

Install JDK 17 and Android SDK 35, then from `android/`:

```bash
./gradlew :core:model:test :core:network:test :app-mobile:assembleDebug :app-tv:assembleDebug
./gradlew :app-mobile:lintDebug :app-tv:lintDebug
```

Configure the SDK through `ANDROID_HOME` or an untracked `android/local.properties`. The Gradle wrapper downloads Gradle 8.13 and checks its SHA-256. Android Studio can open the `android/` folder directly.

## Phone APK releases

The [Phone APK release workflow](.github/workflows/release.yml) publishes an optimized, signed phone APK and SHA-256 checksum to GitHub Releases. Downloads are standalone APK files, without the Actions artifact ZIP or sign-in requirement.

Before the first release, add these repository secrets under **Settings → Secrets and variables → Actions** using your existing Android signing keystore:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The entire keystore encoded as Base64, without line breaks |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password (may match the keystore password) |

For example, send the encoded keystore directly to GitHub using the CLI, without printing it:

```bash
base64 < /path/to/sloplex.jks | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo mtdewwolf/sloplex-android_builds
```

Encode the actual keystore file, rather than its filename, certificate, or a sample value. The workflow opens the keystore and verifies its alias and both passwords before starting the Android build. If this check fails, replace the affected secrets and rerun the failed workflow from its run page.

Keep using and backing up that same key for future releases. A release signed with a different key from the installed debug APK requires a one-time uninstall; subsequent releases with the same key can update normally. APK version names come from tags and version codes are `1000 +` the release workflow run number, so publish versions in order and keep this workflow's run history.

Every push to `Master` automatically publishes an alpha APK as `v0.1.0-alpha.<run number>`. No manual tag creation is needed. For a named release, use **Actions → Phone APK release → Run workflow**, select `Master`, and enter a version such as `0.2.0` or `v0.1.0-alpha02`. The `v` prefix is optional; leaving the version blank creates another automatic alpha release. New versions build the selected branch's commit and create the release tag automatically after a successful build.

You can also push a version tag yourself:

```bash
git switch Master
git pull --ff-only
git tag v0.1.0-alpha02
git push origin v0.1.0-alpha02
```

Tag pushes build, test, lint, sign, verify, and publish the phone APK. Tags containing a suffix, such as `-alpha02`, become prereleases. To retry a failed release, run the workflow again with that version. If its tag already exists, the original tagged commit is rebuilt. Published assets are preserved and their checksums rechecked on retries; use a new version for changed code. Incomplete drafts can be retried.

The download URL for the example above is `https://github.com/mtdewwolf/sloplex-android_builds/releases/download/v0.1.0-alpha02/Sloplex-phone.apk`. Each successful workflow also puts its download link in the run summary. Pull requests that change release packaging build and verify an APK with a temporary test key, without publishing a release or using production signing secrets.

## Connect

Start a Sloplex server and configure a local account with a password in its web app. Use HTTPS for remote access, including Tailscale Serve: enter the hostname (for example `sloplex.<tailnet>.ts.net`) or paste the browser address — a trailing `/web/` path is dropped and HTTPS hostnames default to port 443. On a private LAN, enter an IP and port (default 32400) and explicitly enable local HTTP. The apps never import an owner recovery token.

Discovery uses GDM multicast/broadcast searches on UDP 32414, not mDNS. Discovered addresses must still pass `/identity` validation. A saved server's machine identity is checked before reusing credentials.

For account isolation and remote/proxied access, leave the server's `allowedNetworks` empty. On a deliberately trusted LAN, `strictTokenAuth` from merged [server PR #4](https://github.com/mtdewwolf/sloplex/pull/4) rejects invalid supplied tokens while preserving tokenless owner access. The legacy default falls back to owner when a trusted peer sends an invalid token; client-side checks alone cannot close that race. Read [the security review](docs/android/security-review.md) before enabling trusted-network access.

## Implemented paths

- Multiple saved servers; validated identity; manual HTTPS and private LAN connections; GDM discovery.
- Username/password login, encrypted device tokens, restoration, logout, and device-token revocation.
- Account-scoped Room metadata cache with bounded size, DataStore profiles, and server-authoritative browsing.
- Home rows, libraries, collections, playlists, search, show/season/episode navigation, media versions, and watched state.
- Shared Media3 direct/HLS playback, source-time resume/seek, track selection, quality limits, progress reporting, and conversion-session cleanup.
- Cinematic dark and amber [design system](docs/android/design.md), adaptive Material 3 phone/tablet layouts, and TV Material UI with visible remote focus and matching launcher assets.

Passwords are never persisted, tokens travel in headers, redirects are disabled, and TLS uses the system trust store. Downloads and short-code TV pairing are not implemented. There is no Node.js or Bun dependency in the Android runtime.

## Backend provenance

The initial contract audit is pinned to server commit `0aa2e5f0c52f0d30cd38f4e9dadcfe274a184ce2` on `ccr-fac3cf8e-8f1rqf`. The separately tested hardening was merged as `0eeef57fe834a541581a0d7709be022816b5dbb6`; its tree matches the tested local change, and CI pins that revision. Re-audit later server changes before updating compatibility claims. See [architecture](docs/android/architecture.md), [API contract](docs/android/api-contract.md), and [validation report](docs/android/validation-report.md).
