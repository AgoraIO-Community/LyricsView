# AgoraLyricsScore Rename Implementation Plan

> **For agentic workers:** Execute this approved mechanical migration inline on
> the existing `dev/6.1` branch, following the steps below.

**Goal:** Rename the library, packages, publication and native binary consistently
to AgoraLyricsScore while preserving the lyrics/scoring/playback behavior.

**Architecture:** Keep the library and app module boundaries and functional API
class names. Update every live dependency on their identifiers in one migration;
use existing integration tests and inspect built artifacts to validate it.

**Tech Stack:** Android Gradle 7.3.1, Java/Kotlin, CMake/JNI, Maven publishing.

## Task 1: Record and verify the baseline

- [x] Verify `git branch --show-current` is `dev/6.1` and the worktree is clean.
- [x] Record all names and scope in the adjacent rename design.
- [x] Run `bash scripts/tests/run_tme_tests.sh`,
  `bash scripts/tests/run_tme_tone_tests.sh`, and
  `bash scripts/tests/run_tme_controller_tests.sh`; expect no failures.

## Task 2: Migrate implementation and build configuration

- [x] Move `karaoke_view_ex` to `AgoraLyricsScore` and change
  `settings.gradle` to `rootProject.name = "AgoraLyricsScore"` and
  `include ':AgoraLyricsScore'`.
- [x] Change library source/test/stub packages and directories from
  `io.agora.karaoke_view_ex` to `io.agora.lyricsscore`; change Demo
  source/test packages to `io.agora.examples.lyricsscore`.
- [x] Update library/app Gradle namespaces, Demo application ID and local module
  dependency; set `mavenArtifactId=AgoraLyricsScore` and use
  `implementation "${mavenGroupId}:${mavenArtifactId}:${sdkVersion}"` for SDK mode.
- [x] Change the local AAR filename to `AgoraLyricsScore_v{version}.aar` and
  publication description to `AgoraLyricsScore Android lyrics and scoring SDK`.
- [x] Rename JNI source to
  `io_agora_lyricsscore_internal_ai_AIAlgorithmScoreNative.cpp`, update exported
  `Java_io_agora_lyricsscore_internal_ai_AIAlgorithmScoreNative_*` symbols,
  and use native target/loadLibrary `agora-lyrics-score`.
- [x] Update XML custom view names, Demo label/theme, logging labels and the
  source/class/native-library paths in all three host test scripts.

## Task 3: Update current integration documentation

- [x] Update `README.md`, `K歌融合接入指南-MCC与TME.md`,
  `K歌融合集成文档.md`, `docs/tme-android-integration-guide.md`,
  `app/TME_PARSER.md`, and `AgoraLyricsScore/TME_TONE_DATA.md`.
- [x] Add `docs/agora-lyrics-score-migration.md` with old/new coordinates,
  package/XML/native updates, Demo app ID impact, build commands and historical
  document scope. Link it from README.
- [x] Search active files for old module/package/publication/native names;
  only migration examples and historical documents may retain them.

## Task 4: Verify and review

- [x] Re-run all three host scripts; expect the baseline test counts to pass.
- [x] Run `./gradlew :AgoraLyricsScore:assembleRelease
  :AgoraLyricsScore:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
  :app:assembleDebugAndroidTest :AgoraLyricsScore:generatePomFileForReleasePublication`.
- [x] Inspect the AAR and APK for new packages, native library/symbols and
  application ID; inspect the generated POM for the new artifactId.
- [x] Validate SDK-mode compilation against the locally generated publication
  without uploading it to any remote repository.
- [x] Run `git diff --check`, inspect the final diff and report results and any
  environmental limits without claiming device verification.

## Verification results

- Host suites: parser/resource tests 55 passed, tone/JNI tests 12 passed,
  playback controller tests 22 passed.
- Gradle unit tests: library 62 passed, Demo 32 passed; no failures or skips.
- Release AAR, Demo debug APK and instrumentation APK built successfully.
  Demo compilation and unit tests also passed with the generated AAR consumed
  through a temporary local Maven repository in SDK mode.
- Inspected the release AAR, generated POM, Demo APK and native exports:
  new packages and application ID are present, all four ABIs contain
  `libagora-lyrics-score.so`, and all four JNI methods use the new package prefix.
- Active source/build/test files contain no old identifiers; final diff review
  and `git diff --check` passed. Renamed files remain unstaged in the worktree.
- Device tests were not executed. The new Maven coordinate has not been
  published to a remote repository.
