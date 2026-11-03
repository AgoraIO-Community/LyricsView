# Current MCC/TME Integration Guide Plan

> Execute inline in the existing workspace. The user approved an external Android integration guide based on the current RTC SDK API. No application behavior change, commit or push is part of this task.

**Goal:** Document complete MCC/YJX and TME catalog, metadata, resource preparation, playback and KaraokeView internal scoring flows.

**Architecture:** Use the Java interfaces in app/libs/agora-rtc-sdk.jar as the API baseline. Keep native MCC typed results separate from TME JSON responses. Identify lyric-library helpers separately from SDK APIs; use API-level examples instead of Demo manager wrappers.

**Tech Stack:** Current local RTC Java/native SDK, Android Kotlin, KaraokeView source, Gradle 7.4/JDK 17.

## Tasks

- [x] Read K歌融合集成文档.md, current TME documentation, SDK signatures and implementation of both flows.
- [x] Confirm that the audience integrates into its own Android application and current RTC API takes precedence over the reference document.
- [x] Create K歌融合接入指南-MCC与TME.md. Cover exact request/callback signatures, both catalog mechanisms, response decoding, song metadata, XML versus LRC/pitch resources, media IDs, playback gating, pitch/progress and score callbacks, teardown and errors.
- [x] Link the new guide from README.md and docs/tme-android-integration-guide.md; retain the original reference document.
- [x] Check method/type names against javap output and source, compile representative Kotlin API examples against the actual JAR and lyric classes, and validate Markdown links/fences and JSON examples.
- [x] Check git diff --check and report the document path plus verification limits. No live-service success claim follows from documentation checks.

## Verification

All 18 Kotlin blocks (432 lines) were extracted from the final guide and compiled together against the actual RTC SDK JAR, current lyric-library classes and Android 34 API with Kotlin language/API target 1.8 and JVM target 1.8. Compilation succeeded; only the documented deprecated YJX methods and the compiler's language-version notice remain. The guide uses mcc.destroyMusicPlayer rather than deprecated player.destroy.

Five JSON examples parse successfully. All eight local file links resolve and all eight table-of-contents fragments match headings; code fences are balanced. git diff --check passes. No live catalog, resource URL, audio playback or microphone scoring verification was performed for this documentation-only task.

The SDK itself marks YJX catalog/search/metadata/lyric methods deprecated, but those methods remain present and are the only fully evidenced YJX query/resource path in this repository. The guide explicitly records this limitation rather than inventing a vendor-1 common-action contract. TME uses sendExtRequest and the exact existing songs/song-info payloads.
