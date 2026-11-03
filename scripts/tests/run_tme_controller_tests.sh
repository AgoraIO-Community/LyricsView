#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
task_gradle_cache="${GRADLE_USER_HOME:-$HOME/.gradle}"
task_junit="$(rg --files "$task_gradle_cache" -g junit-4.13.2.jar | head -1 || true)"
task_hamcrest="$(rg --files "$task_gradle_cache" -g hamcrest-core-1.3.jar | head -1 || true)"
task_kotlinc="${KOTLINC:-$(command -v kotlinc || true)}"
if [[ -z "$task_kotlinc" ]]; then
  task_kotlinc='/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc.ide/bin/kotlinc'
fi
task_output="$(mktemp -d "${TMPDIR:-/tmp}/lyricsview-controller-tests.XXXXXX")"
trap 'rm -f "$task_output/tests.jar"; rmdir "$task_output"' EXIT
bash "$task_kotlinc" -language-version 1.8 -api-version 1.8 -jvm-target 1.8 \
  -classpath "$task_junit:$task_hamcrest" \
  "$task_root/app/src/main/java/io/agora/examples/karaoke_view_ex/agora/tme/TmePlaybackController.kt" \
  "$task_root/app/src/test/java/io/agora/examples/karaoke_view_ex/agora/tme/TmePlaybackControllerTest.kt" \
  -include-runtime -d "$task_output/tests.jar"
java -cp "$task_output/tests.jar:$task_junit:$task_hamcrest" org.junit.runner.JUnitCore \
  io.agora.examples.karaoke_view_ex.agora.tme.TmePlaybackControllerTest
