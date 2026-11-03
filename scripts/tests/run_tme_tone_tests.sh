#!/usr/bin/env bash
set -euo pipefail

# Runs production parser, scoring machine and JNI algorithms on the host.
# Only Android annotation/logging classes use host substitutes.
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
task_cache="${GRADLE_USER_HOME:-$HOME/.gradle}"
find_jar() { rg --files "$task_cache/caches" "$task_cache/wrapper/dists" -g "$1" 2>/dev/null | head -1 || true; }
task_gson="${GSON_JAR:-$(find_jar gson-2.10.1.jar)}"
task_junit="${JUNIT_JAR:-$(find_jar junit-4.13.2.jar)}"
task_hamcrest="${HAMCREST_JAR:-$(find_jar hamcrest-core-1.3.jar)}"
task_android="${ANDROID_JAR:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar}"
task_kotlinc="${KOTLINC:-/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc.ide/bin/kotlinc}"
task_java_home="${JAVA_HOME:-$(/usr/libexec/java_home)}"
task_output="$(mktemp -d "${TMPDIR:-/tmp}/lyricsview-tone-tests.XXXXXX")"
# mktemp's validated, task-specific directory contains only this run's outputs.
trap 'rm -r "$task_output"' EXIT
bash "$task_kotlinc" -language-version 1.8 -api-version 1.8 -jvm-target 1.8 \
  "$task_root/karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme/TMEStrictJson.kt" \
  -include-runtime -d "$task_output/strict-json.jar"
task_main="$task_root/karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex"
task_sources=()
while IFS= read -r task_source; do task_sources+=("$task_source"); done < <(rg --files "$task_root/scripts/tests/jvm-stubs" -g '*.java')
task_cp="$task_output/strict-json.jar:$task_gson:$task_junit:$task_hamcrest:$task_android"
javac --release 8 -encoding UTF-8 -cp "$task_cp" -d "$task_output/classes" "${task_sources[@]}" \
  "$task_main/IScoringAlgorithm.java" "$task_main/constants/Constants.java" \
  "$task_main/internal/config/Config.java" "$task_main/internal/constants/LyricType.java" \
  "$task_main/internal/model/LyricsLineModel.java" "$task_main/internal/model/PitchData.java" \
  "$task_main/internal/model/LyricsPitchLineModel.java" "$task_main/model/LyricModel.java" \
  "$task_main/internal/model/TMELyricModel.java" "$task_main/internal/lyric/parse/TMEToneParser.java" \
  "$task_main/internal/ScoringMachine.java" "$task_main/internal/scoring/DefaultScoringAlgorithm.java" \
  "$task_main/internal/ai/AIAlgorithmScoreNative.java" \
  "$task_root/karaoke_view_ex/src/test/java/io/agora/karaoke_view_ex/tme/TMEFileTestSupport.java" \
  "$task_root/karaoke_view_ex/src/test/java/io/agora/karaoke_view_ex/tme/TMEToneParserTest.java" \
  "$task_root/karaoke_view_ex/src/testNative/java/io/agora/karaoke_view_ex/tme/TMEToneScoringTest.java"
case "$(uname -s)" in
  Darwin) task_jni_platform=darwin; task_native=libagora-karaoke-ai.dylib; task_shared=-dynamiclib ;;
  Linux) task_jni_platform=linux; task_native=libagora-karaoke-ai.so; task_shared=-shared ;;
  *) echo 'Host JNI test runner supports macOS/Linux.' >&2; exit 1 ;;
esac
"${CXX:-clang++}" "$task_shared" -fPIC -std=c++11 \
  -I"$task_java_home/include" -I"$task_java_home/include/$task_jni_platform" \
  "$task_root/karaoke_view_ex/src/main/cpp/io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative.cpp" \
  -o "$task_output/$task_native"
java -Djava.library.path="$task_output" -cp "$task_output/classes:$task_cp" org.junit.runner.JUnitCore \
  io.agora.karaoke_view_ex.tme.TMEToneParserTest io.agora.karaoke_view_ex.tme.TMEToneScoringTest
