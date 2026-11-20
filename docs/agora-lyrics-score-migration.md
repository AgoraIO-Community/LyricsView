# AgoraLyricsScore 迁移说明

`dev/6.1` 将组件模块、Java/Kotlin 包名、Maven artifactId、AAR 和 JNI
本地库统一更名。此变更不提供旧包名兼容层，接入方需要同步更新依赖和源码。
歌词解析、音高打分、播放同步和公开功能类名保持原有行为。

## 名称对应

| 项目 | 原名称 | 新名称 |
| --- | --- | --- |
| 根项目 | `LyricsViewEx` | `AgoraLyricsScore` |
| 库目录 / Gradle 模块 | `karaoke_view_ex` / `:karaoke_view_ex` | `AgoraLyricsScore` / `:AgoraLyricsScore` |
| 库 namespace / Java、Kotlin 包名 | `io.agora.karaoke_view_ex` | `io.agora.lyricsscore` |
| Demo namespace / applicationId / 包名 | `io.agora.examples.karaoke_view_ex` | `io.agora.examples.lyricsscore` |
| Maven 坐标 | `io.github.winskyan:Agora-LyricsViewEx:3.0.4` | `io.github.winskyan:AgoraLyricsScore:3.0.4` |
| 本地 AAR 文件 | `karaoke_view_v3.0.4.aar` | `AgoraLyricsScore_v3.0.4.aar` |
| JNI 本地库 | `libagora-karaoke-ai.so` | `libagora-lyrics-score.so` |
| 日志 TAG / 文件名前缀 | `LyricsViewEx` / `agora.AgoraLyricsViewEx` | `AgoraLyricsScore` / `agora.AgoraLyricsScore` |

本次保留 Maven groupId `io.github.winskyan` 和 SDK 版本号 `3.0.4`。
新 artifactId 是独立发布坐标，必须发布后才可从远端仓库下载；更名不表示已经发布。
源码工程继续使用 `isSdkMode=false`，通过本地模块依赖构建。

## 应用侧更新

源码集成时，在 `settings.gradle` 引入新模块：

```groovy
include ':AgoraLyricsScore'
```

在应用的 `build.gradle` 中使用：

```groovy
implementation project(':AgoraLyricsScore')
```

发布到接入方可用的 Maven 仓库后，改用：

```groovy
implementation 'io.github.winskyan:AgoraLyricsScore:3.0.4'
```

Java/Kotlin 的全部库导入从 `io.agora.karaoke_view_ex` 改为
`io.agora.lyricsscore`，子包结构不变。例如：

```kotlin
import io.agora.lyricsscore.KaraokeView
import io.agora.lyricsscore.model.LyricModel
import io.agora.lyricsscore.tme.TMEScoringPreparation
```

更新 XML 中的自定义控件全限定名：

```xml
<io.agora.lyricsscore.LyricsView
    android:layout_width="match_parent"
    android:layout_height="match_parent" />

<io.agora.lyricsscore.ScoringView
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

应用自行维护的反射字符串、混淆保留规则及测试入口也应更新。例如：

```proguard
-keep class io.agora.lyricsscore.** { *; }
```

使用新 AAR 整体替换旧 AAR，避免同时引入两套歌词控件和同名资源。新 AAR 内已包含
`libagora-lyrics-score.so`，并使用新包名对应的 JNI 导出符号，不能混用旧 `.so`。
手工打包本地库的接入方也要更新文件名。`KaraokeView`、`LyricsView`、
`ScoringView`、`IScoringAlgorithm` 等功能类名及调用方法不变。

## Demo 与测试

Demo 模块仍叫 `app`，显示名称为 `AgoraLyricsScore Example`。新的
applicationId 为 `io.agora.examples.lyricsscore`，Android 会将其视为新的应用，
不会自动继承旧 Demo 的私有数据和授权；调试脚本、启动组件和设备端测试包名须同步更新。
这些 Demo 标识变化不要求接入方修改自己产品的 applicationId。

```bash
bash scripts/tests/run_tme_tests.sh
bash scripts/tests/run_tme_tone_tests.sh
bash scripts/tests/run_tme_controller_tests.sh

./gradlew :AgoraLyricsScore:assembleRelease \
  :AgoraLyricsScore:testDebugUnitTest :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

本地 AAR 位于 `AgoraLyricsScore/build/outputs/aar/AgoraLyricsScore_v3.0.4.aar`。
Maven 发布使用标准坐标文件名 `AgoraLyricsScore-3.0.4.aar`，两者命名方式不同。

## 文档范围

README、当前接入指南、TME 解析说明及 tone 数据说明均使用新名称。
`docs/superpowers/` 下更名前的日期化设计/计划，以及本地 `work/verification/` 中的
历史验证记录保留当时的名称；复制其中示例时，应按本文映射更新路径和包名。
