#!/usr/bin/env bash
set -euo pipefail

task_root="$(cd "$(dirname "$0")/../.." && pwd)"
task_gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
task_kotlinc="${KOTLINC:-}"
if [[ -z "$task_kotlinc" ]]; then
  task_kotlinc="$(command -v kotlinc || true)"
fi
if [[ -z "$task_kotlinc" && -f '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc.ide/bin/kotlinc' ]]; then
  task_kotlinc='/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc.ide/bin/kotlinc'
fi
if [[ -z "$task_kotlinc" ]]; then
  echo 'Set KOTLINC to a Kotlin compiler executable.' >&2
  exit 1
fi

find_test_jar() {
  local task_name="$1"
  rg --files "$task_gradle_home/caches" "$task_gradle_home/wrapper/dists" -g "$task_name" 2>/dev/null | head -1 || true
}
task_gson="${GSON_JAR:-$(find_test_jar gson-2.10.1.jar)}"
task_junit="${JUNIT_JAR:-$(find_test_jar junit-4.13.2.jar)}"
task_hamcrest="${HAMCREST_JAR:-$(find_test_jar hamcrest-core-1.3.jar)}"
task_android="${ANDROID_JAR:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar}"
for task_jar in "$task_gson" "$task_junit" "$task_hamcrest" "$task_android"; do
  if [[ ! -f "$task_jar" ]]; then
    echo 'Supply GSON_JAR, JUNIT_JAR, HAMCREST_JAR and ANDROID_JAR, or populate the Gradle/Android SDK caches.' >&2
    exit 1
  fi
done

task_library_sources=()
while IFS= read -r task_source; do
  task_library_sources+=("$task_source")
done < <(rg --files \
  "$task_root/karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme" -g '*.kt')
task_test_sources=()
while IFS= read -r task_source; do
  task_test_sources+=("$task_source")
done < <(rg --files \
  "$task_root/karaoke_view_ex/src/test/java/io/agora/karaoke_view_ex/tme" -g '*.kt')
task_output="$(mktemp -d "${TMPDIR:-/tmp}/lyricsview-tme-tests.XXXXXX")"
trap 'rm -f "$task_output/library.jar" "$task_output/tests.jar"; rmdir "$task_output"' EXIT
task_classpath="$task_gson:$task_junit:$task_hamcrest:$task_android"
bash "$task_kotlinc" -language-version 1.8 -api-version 1.8 -jvm-target 1.8 \
  -classpath "$task_classpath" "${task_library_sources[@]}" -d "$task_output/library.jar"
bash "$task_kotlinc" -language-version 1.8 -api-version 1.8 -jvm-target 1.8 \
  -Xfriend-paths="$task_output/library.jar" -classpath "$task_output/library.jar:$task_classpath" \
  "${task_test_sources[@]}" -include-runtime -d "$task_output/tests.jar"
task_tests=()
for task_source in "${task_test_sources[@]}"; do
  if [[ "$task_source" == *Test.kt ]]; then
    task_tests+=("io.agora.karaoke_view_ex.tme.$(basename "$task_source" .kt)")
  fi
done
java -cp "$task_output/library.jar:$task_output/tests.jar:$task_classpath" org.junit.runner.JUnitCore "${task_tests[@]}"
