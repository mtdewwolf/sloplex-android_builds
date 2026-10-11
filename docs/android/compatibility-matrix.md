# Compatibility and validation matrix

Backend audit pinned to `0aa2e5f0c52f0d30cd38f4e9dadcfe274a184ce2`. Minimum Android API 26; compile/target 35. Development JDK 17. Evidence recorded 2026-10-10; these are alpha validation results, not device certification.

| Check | Evidence/status |
|---|---|
| Original Go backend race suite | Passed `go test -race ./...` before Android/backend changes |
| Opt-in trusted-token hardening | Passed Go race suite and targeted strict/legacy auth tests; default fallback preserved |
| Android model/network unit tests | Passed 21 tests with the actual Go server runner: 7 timeline, 13 MockWebServer contracts, 1 real-server integration |
| Mobile debug APK | Compiled, including final security/lifecycle changes |
| TV debug APK | Compiled, including final security/lifecycle changes |
| Mobile and TV instrumentation APKs | Compiled; tests have not executed successfully in this environment |
| Android lint | Passed both apps, no errors; warnings described in validation report |
| Optimized unsigned release APKs | Compiled for both apps; signing and runtime acceptance pending |
| Mobile emulator UI/launch | Unverified: software emulator booted; APK installation failed in package service with `Broken pipe`; no `/dev/kvm` |
| TV emulator UI/launch | Unverified: Android TV software emulator booted; APK installation hung and was stopped; no `/dev/kvm` |
| Physical phone/tablet/foldable/TV | Not tested |

| Media case | Intended behavior | Verification required |
|---|---|---|
| H.264 + AAC MP4 | direct when decoder/size/track limits fit | Real Go server authenticated 206 byte ranges and direct decision passed; Media3 playback/seek remains unverified |
| H.264 MKV | Media3 Matroska direct when codecs fit | range/seek and selected embedded audio |
| HEVC | direct only when installed decoder advertises size; HLS H.264 otherwise | profile/level and real decoder playback |
| HDR10 / HLG | hdr=0 requests server SDR tone mapping | physical display/decoder and server ffmpeg matrix |
| AAC stereo | direct when decoder available | audio focus and noisy interruption |
| AC3 / E-AC3 / Opus / FLAC, multichannel | direct when a decoder or passthrough output is available, otherwise HLS with copied H.264 video and stereo AAC | actual server decisions/output, downmix and audio routing |
| DTS / TrueHD | HLS with copied H.264 video and stereo AAC | actual server decisions/output and audio routing |
| Sidecar SRT/WebVTT/ASS/SSA | direct fetch as VTT; HLS server subtitle renditions | switch during playback and seek timing |
| Embedded text subtitles | server VTT source or HLS rendition | selection mapping and duplicate language labels |
| PGS/VobSub/image subtitles | server burn-in HLS | restarted source offset and subtitle timing |
| Capacity exhausted | understandable 503/Retry-After error | two live server sessions plus third attempt |
| Multi-part title | explicit alpha error | future timeline/concatenation implementation |

Decoder capability queries are an input to decision logic, not a guarantee that every file will decode. Missing source codec information must fail toward conversion, and physical playback tests must cover profile/level/container combinations. Current alpha does not certify device HDR direct output or multichannel passthrough.

Generated H.264/AAC MP4, HLS/TS and VTT fixtures and instrumentation checks are included. Compiling those tests is not evidence of decoder playback. The GitHub workflow requests hardware-accelerated phone and actual Android TV images, but has not run because the Android repository is still empty. See [validation report](validation-report.md).
