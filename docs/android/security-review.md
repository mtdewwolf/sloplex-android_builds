# Security review

Audit revision: server `0aa2e5f0c52f0d30cd38f4e9dadcfe274a184ce2`; native alpha implementation. This is a source-level review plus automated tests, not an independent penetration test.

## Release-blocking server behavior

`internal/server/middleware.go` resolves a supplied token first, then falls back to account 1 whenever resolution fails and the TCP peer belongs to `allowedNetworks`. Thus a revoked restricted-account token can gain owner privileges on a trusted LAN. The specs explicitly document this; it is not a client parsing problem. Password/token/account isolation checks cannot eliminate a revocation race between verification and streaming.

Recommended deployment: `allowedNetworks: []`. Merged [server PR #4](https://github.com/mtdewwolf/sloplex/pull/4), revision `0eeef57fe834a541581a0d7709be022816b5dbb6`, adds opt-in `strictTokenAuth: true` (default false), rejecting invalid supplied tokens on protected routes while preserving tokenless trusted-owner access and existing public endpoints. Tests cover issued/revoked/header/query tokens, owner recovery token, no-token legacy access and public identity. The default behavior is intentionally unchanged. Enable the option or remove trusted networks before production native multi-account use. Never grant owner privileges based on a client's claimed address.

HLS session URLs are public random bearer capabilities by existing design. An already issued random session URL is not immediately revoked by deleting an account token. Android stops its own labeled session on logout/revocation and the server expires idle sessions. Strong server-side revocation of all account sessions is a future separately reviewed hardening change. Do not publish HLS URLs in logs or exports.

Trust is based on the TCP peer, not a client-supplied forwarding header. A reverse proxy or Tailscale Serve can make remote requests arrive from loopback: trusting that address grants tokenless remote requests owner access even with `strictTokenAuth` enabled. Keep `allowedNetworks: []` for remote/proxied deployments, as the existing TrueNAS deployment guide instructs. The opt-in flag is a supplied-token hardening control, not a replacement for that deployment policy.

## Native controls

| Area | Implementation / residual risk |
|---|---|
| TLS | Android system trust store; no trust-all manager or hostname override; self-signed/untrusted servers fail visibly |
| HTTP | Platform cleartext enabled for dynamic LAN hosts, but client requires explicit LAN HTTP selection and a DNS policy rejects non-private addresses on every lookup; HTTPS required for remote public hosts |
| Redirects/origin | Redirect following disabled; all API, media, HLS and artwork requests constrained to exact scheme/host/port; no token query parameters |
| Credentials | Login passwords not persisted; token JSON AES-GCM encrypted with Android Keystore key, random IV and machine ID AAD; ciphertext preferences written atomically |
| Session | Restore and repository transactions validate account ID and token ID; authentication failures invalidate local session/cache; no owner recovery token import |
| Cache | Room primary key includes machine/account/bucket/item; account deletion on logout; no disk artwork cache; Coil loader replaced/shut down at session changes |
| Diagnostics | No network logging interceptor; DTO/session toString redacts credentials; UI exports no raw exception/URL/token; error messages are user-readable |
| Components | Merged debug manifests reviewed: launcher activities are the only unprotected exported components; AndroidX ProfileInstallReceiver is exported with signature-level `android.permission.DUMP`; initialization provider is unexported. No arbitrary deep links; no extra Compose test activity in app APKs; backups and device transfer excluded |
| Permissions | INTERNET, ACCESS_NETWORK_STATE and CHANGE_WIFI_MULTICAST_STATE; no location/storage/camera/contact access; Android future local-network restrictions need a tested target-SDK migration |

Local HTTP consent does not make a hostile LAN safe: traffic is plaintext and a local actor may impersonate discovery. GDM only offers candidates; `/identity` validation and saved machine-ID checks detect accidental mismatches, not cryptographic impersonation. For sensitive accounts use HTTPS even on LAN. Tailscale Serve HTTPS is preferred; native clients do not infer that a `100.x` address proves an encrypted tunnel.

The current username/password UI allows remote typing on Android TV. It never invents short-code pairing routes. A future pairing proposal must use a high-entropy pending secret plus a short display code, five-minute expiry, bounded polling, rate limiting, authenticated approval, explicit server/account/device confirmation, single-use consumption, and cancellation. Approval must issue a narrowly scoped revocable device token, never expose another device's token or password.

## Remaining production gates

- Device tests for certificate failures, DNS rebind attempts, server change detection, logout while a request/player/artwork decode is active, and Keystore invalidation/process death.
- Confirm hardened server configuration on the actual TrueNAS deployment; backward compatibility does not imply safe use of trusted networks.
- Independent review of concurrent account switching and all UI caches. No offline playback/downloads until encrypted storage, ownership and deletion are designed.
- Add server-side pairing only in a separate proposal/PR; add immediate HLS revocation separately if required by the threat model.

No security bypass has been added to make local builds or playback succeed. Development proxy certificates, when needed by the build environment, belong only to the development JDK trust store and are never bundled into the APK.
