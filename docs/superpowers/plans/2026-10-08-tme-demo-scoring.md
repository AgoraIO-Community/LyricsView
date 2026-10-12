# Android TME Demo Scoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Follow the approved design in `docs/superpowers/specs/2026-10-08-tme-demo-scoring-design.md`.

**Goal:** Provide song selection and an independent Android TME singing screen, using reusable public scoring preparation and internal lyric scoring.

**Architecture:** `TMEScoringPreparation` owns song-info requests, cancellable parallel downloads and background parsing. A SDK-independent `TmePlaybackController` owns one session and the media/preparation/UI gate. ServiceManager and MccManager retain RTC/MCC ownership and isolate each session's player. The singing Activity applies the model and acknowledges readiness before playback.

**Tech Stack:** Kotlin 1.8, Java, XML/ViewBinding, existing TMEParser and KaraokeView, HttpURLConnection (library minSdk 17), JUnit 4, Gradle 8.7/AGP 7.3.1.

The user selected the current LyricsView checkout on dev/5.0; work in place, preserve unrelated changes, and do not push remotely.

## Task 1: Public preparation component

Files: `karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme/TMEScoringPreparation.kt`, `TMEScoringPreparationModels.kt`, `TMEResourceDownloader.kt`; corresponding tests under `karaoke_view_ex/src/test/.../tme/`; host runner where necessary.

- [x] Write failing tests for request JSON, exact matching songId/LRC, parallel resources, real parser delivery, errors, cancellation, deadlines, duplicate responses and reentrancy. Verify expected RED.
- [x] Implement `prepare(songId, sender)`, `handleExtResponse(original four parameters)`, `cancel`, and idempotent `close`. Use independent request manager, strongly retained delegate, main-thread callbacks and generation checks.
- [x] Define listener stages/result/error types. Result exposes model and scorableLineCount, never raw error messages. Default downloader streams into UUID-owned directories, checks cancellation, closes/disconnects, atomically commits files, and cleans only owned files after writers finish. Do not close injected shared downloader.
- [x] Parse in background via parseTMEToneData, exact HTTP(S) resource validation, preserve signed URL strings, main-thread notifications. Deadlines: query 30s/download 60s/overall 120s with injectable scheduler.
- [x] Run new tests GREEN and existing response/tone tests. Spec review then code-quality review; fix findings.

## Task 2: Session controller

Files: `app/src/main/java/io/agora/examples/karaoke_view_ex/agora/tme/TmePlaybackController.kt`, corresponding SDK-free JUnit tests and host runner.

- [x] Write failing tests for media-first/data-first, page acknowledgement, duplicate events, degradation, stale session, pause/resume/background, timeout and ordered pitch input. Verify expected RED.
- [x] Implement immutable state snapshots and injected media/preparation/timer ports. Maintain original string ID separately from SDK songCode, preload identity, terminal generation, one initial play and per-page foreground/acknowledgement.
- [x] Preparation success requires modelApplied; failure requires noScoringUIApplied. Set playSubmitted before play. Wait for PLAYING before inputs. Preload/open/play failures fail media rather than degrade. Deadlines: 120s/30s/15s.
- [x] Read actual player position per 20ms tick and before local pitch. Deliver progress before pitch. Preserve natural line callbacks/final scores, reject external scoring, no synthetic final sample.
- [x] Identity-safe attach/detach, background pause and explicit resume, terminal cleanup and retained final result. Run GREEN and review.

## Task 3: SDK ownership and UI integration

Files: `app/.../agora/MccManager.java`, `ServiceManager.kt`, `MainActivity.java`, `TmeSingingActivity.kt`, `app/src/main/res/layout/activity_main.xml`, `activity_tme_singing.xml`, manifest and strings as needed.

- [x] Connect MCC ext requests with original arguments to preparation then independent list manager. Always asynchronously post SDK callbacks to main, and compare request ID + songCode + session for preload. Create/register/destroy a distinct player/observer for each TME song. Remove URL/body logging.
- [x] ServiceManager owns one controller, separate song list and singing observers. Keep existing MCC/MCC_EX behavior. Parent Activity forwards local microphone to current TME controller independently of its old UI state and retains SDK during child screen.
- [x] Add TME song list with retry and immediate navigation. Singing screen uses XML and ViewBinding, installs each model once before acknowledging, hides whole scoring area on degradation before acknowledging, shows independent resource stages, position, line/cumulative/full scores, pause/resume and track toggle.
- [x] Implement onStart/onStop subscriptions, background pause, return cancellation/reset, common rotation retaining view, other recreation interruption with restart. No seek/settings or new scoring algorithm.
- [x] Compile actual SDK if locally available. If absent, report the specific missing prerequisite; never claim a substitute SDK proves TME compatibility. Add useful UI wiring verification without pretending to have run instrumentation.

## Task 4: Documentation and verification

Files: `app/TME_PARSER.md`, `karaoke_view_ex/TME_TONE_DATA.md`, this plan and approved spec.

- [x] Document SDK-owned minimal public preparation wiring, UI acknowledgement, internal scoring progress-before-pitch, cancel/close, limits and local SDK/Gradle requirements. Maven 3.0.4 lacks the new API.
- [x] Run library AAR build/unit tests using cached Gradle 8.7 with configure-on-demand; existing response and real tone/JNI regressions; new controller tests; git diff --check.
- [x] Perform independent spec compliance review followed by code-quality review and address findings. Record exact results and any device/instrumentation environment gaps. Leave changes reviewable in the selected workspace without a remote push.

## Verification results (2026-10-08)

- Public library `assembleDebug` and `testDebugUnitTest` using cached Gradle 8.7: BUILD SUCCESSFUL; 57 tests, zero failures/errors. The AAR includes the new preparation API.
- Host TME response/preparation/downloader suite: 50 passed. It uses the real TMEParser and real Java file parser/model; the static host KaraokeView facade does not prove Android View behavior.
- Host session-controller suite: 19 passed, including expected RED/GREEN regressions for reentrant notification/control operations and real microphone zero-pitch silence.
- Real tone/JNI regression suite: 11 passed. Full-score fixture distinguishes 2 scoring lines from 6 display lines.
- All Demo XML/resources compiled with SDK aapt2; git diff --check passed. Three new page instrumentation tests cover scoring UI before play, degradation/independent downloads, pause/resume and interrupted recreation; these are written and reviewed but not executed.
- Independent spec and code-quality reviews for public preparation, controller and Demo integration passed after fixes; final cross-component review found no additional blocking findings.
- Actual Demo APK build is blocked at app/build.gradle:10: missing app/libs/agora-rtc-sdk.jar. Matching arm64 native libraries are also absent. Searches of workspace, Downloads, local SDK source/build directories and Spotlight did not find the required package. The available old SDK is incompatible with the baseline TME APIs and was not substituted.
- Consequently actual SDK compilation, page instrumentation execution, phone playback/scoring, and real network degradation remain unverified. These require the matching local SDK package; live singing also requires valid credentials/device. No SDK binaries, credentials or APK added to Git; no remote push performed.
