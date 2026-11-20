# AgoraLyricsScore full rename

The user approved the full module, package and publication migration on `dev/6.1`,
including changes that require consumers to update their integration. Work stays
on that branch in the existing workspace.

## Naming contract

| Surface | Before | After |
| --- | --- | --- |
| Root project | LyricsViewEx | AgoraLyricsScore |
| Library directory / Gradle project | karaoke_view_ex / :karaoke_view_ex | AgoraLyricsScore / :AgoraLyricsScore |
| Library namespace / package | io.agora.karaoke_view_ex | io.agora.lyricsscore |
| Demo namespace / application ID / package | io.agora.examples.karaoke_view_ex | io.agora.examples.lyricsscore |
| Maven artifact | io.github.winskyan:Agora-LyricsViewEx | io.github.winskyan:AgoraLyricsScore |
| Local AAR | karaoke_view_v{version}.aar | AgoraLyricsScore_v{version}.aar |
| Native target / loadLibrary | agora-karaoke-ai | agora-lyrics-score |
| JNI class prefix | io_agora_karaoke_view_ex | io_agora_lyricsscore |
| JNI exported prefix | Java_io_agora_karaoke_1view_1ex | Java_io_agora_lyricsscore |

The Maven group and SDK version remain unchanged because this is a naming
migration, not a change of publisher or functional version. The app module stays
`app`. Public functional class names (`KaraokeView`, `LyricsView`, `ScoringView`)
and all scoring/playback behavior remain unchanged. No compatibility aliases are
introduced.

## Implementation boundaries

Move production/test source packages, JVM substitutes and JNI source filenames;
update Gradle namespaces, XML view names, imports, script source paths, test
entry points, CMake target/source, and System.loadLibrary together. The Demo
label, theme and library logging names follow the new product name. Use the
existing Maven group/artifact properties for the Demo's published dependency.

Update the current README, integration guides and module-specific documentation.
Older dated specs/plans and ignored work/verification records remain historical
evidence; the migration guide explains their old names. Do not rewrite SDK
binaries, credentials, generated files or unrelated functionality. Do not publish
artifacts, push or commit without a separate request.

## Verification

Run the three existing host test scripts before and after migration. Their real
file parsers, scoring machine and JNI algorithms verify behavior and native
symbol resolution. Build the release AAR, debug Demo APK and instrumented-test
APK, and run Gradle unit tests. Inspect AAR classes/native libraries, APK
application ID, generated POM and remaining active old-name references. Run
git diff --check and review that changes are limited to names and documentation.
