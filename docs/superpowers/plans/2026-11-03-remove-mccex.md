# Remove MCCEx Implementation Plan

> Execute inline with superpowers:executing-plans in the user-selected dev/6.0 workspace. The user has approved the design; no further execution-choice gate is needed.

**Goal:** Remove the MCCEx plugin completely while retaining native MCC/TME playback, lyrics and internal scoring.

**Architecture:** ServiceManager delegates only to the existing MccManager. Both services use the local RTC SDK enum and native mode switch. Legacy persisted service IDs are normalized without a runtime MCCEx service.

**Tech Stack:** Java/Kotlin, Android Gradle Plugin 7.3.1, cached Gradle 8.7/JDK 17, local RTC SDK, JUnit 4 and AndroidX instrumentation.

## 1. Record playback behavior before changing implementation

- [x] Enable default Android stub values for local unit tests in app/build.gradle; no new test dependency is needed.
- [x] Add app/src/test/java/io/agora/examples/karaoke_view_ex/agora/ServiceManagerPlayModeTest.kt. Inject an IAgoraMusicPlayer recording proxy into a real MccManager; exercise ServiceManager.doPlayOriginal for MCC/TME and verify native ACCOMPANY then ORIGINAL, rejection and absent-player state.
- [x] Run `:app:testDebugUnitTest --tests '*ServiceManagerPlayModeTest' --offline --no-daemon --console=plain`; expect assertions to fail because the existing code switches tracks and updates state on failure.

## 2. Remove the plugin and adapt existing boundaries

- [x] app/build.gradle: remove Agora-MccExService and YSD buildConfigField entries.
- [x] Delete app/src/main/java/io/agora/examples/karaoke_view_ex/agora/MccExManager.kt and app/src/main/java/io/agora/examples/utils/Constants.kt (plugin-only constants).
- [x] app/src/main/java/io/agora/examples/karaoke_view_ex/agora/ServiceManager.kt: remove plugin imports, initialization/authentication, callbacks, catalog selection and all MCC_EX branches. Remove external onLineScore callback. Change mode switching to update mIsOriginal only after SDK return 0.
- [x] app/src/main/java/io/agora/examples/karaoke_view_ex/agora/MccManager.java: import `IAgoraMusicPlayer.MusicPlayMode`; implement `public int setPlayMode(MusicPlayMode mode) { if (mAgoraMusicPlayer == null) return -1; int ret = mAgoraMusicPlayer.setPlayMode(mode); Log.i(TAG, "setPlayMode: " + mode + " ret=" + ret); return ret; }`.
- [x] app/src/main/java/io/agora/examples/karaoke_view_ex/agora/RtcManager.kt: replace plugin Utils with SimpleDateFormat, Date, Locale.US and Kotlin's standard random selection, retaining the current channel/file naming formats.
- [x] app/src/main/java/io/agora/examples/utils/ServiceType.kt: retain MCC(0), TME(2). DemoServicePreferences.kt continues normalizing all non-TME values to MCC/XML.
- [x] app/src/main/java/io/agora/examples/utils/LyricsResourcePool.java: remove asMusicListEx and constructors/fields used only by that catalog; retain the three native songs.
- [x] app/src/main/java/io/agora/examples/karaoke_view_ex/MainActivity.java: always use internal scoring for native MCC; remove plugin-only local lyric/cutter branch and external score callback, retaining the lyric library API itself. Simplify ServiceCallback lyric event to the fields used by native MCC.
- [x] Run the mode-switch test again and require all cases to pass.

## 3. Compatibility, current documentation and acceptance

- [x] DemoServicePreferencesInstrumentedTest.kt and DemoNavigationInstrumentedTest.kt: use literal legacy ID 1, preserve migration coverage. Assert plugin class absence rather than inspecting mMccExManager.
- [x] NativeMccPlaybackInstrumentedTest.kt: remove deleted-manager reflection assertion; retain opt-in real playback/internal-scoring checks.
- [x] Update README.md, app/TME_PARSER.md and docs/tme-android-integration-guide.md to describe plugin-free operation and native setPlayMode.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --console=plain` with cached Gradle 8.7/JDK 17.
- [x] Run `bash scripts/tests/run_tme_controller_tests.sh`, `bash scripts/tests/run_tme_tests.sh`, `bash scripts/tests/run_tme_tone_tests.sh`.
- [x] Run `:app:dependencies --configuration debugRuntimeClasspath --offline --no-daemon --console=plain`; require no Agora-MccExService. Inspect APK entries/DEX references for io.agora.mccex and libagora-mccex-service-sdk.so.
- [x] Run `git diff --check` and inspect the final diff for accidental changes. Report fresh results and any absent-device limitation. Keep all changes reviewable on dev/6.0; commit/push only when requested.

## Review fixes and verification results

- Review found and resolved three state inconsistencies: native UI changed its label before a rejected switch, replay reset the mode despite SDK failure, and a fresh TME player did not explicitly select original mode. TME now selects ORIGINAL before initial play; failure prevents play and follows controller cleanup. Resume preserves the selected mode.
- Red regressions reproduced each failure before the fixes. All 9 mode/startup regressions now pass.
- Final Gradle run: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`, offline with Gradle 8.7/JDK 17, BUILD SUCCESSFUL (31s). 32 app unit tests passed: 9 mode/startup, 22 controller, 1 example. Instrumentation APK compiled successfully.
- Existing host suites passed: controller 22, TME parser/request 55, real JNI tone 12. The controller suite overlaps the app tests and is not an additional 22 unique cases.
- Runtime dependency report contains no Agora-MccExService. Final APK `LyricsViewEx-30.0728.1-debug-20261103_122542.apk` contains neither MCCEx DEX packages nor its native library; native RTC and karaoke AI libraries remain.
- Final read-only review found no remaining Critical/Important issue. `git diff --check` passed.
- `adb devices -l` found no connected device. Instrumentation execution, live service playback and audible mode switching remain device acceptance checks.
- Changes remain uncommitted in dev/6.0. The work/ directory and user local.properties were not modified.
