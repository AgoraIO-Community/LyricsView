# Android Lyrics View with RTC Media

## Overview

Display synchronized lyrics in an Android RTC media experience for karaoke, music, or live audio use cases.

Repository: [AgoraIO-Community/LyricsView](https://github.com/AgoraIO-Community/LyricsView)

## Prerequisites and Setup

1. Use the Android toolchain described by the README: compile/target SDK 34, Java 8 compatibility, and NDK 26.1.10909125 or the version configured by the project.
2. Create local `local.properties` entries for `APP_ID`, `APP_CERTIFICATE`, `YSD_APP_ID`, `YSD_APP_KEY` and `YSD_TOKEN_HOST` using your test service configuration.
3. Open the repository in Android Studio, sync and run the `app` sample on a supported device.
4. Load lyrics and optional pitch data, then follow the sample's playback progress and scoring flow. Choose the music integration branch documented in the README if using a different RTC KTV solution.

## What To Check

- Verify lyric highlighting follows playback position and seeking.
- Check pitch/scoring updates with suitable lyric and pitch assets.
- Switch songs and reset the view; confirm old lyric and score state is cleared.

## Integration Notes

The core feature is karaoke lyric display and scoring; a camera preview is not the acceptance criterion. Music service credentials and compatible song rights are separate prerequisites.

Keep App Certificates, customer secrets and privileged token generation on a trusted backend for production. Use only your own test credentials while configuring historical demos; do not commit them. Preserve the sample's existing dependency versions and lifecycle until a separate migration is tested.

## Source References

- [README.md](../../README.md)
- [app/build.gradle](../../app/build.gradle)
- [karaoke_view_ex/build.gradle](../../karaoke_view_ex/build.gradle)

## Validation Status

This recipe was checked against the repository's source and setup documents. The platform build and live service/device flow have not been run as part of this documentation change. Complete the checks above in an environment with the required SDKs, hardware and service access before treating it as a validated deployment.

## Implementation Prompt

```text
You are working in AgoraIO-Community/LyricsView. Use docs/ai/RECIPE.md and its linked source files to adapt the Android Lyrics View with RTC Media reference to this project.

Start by inspecting the actual sample directory and configuration described above. Resolve its listed missing prerequisites before claiming it is runnable. Preserve the existing architecture and compatible dependency versions. Implement the recipe's feature-specific flow, permissions and resource cleanup. Use a trusted backend for privileged credentials and tokens in production.

Run the repository-specific build or launch flow, then perform each What To Check item. Report the commands run, the observed results and anything blocked by service access, hardware or incomplete sample code. Do not claim live validation from a documentation or catalog build alone.
```
