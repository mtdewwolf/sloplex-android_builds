# Compatibility and validation matrix

Backend audit pinned to `0aa2e5f0c52f0d30cd38f4e9dadcfe274a184ce2`. Minimum Android API 26; compile/target 35. Development JDK 17. Test claims below must be updated from actual command results before final delivery.

| Check | Evidence/status |
|---|---|
| Original Go backend race suite | Passed `go test -race ./...` before Android/backend changes |
| Opt-in trusted-token hardening | Passed Go race suite and targeted strict/legacy auth tests; default fallback preserved |
| Android model/network unit tests | Pending final build result |
| Mobile debug APK | Pending final build result |
| TV debug APK | Pending final build result |
| Android lint | Pending final result |
| Mobile emulator UI/launch | Pending; current environment has no `/dev/kvm`, software emulation attempted separately |
| TV emulator UI/launch | Pending; current environment has no `/dev/kvm`, software emulation attempted separately |
| Physical phone/tablet/foldable/TV | Not tested |

| Media case | Intended behavior | Verification required |
|---|---|---|
| H.264 + AAC MP4 | direct when decoder/size/track limits fit | byte-range seek, start/resume, progress |
| H.264 MKV | Media3 Matroska direct when codecs fit | range/seek and selected embedded audio |
| HEVC | direct only when installed decoder advertises size; HLS H.264 otherwise | profile/level and real decoder playback |
| HDR10 / HLG | hdr=0 requests server SDR tone mapping | physical display/decoder and server ffmpeg matrix |
| AAC stereo | direct when decoder available | audio focus and noisy interruption |
| AC3 / DTS / TrueHD / multichannel | conservative stereo AAC HLS conversion | actual server decisions/output and audio routing |
| Sidecar SRT/WebVTT/ASS/SSA | direct fetch as VTT; HLS server subtitle renditions | switch during playback and seek timing |
| Embedded text subtitles | server VTT source or HLS rendition | selection mapping and duplicate language labels |
| PGS/VobSub/image subtitles | server burn-in HLS | restarted source offset and subtitle timing |
| Capacity exhausted | understandable 503/Retry-After error | two live server sessions plus third attempt |
| Multi-part title | explicit alpha error | future timeline/concatenation implementation |

Decoder capability queries are an input to decision logic, not a guarantee that every file will decode. Missing source codec information must fail toward conversion, and physical playback tests must cover profile/level/container combinations. Current alpha does not certify device HDR direct output or multichannel passthrough.
