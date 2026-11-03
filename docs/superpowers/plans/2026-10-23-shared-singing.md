# Shared Singing Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans in this session. Preserve the user's existing working changes and execute sequentially.

**Goal:** TME uses the existing MCC singing layout and controls, with working seeking and lyrics-only fallback.

**Architecture:** MainActivity owns one binding and KaraokeView. TmeSingingPresenter adapts the retained TME controller to these controls; ServiceManager owns song selection and the media session. Optional lyrics-only preparation preserves the default library contract.

**Tech Stack:** Android Java/Kotlin, ViewBinding, JUnit4, Android instrumentation, local Agora RTC SDK.

### Task 1: Controller and resource fallback

- [ ] Extend `app/src/test/java/io/agora/examples/karaoke_view_ex/agora/tme/TmePlaybackControllerTest.kt` with seek while playing/paused, rejected seek, stale/terminal calls and unscored-model readiness. Run `bash scripts/tests/run_tme_controller_tests.sh` and confirm the missing behavior fails.
- [ ] Add `MediaPort.seek(positionMs): Int`, `seek(positionMs): Boolean` and `PreparationListener.onUnscored(model, category)` to `TmePlaybackController.kt`. Only playing/paused acknowledged sessions can seek; rejected seeks do not change progress or scores. Unscored models require the same page acknowledgement as other unscored playback.
- [ ] Extend `TMEScoringPreparationTest.kt` for opt-in lyric retention when pitch is missing, download fails or times out, and merged parsing fails. Strict mode continues failing as before. Run `bash scripts/tests/run_tme_tests.sh` to confirm failures.
- [ ] Add opt-in `allowLyricsOnly`, a per-session fallback error, and `TMEScoringPreparationListener.onLyricsOnly(model,error)`. Download/parse valid LRC even if pitch is unavailable; preserve per-session cleanup and cancellation. Add `TMEToneParser.parseLyrics` reusing its existing LRC reader. Re-run both suites and `bash scripts/tests/run_tme_tone_tests.sh`.

### Task 2: Shared controls and media session

- [ ] Update `TmeSingingScreenInstrumentedTest.kt` to assert MCC control IDs, callback/description status, gating initial play, unscored lyrics, and shared pause/continue. Use existing injected controller fixtures without starting a real song.
- [ ] Add `TmeSingingPresenter.kt` implementing `TmePlaybackController.Listener<LyricModel>`, receiving `ActivityMainBinding` and the existing `KaraokeView`. It handles TME control actions, model installation, callbacks, preferences and lifecycle without inflating a view.
- [ ] Update `MainActivity.java` to delegate TME events and controls to the presenter; remove TME container visibility switching. Share lyric and score toggles. Preserve MCC callbacks and guard TME from duplicate legacy callbacks.
- [ ] Update `ServiceManager.kt` and `MccManager.java` for session seek and selected TME song tracking; enable lyric fallback and forward the model with `onUnscored`. Route pause/continue/stop through the controller.
- [ ] Make `TmeSingingActivity.kt` a compatibility subclass of MainActivity; remove the obsolete Fragment and dedicated layout/container. Maintain supplied-session entry and safe interrupted restoration.

### Task 3: Verification and documentation

- [ ] Update navigation instrumentation to assert shared controls under both service choices and settings round trips. Update Demo docs to describe one singing UI and optional lyrics-only preparation.
- [ ] Run `bash scripts/tests/run_tme_controller_tests.sh`, `bash scripts/tests/run_tme_tests.sh`, `bash scripts/tests/run_tme_tone_tests.sh`.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline` with the local JDK. Run `git diff --check` and inspect the final diff for unrelated changes.
- [ ] Check `adb devices`; run connected instrumentation only when an authorized device is present. Report exact checks completed and any device validation limits.

## 执行记录

任务 1–3 的实现、主机回归、应用单元测试和两种 APK 构建已完成。构建改用项目此前验证过的缓存 Gradle 8.7 / JDK 17，避免 wrapper/AGP 和新版 JDK/D8 不兼容。默认严格计分准备模式保持通过；仅歌词降级显式启用。设备回归已更新、编译，但没有连接设备，未执行真机测试。

独立审查补充并修正空模型绑定和 SDK 拒绝拖动时保留分数两个边界，复审无剩余重要问题。完整命令、结果及限制见 [验证记录](../../../work/verification/shared-singing-20261023/README.md)。本工作区原有修改及本次结果保留为未提交状态。
