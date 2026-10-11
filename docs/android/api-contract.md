# Verified Sloplex Android API contract

Audit commit: `0aa2e5f0c52f0d30cd38f4e9dadcfe274a184ce2`. The authoritative implementation is `internal/server/{server,types,accounts_api,items,search,playstate,streams,decision,transcode,collections,playlists}.go` with account/library/play-state/transcode stores. This document records existing routes; it does not define new Android-only endpoints.

## Encoding and identity

Send `Accept: application/json`. Otherwise the media API defaults to XML. JSON wraps media responses in `{"MediaContainer":{...}}`, with `Directory`, `Metadata`, `Media`, `Part`, `Stream`, `Genre`, and `Role` using that capitalization. JSON flags are booleans; XML flags are 0/1 attributes. JSON metadata uses `Metadata` for movies, shows, seasons and episodes; XML uses `Video` for movies/episodes and `Directory` for hierarchy entries. Empty `omitempty` arrays/fields are absent. `size` is the returned entry count. `totalSize` and `offset` may be omitted when the request did not trigger pagination.

Account endpoints under `/sloplex/` always use ordinary JSON without a MediaContainer. HTTP errors outside that account API are generally plain text; never assume JSON error bodies. Mutations often return empty 200/204 bodies.

| Route | Auth | Response / important parameters |
|---|---|---|
| `GET /identity` | public | MediaContainer: machineIdentifier, version, size=0; no friendlyName from this handler |
| `GET /` | token/trusted owner | MediaContainer adds friendlyName/platform/platformVersion/updatedAt |
| `POST /sloplex/auth/login` | public | JSON body username/password/device; response `{account:{id,name,admin,libraries,hasPassword,createdAt}, token:{id,device,createdAt,token}}` |
| `POST /sloplex/auth/logout` | issued token | 204; 400 for master-token/trusted-only requests |
| `GET /sloplex/account` | token | caller account object |
| `GET /sloplex/tokens` | token | `{tokens:[{id,device,createdAt}]}`; token values never listed |
| `DELETE /sloplex/tokens/{id}` | own token/admin | 204; admin may revoke any account's token |

`X-Plex-Token` header is preferred. Query tokens also work on the server, but Android never writes them into URLs. Login issues a per-device revocable token. There is no refresh endpoint, expiresAt, automatic expiration or pairing API in this revision. Restoration checks account ID and issued token ID; 401/revocation signs out locally. Changing a password revokes other tokens. Owner account 1 starts with no password and must be configured outside Android before normal login.

## Libraries and metadata

| Route | Meaning |
|---|---|
| `GET /library/sections` | visible section Directory records: key as numeric string, title, type movie/show, uuid; server may include location paths |
| `GET /library/sections/{id}/all` | paged section items, type names or numeric 1/2/3/4; default type is the section's root type; optional `unwatched=1`, `sort=titleSort/year/addedAt/updatedAt/index[:asc/desc]` |
| `GET /library/recentlyAdded` | movies and episodes, addedAt descending, default max 50; section variant also exists |
| `GET /library/metadata/{id}` | one item, full versions/parts/streams and cast |
| `GET /library/metadata/{id}/children` | show→seasons, season→episodes |
| `GET /library/metadata/{id}/allLeaves` | leaf episodes in hierarchy order |
| `GET /search?query=...` | visible movies/shows/episodes; query must be present and ≤200 characters; paged |
| `GET /hubs/search` | grouped search; limit 1–100 and optional sectionId; available but alpha uses flat search |
| `GET /library/sections/{id}/collections` | collection Metadata; same-server collection ID |
| `GET /library/collections/{id}/children` | visible collection members |
| `GET /playlists` | account-owned playlists |
| `GET /playlists/{id}/items` | existing visible leaf items, preserving playlist order |

Lists support `X-Plex-Container-Start` and `X-Plex-Container-Size` in headers or query, query taking precedence. Invalid/negative values fall back to defaults. Item IDs (`ratingKey`) are strings in HTTP JSON even though internal IDs are integers. Media/part/stream IDs and section IDs are integers. Duration/viewOffset are milliseconds; addedAt/updatedAt/lastViewedAt are Unix seconds. No rating, genre or cast should be fabricated when absent.

Detail example, constructed from the verified serializer:

```json
{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"7","key":"/library/metadata/7","type":"movie","title":"Example","duration":180000,"viewOffset":90125,"Media":[{"id":1,"videoCodec":"h264","audioCodec":"aac","container":"mp4","Part":[{"id":2,"key":"/library/parts/2/1700000000/file.mp4","Stream":[{"id":3,"streamType":2,"codec":"aac","displayTitle":"English (AAC Stereo)","selected":true}]}]}]}]}}
```

Section filtering applies before pagination. Hidden items return 404 rather than 403 to avoid existence disclosure. Playback must re-fetch metadata and authorize streaming; cached library access is never authority.

## Streaming and conversion

| Route | Parameters / behavior |
|---|---|
| `GET /library/parts/{id}/{ts}/{file}` | token and library authorization; `http.ServeContent` handles Range/206, Content-Range, seeking; use the exact returned key |
| `GET /video/:/transcode/universal/decision` | path `/library/metadata/{id}`; mediaIndex/partIndex; protocol=hls/http/webm; directPlay/directStream=0/1; containers/videoCodecs/audioCodecs comma-separated; maxAudioChannels; hdr=0/1; videoResolution WIDTHxHEIGHT; maxVideoBitrate kbps; audioStreamID/subtitleStreamID; subtitles auto/burn/none |
| `GET /video/:/transcode/universal/start.m3u8` | same targeting/limits; session is a client label; offset is nonnegative **seconds**; abr=0/1; returns master playlist with relative session/rendition URLs |
| `PUT /video/:/transcode/universal/ping?session=label` | refreshes sessions belonging to the caller and label; empty 200 |
| `PUT /video/:/transcode/universal/stop?session=label` | stops sessions belonging to caller and label; empty 200 |
| `GET /video/:/transcode/universal/session/{sid}/{level}/{file}` | public bearer capability from random sid; base/index.m3u8 and numbered TS segments |
| `GET /video/:/transcode/universal/session/{sid}/subs/{stream}/{file}` | VTT subtitle rendition playlist/document; timing adjusted by source offset and MPEG-TS timestamp map |

General decision codes: 1000 directplay, 1001 conversion available, 2000 neither. DirectPlayDecisionCode=3000 when incompatible; TranscodeDecisionCode=4000 when unavailable. The decision response describes converted output, not a ready playback URL: converted part.key is empty. HLS start must be constructed from verified parameters. Decision checks availability, not admission capacity. Start 503 with Retry-After=10 means capacity exceeded; 503 without it means ffmpeg unavailable. Default maxTranscodes=2. Idle HLS sessions expire after about two minutes; requests/ping refresh activity. Random sid differs from the client session label.

The source may be copied when HLS is requested; `directStream=0` prevents copying an unsupported source video codec. HDR conversion supports server tone mapping; the alpha conservatively advertises hdr=0. Container support alone is insufficient. Multi-part source concatenation is not implemented by the Android alpha.

## Tracks and watch state

| Route | Parameters / response |
|---|---|
| `PUT /library/parts/{id}` | query audioStreamID/subtitleStreamID; per-account selection; subtitleStreamID=0 means off; empty 200 |
| `GET /library/streams/{id}` | text subtitles only; format=vtt/srt/ass; optional offset in seconds; image subtitles require burn-in |
| `PUT /:/timeline` | ratingKey or key, time **ms**, duration **ms**, state; empty 200; state accepted but ignored |
| `PUT /:/progress` | time required, same completion rule; available but Android uses timeline |
| `PUT /:/scrobble` / `PUT /:/unscrobble` | mark watched/unwatched; shows/seasons cascade; empty 200 |
| `GET /library/onDeck` | per-account in-progress leaf items plus next non-special episode after most recently watched episode; paged |

Tracks only occur in detail responses. streamType=1 video, 2 audio, 3 subtitles. Index is the ffprobe index, absent for sidecars. Text sub key points to /library/streams/{id}; image tracks have no fetch key. JSON flags selected/default/forced/hearingImpaired are real booleans. LanguageCode is ISO 639-2/B, not necessarily a BCP47 tag. HLS WebVTT includes proper timestamp mapping. Subtitle track changes that require burn-in restart HLS at the current source position.

Timeline marks completion at 90% of duration. Repeated completion updates usually count once; time≤0 is a no-op. Updates are serialized by the server store and last-arriving update wins; there is no client timestamp, ETag or compare-and-swap conflict control. Concurrent clients can overwrite one another's resume position. Android reports about every 10 seconds and on pause/seek/stop/end, does not blindly retry scrobble (which increments the counter), and does not resurrect an offline stale progress queue after reconnect.

## Mismatches and gaps

- The master prompt's `internal/lan` location does not exist; implementation/spec both use GDM.
- `/identity` has no friendlyName or capability negotiation, and versions can be development strings; do not invent a minimum semver. Validate required fields and feature responses.
- Auth JSON and MediaContainer JSON are different shapes. Specs describe that distinction; treating every response as wrapped would fail.
- Specs and handlers agree that trusted-network **invalid** tokens fall back to owner, a security risk rather than a documentation mismatch.
- The decision endpoint can report conversion available while a later start encounters capacity. Specs do not offer reservation.
- Playback state `state` is ignored despite the familiar timeline parameter name. There is no live sessions/history endpoint for ordinary clients.
- The API has no pairing, recommendations service, download API or refresh-token lifecycle. Recommendations/recent history must be derived from actual local data or omitted.
- Device codec/profile/level, HDR output/display suitability, selected-output playback and multi-part UX need physical playback verification. Documentation cannot certify them.

No other wire-format disagreement was found in the inspected critical handlers. This is a source audit, not a claim that every documented server option has been exercised on a live installation.
