# Remove MCCEx from the Android Demo

The user approved this scope on 2026-11-03. Implement in the existing `dev/6.0` workspace.

## Runtime and compatibility

Remove the Agora-MccExService dependency, MccExManager, its ServiceManager branches, external scoring callbacks, plugin catalog and plugin-only token configuration/cache access. Keep the local RTC SDK and the existing native MCC and TME flows. Keep service storage IDs MCC=0 and TME=2; normalize legacy value 1 and invalid values to MCC/XML, while TME remains TME/LRC. Legacy token values need no destructive data migration: stop accessing them. Remove generated YSD/VENDOR_2 configuration; do not edit the user's local.properties.

Use `IAgoraMusicPlayer.MusicPlayMode` and the RTC `setPlayMode` method for original/accompaniment. Remove manual channel/track switching and the musicType parameter. Keep the original/accompaniment UI; do not expose lead singing. Preserve the displayed mode when the player is absent or rejects switching. Use Java/Kotlin standard date/random utilities for RTC channel and recording names.

Before initial TME playback, explicitly select ORIGINAL and abort playback if the SDK rejects it. Resume preserves the user's selected mode. Native MCC replay also requires a successful ORIGINAL switch before resetting the displayed state or preloading.

Keep the lyric component's existing parsing, internal scoring and common settings, including KRC support as a component capability. Update current README and integration instructions; retain historical design/verification records as history. Leave work/ alone.

## Verification

Add host regressions that exercise the real ServiceManager/MccManager with a recording RTC player interface, covering both services, mode order, rejection and absent players. Retain preference migration tests using the legacy numeric value rather than a deleted service enum. Replace tests that reflect on a deleted plugin manager with assertions that the plugin classes are absent. Build debug application and instrumentation APKs, run app unit tests and existing host parser/controller/JNI suites. Inspect the resolved runtime dependency graph and built APK for plugin classes and native library. Device playback checks require a connected Android device and must be reported separately from host/build results.
