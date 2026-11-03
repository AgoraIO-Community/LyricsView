# Native MCC Entry Implementation Plan

> Execute inline in the current authorized workspace. Preserve existing navigation changes and local RTC binaries. Use verification-before-completion before reporting results.

**Goal:** Route the Android 音集协 UI through native MCC/XML with the four configured native songs, retaining MCCEx compatibility and TME behavior.

**Architecture:** Keep ServiceManager's existing MCC playback branch. Change preference normalization and UI dispatch, and verify the actual native resource/playback path on the connected phone.

**Tech Stack:** Android Java/Kotlin, local RTC SDK, KaraokeView, Gradle 8.7/JDK 17, Espresso/ADB.

## 1. Migration regression

- [x] Add preference regression cases for absent, MCC_EX, MCC, invalid, and TME settings; assert MCC/XML for non-TME and TME/LRC for TME.
- [x] Build debug and instrumentation APKs using cached Gradle 8.7, install with `adb -s 3NU9K24C14000169 install -r`, and execute the preference class before changing production code. Confirm expected MCC-vs-MCC_EX assertion failure.

## 2. Entry and song dispatch

- [x] Change DemoServicePreferences normalization to MCC/XML and ServiceManager's default to MCC.
- [x] Add guarded `selectMccSong(position)` using `asMusicList().indices`; retain `selectMccExSong`.
- [x] Rename the sheet host callback to `onMccSongSelected`, update MainActivity and TmeSingingActivity, and list `asMusicList()` rows.
- [x] Change the settings radio ID to radio_mcc and display 音集协 through the native MCC string; persist MCC/XML and enable XML only for MCC, LRC only for TME, and never KRC in this UI.
- [x] Set MainActivity's initial/internal-scoring state and lyric fallback to native MCC/XML; reapply internal scoring when the service changes to MCC.
- [x] Update navigation regression expectations, migration direction, selected native row, and settings assertions. Update current Demo docs.

## 3. Automated checks

- [x] Run preference regressions and DemoNavigationInstrumentedTest on the phone, alongside TME unit/screen checks appropriate to changed navigation.
- [x] Resolve failures caused by the native entry; retain parser/scoring algorithms and existing TME semantics.

## 4. Actual phone verification

- [x] Launch the real app on FIN_AL60a. Record runtime service, XML selection, native song codes, preload/getLyric/open/play callbacks, and resource results for all four configured songs.
- [x] Exercise pause/resume, lyric drag, original/accompaniment switch, and live microphone pitch/internal scoring. Use actual callbacks/data; any synthetic input must be reported separately from live microphone validation.
- [x] Record precise server/authorization/resource blockers if any; do not substitute MCCEx or invented success.
- [x] Save concise evidence under `work/verification/native-mcc-20261021/`, update the design status, and run `git diff --check`.

Result: build, 20 unit tests and 12 device regressions passed. The three playable songs passed the stricter live acceptance run (44.524s). The fourth song is blocked by native preload status=1/errorCode=3 on both attempts. Internal scoring callbacks ran with score 0; no claim of validated human singing accuracy. Read-only code review found no blocking regression in this scoped change. See [verification evidence](../../../work/verification/native-mcc-20261021/README.md).

Follow-up: the user requested removing the fourth song. Removed it from the native resource list and live test class, changed the existing sheet regression to expect the three retained titles explicitly, and updated current Demo documentation. Rebuilt and installed the app; direct device UI inspection confirmed exactly those three titles.
