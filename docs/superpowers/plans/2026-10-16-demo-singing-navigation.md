# Demo Singing Navigation Implementation Plan

> **For agentic workers:** Execute inline using superpowers:executing-plans. User approved the interactive design and implementation; work in the explicitly selected LyricsView checkout.

**Goal:** Launch into singing and select songs through a bottom sheet occupying 80% of the screen, with TME and MccEx (displayed as 音集协).

**Architecture:** MainActivity owns the existing RTC/service lifetime and MccEx UI. Extract the TME Activity presentation into TmeSingingFragment without changing TmePlaybackController. A shared SongSelectionSheet reports the selected row to its host and owns the TME list listener only while visible.

**Tech Stack:** Android XML/ViewBinding, Kotlin/Java, existing Material BottomSheetDialogFragment, JUnit/Espresso, cached Gradle 8.7.

## 1. Regression coverage

- [x] Replace obsolete LibrarySelectionInstrumentedTest with DemoNavigationInstrumentedTest. Launch through the real launcher and assert MainActivity, MCC_EX default, title 音集协演唱. Exercise the existing 设置 control, choose TME, assert the empty TME singing view, then open 选歌.
- [x] Verify dialog title, absence of 设置 inside dialog, height ratio against display metrics, closing without altering session and clicking the second configured 音集协 row selects its actual list position (resource index values are all zero).
- [x] Build test APK and run the launcher regression against unchanged app; expect failure because the existing launcher is LibrarySelectionActivity.

## 2. Service defaults and shared navigation

- [x] Add DemoServicePreferences with normalization `stored == TME.type ? TME : MCC_EX`, retaining valid settings and repairing missing/legacy service-format pairs.
- [x] MainActivity becomes launcher; remove LibrarySelectionActivity and layout. Add navigation menu 选歌/设置. SettingsActivity uses an action bar and only two service choices, labels MCC_EX 音集协, and always returns updated preferences even with system Back.
- [x] Add SongSelectionSheet and layout: `height = (fullScreenHeight * 0.8f).toInt()`, expanded Material bottom sheet with close/back/outside dismissal. Keep ArrayAdapter/simple_list_item_1, TME loading/retry/listener lifecycle, asMusicListEx titles for MCC_EX.
- [x] Add ServiceManager.selectMccExSong(position) with service and range validation; MainActivity routes a selected row through the existing stop/play path.

## 3. TME presentation reuse

- [x] Extract presentation into TmeSingingFragment; preserve installed-model-before-acknowledgement, score callbacks, independent resource states, foreground attach/detach and interruption behavior. Empty session shows 请选择歌曲; selecting a song creates the normal session without leaving the Activity.
- [x] Embed it in MainActivity only for TME; remove it before destroying/changing service. Keep TmeSingingActivity as a thin compatibility host for existing scoring-screen tests. Remove obsolete body navigation controls.
- [x] Preserve Fragment and dialogs on rotation by handling orientation/screenSize in MainActivity. Other recreation ends the prior TME session and shows interruption as before. Recalculate sheet height on configuration changes.

## 4. Verification and documentation

- [x] Run `gradle :app:assembleDebug :app:testDebugUnitTest :karaoke_view_ex:testDebugUnitTest :app:assembleDebugAndroidTest --offline --console=plain` using cached Gradle 8.7 and Java 17.
- [x] Install build/test APKs on the available emulator; run navigation and existing TME screen/parser tests. Inspect real screenshots and check list dismissal/settings return/recreation.
- [x] Update current Demo integration docs and record exact test results, artifact paths and limits. Review diff and run git diff --check. Leave changes in the current checkout without commit/push.

## Verification record — 2026-10-16

- Gradle 8.7 / Java 17: BUILD SUCCESSFUL; 77 unit tests across 9 suites, zero failures/errors/skips.
- Pixel_3a_API_33_arm64-v8a (`emulator-5554`): OK (14 tests): 6 navigation, 5 singing-screen, 1 parser delivery, 2 tone/legacy parser entry regressions.
- Confirmed regressions before fixes: old launcher still opened LibrarySelectionActivity; cold TME called applyPreferences before fragment view creation; rotating an open sheet retained height 1776px instead of landscape 864px. Final runs pass all three.
- Defaulting to MCC_EX exposed an existing missing-config crash: empty YSD_TOKEN_HOST generated an invalid URL. Added an early configuration check and prompt; valid host initialization remains on the existing path.
- Independent read-only review found the fragment timing problem and approved its fix and configuration-change resizing. No outstanding actionable findings.
- Visual inspection additionally reproduced old TME callback text/progress remaining after a service change. The service-change branch now clears these two displays; the existing settings regression asserts that both are empty after switching to 音集协.
- APK: `app/build/outputs/apk/debug/LyricsViewEx-30.0728.1-debug-20261016_180119.apk`.
- Build/test logs and real screenshots: `work/verification/demo-navigation/`.
- No XML/音集协 parser, scoring library or TmePlaybackController source changed. No commit/push.

This environment lacks the configured 音集协 token-service URL. Selection/navigation are verified on the emulator; TME singing-screen regressions use fake playback/preparation callbacks. These checks do not establish successful live vendor downloads or real microphone scoring.
