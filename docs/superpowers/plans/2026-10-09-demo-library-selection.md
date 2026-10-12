# Demo Library Selection Implementation Plan

**Goal:** Launch into a TME/音集协 selector, route each explicit choice to the existing Demo path, and verify the updated APK on the connected phone.

**Architecture:** A launcher Activity owns navigation only. MainActivity applies the selected service before SDK initialization, preserving the existing TME preparation/controller and MCC screen. Existing teardown returns to the launcher without retaining an old SDK session.

**Tech Stack:** XML, ViewBinding, Kotlin/Java, existing Material theme, Gradle 8.7/AGP 7.3.1, JUnit/Espresso.

Work in the user-selected LyricsView checkout and preserve all existing uncommitted TME work. Execute inline; do not commit or push unrelated work.

## 1. Navigation regression tests

- [x] Add `app/src/androidTest/java/io/agora/examples/karaoke_view_ex/LibrarySelectionInstrumentedTest.kt`. Resolve the actual package launcher, assert it is LibrarySelectionActivity, and check RTC is absent before choosing.
- [x] Exercise TME and MCC with the opposite saved preference. Assert TME list/legacy controls visibility, selected service, return to selector, and SDK release. Use string labels so the regression tests compile before new resources exist.
- [x] Build the test APK with cached Gradle 8.7 and run the launcher test against the current app; expect an assertion failure identifying MainActivity as the old launcher.

## 2. Launcher and early service selection

- [x] Add `LibrarySelectionActivity.kt` and `activity_library_selection.xml`, with two buttons navigating through `Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SERVICE_TYPE, service.type)`.
- [x] Move the MAIN/LAUNCHER filter from MainActivity to LibrarySelectionActivity. Keep MainActivity internal.
- [x] In MainActivity, apply a valid explicit TME/MCC selection only when `savedInstanceState == null`, persist its corresponding LRC/XML preference, and resolve preferences before `initRtc()` / `initService()`.
- [x] Preserve current settings/recreation behavior and title the current screen using localized resources. Update `app/TME_PARSER.md` to describe the new launcher.

## 3. Verification and installation

- [x] Run `:app:assembleDebug :app:testDebugUnitTest :karaoke_view_ex:testDebugUnitTest :app:assembleDebugAndroidTest --offline --console=plain` with cached Gradle 8.7.
- [x] Install target and test APKs, run LibrarySelectionInstrumentedTest and existing TmeSingingScreenInstrumentedTest. Check actual launcher, both paths and return navigation.
- [x] Review the scoped changes, run `git diff --check`, verify APK signature/ABI, and leave the updated Demo on the phone's selection page. Report artifact path and any live-network verification gap.

## Results (2026-10-09)

- Launcher regression failed on the previous installed APK because its launcher was MainActivity, then passed on the new APK.
- Instrumentation build initially failed on 15 legacy `ScoringMachine.setPitch(pitch, position)` calls. Adapted them to the current `setPitch(pitch, 0F, position)` signature; scoring implementation is unchanged.
- Independent review found rapid taps could enqueue duplicate MainActivity screens sharing the SDK. Reproduced on the phone, added a one-navigation guard reset on selector resume, and verified the regression passes. Follow-up review found no remaining important issues.
- 77 existing unit tests passed (zero failures/errors/skips). Eleven phone instrumentation tests passed: five launcher/navigation/recreation tests, three singing-screen tests, one main-thread parser test, and two tone/parser/JNI compatibility tests.
- Final APK: `app/build/outputs/apk/debug/LyricsViewEx-30.0728.1-debug-20261009_153527.apk`; arm64-v8a, APK signature verified, update installed successfully on device `3NU9K24C14000169`.
- Real online song playback and live microphone singing were not exercised by these simulated preparation/player screen tests. No claim of live TME service or end-to-end singing validation.
