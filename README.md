# Sloplex Android auth and playback core

PR 3 adds Keystore device-token storage, validated sessions, account-partitioned Room metadata caching, shared Media3 direct/HLS playback, source timeline offsets, progress reporting and stream/session management. Mobile and TV browsing/player UI follow in PR 4.

The device instrumentation tests exercise cache isolation, encrypted tokens, revocation, authenticated MP4 ranges, subtitles and HLS timeline logic. See `docs/android/compatibility-matrix.md` for actual results and physical hardware release gates.
