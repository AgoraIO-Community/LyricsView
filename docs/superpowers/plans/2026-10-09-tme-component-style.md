# TME component style implementation plan

> Execute inline in the authorized LyricsView workspace, preserving existing changes.

**Goal:** Match TME lyric and scoring component appearance to the existing MCC page.

**Architecture:** Share XML view styles and the existing appearance preference application code. Keep each page's playback and drag handling unchanged.

**Tech Stack:** Android XML, Java, Kotlin, Gradle, ADB.

- [x] Move the visual attributes of activity_main.xml into KaraokeScoringView and KaraokeLyricsView styles, and reference both styles from activity_tme_singing.xml.
- [x] Extract MainActivity's display preference setters into KaraokeViewStyle.apply(context, lyrics, scoring). Retain MainActivity's drag setting separately and call the shared helper during TmeSingingActivity creation.
- [x] Keep TME header and controls padded while giving the lyric/scoring region full page width. Keep score/status IDs and visibility logic intact.
- [x] Build using cached Gradle 8.7: :app:assembleDebug :app:assembleDebugAndroidTest --offline. Run existing TmeSingingScreenInstrumentedTest to verify scored/unscored play and interrupted sessions.
- [x] Install the new APK on connected phone; open a TME song and inspect orange scoring area, purple lyric area, component heights, text and controls. Report observed results and APK path.

No new tests that merely duplicate XML attributes are needed for this reversible display change. Existing playback tests and actual device rendering verify the affected integration.

Verification: Gradle build succeeded; APK signature verification succeeded; all shared XML style attributes match the original MCC layout; existing device suite passed 3/3. Real TME playback of 六月 showed full-width orange pitch rendering, purple multiline lyrics, current-line highlighting, position and score updates. APK: app/build/outputs/apk/debug/LyricsViewEx-30.0728.1-debug-20261009_160553.apk.
