# Android TME 响应解析接入

完整开发接入流程见 [Android TME 歌曲播放、歌词与内置打分接入参考](../docs/tme-android-integration-guide.md)，包含 SDK 初始化、请求与预加载、模型准备、播放门控、UI、逐句计分及生命周期。

TME 音高 JSON 与本地 LRC 合并使用库内 `KaraokeView.parseTMEToneData(toneFilePath, lyricFilePath)`，然后交给 `setLyricData(model, true)`。详见 [音高接入说明](../karaoke_view_ex/TME_TONE_DATA.md)。

实现位于歌词库 `karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme`，公开包名为 `io.agora.karaoke_view_ex.tme`，与 iOS 的 TME 响应契约一致。Demo 通过已有的 `implementation project(':karaoke_view_ex')` 引用。启动后先选择曲库：TME 进入歌曲列表，点击歌曲即进入独立演唱页；音集协进入原有 MCC 歌词与计分页面。选择页不创建 SDK 对象，本次选择在 RTC/MCC 初始化前生效；公共准备组件也不创建 RTC/MCC 或播放器。

演唱页返回 TME 列表时保留 RTC/MCC；曲库页面返回选择页时释放当前 RTC/MCC，再选择另一曲库会重新初始化。每次重新启动均显示选择页，旧设置不会覆盖本次入口选择。原有设置与其他服务兼容路径仍可使用。

本地 Demo 使用 `gradle.properties` 中的 `isSdkMode=false`。已发布的 3.0.4 不含本次新增接口；新版本发布后，才可切换到 Maven 依赖模式。库已启用 Kotlin 1.8，JSON 解码复用库现有 Gson 2.10.1；关闭接口使用 `Closeable`，兼容库的 Android API 17 下限。

## 直接解析 SDK 回包

页面自行持有解析器和委托；解析器只弱引用委托。不要仅把一个没有其他持有者的匿名对象赋给 `delegate`。

```kotlin
import io.agora.karaoke_view_ex.tme.TMEParser
import io.agora.karaoke_view_ex.tme.TMEParserDelegate
import io.agora.karaoke_view_ex.tme.TMEParseError
import io.agora.karaoke_view_ex.tme.TMESongsResult

private val parser = TMEParser()
private val listener = object : TMEParserDelegate {
    override fun onSongs(requestId: String, result: TMESongsResult) {
        // 主线程：根据业务选择展示数量；result 保留全部歌曲。
    }

    override fun onParseError(
        requestId: String, jsonOption: String, responseBody: String, error: TMEParseError
    ) {
        // 根据错误类型展示状态。不要记录原始响应、签名 URL 或业务 msg。
    }
}

// 页面初始化：parser.delegate = listener
// MCC onExtResponse 回调内（允许 SDK 后台线程）：
parser.parse(requestId, jsonOption, httpCode, responseBody)
// 页面结束：parser.delegate = null; parser.close()
```

`parse` 返回后才异步交付结果：后台单线程解析，主线程 `Handler(Looper.getMainLooper())` 回调。同一解析器按输入提交顺序回调。有效输入恰好选择一种成功或错误回调；委托已释放时不交付。`close` 不丢弃已提交输入，但不能再调用 `parse`。

| actionType | 成功回调 | 结果类型 |
|---|---|---|
| songs | onSongs | TMESongsResult |
| search-song | onSearchSongs | TMESearchSongsResult |
| song-info | onSongInfo | TMESongInfoResult |
| song-url | onSongUrl | TMESongUrlResult |
| songlist-page | onSonglistPage | TMEPageResult |
| songlist-detail | onSonglistDetail | TMEDetailResult |
| ranklist-page | onRanklistPage | TMEPageResult |
| ranklist-detail | onRanklistDetail | TMEDetailResult |

请求必须为 JSON 对象，数值 `vendorId: 2` 和支持的字符串 `actionType` 必填；解析响应不需要 `actionParameter`。错误按请求结构、未知操作、HTTP、业务 code/msg、data 的顺序判断，依次对应 `InvalidRequest`、`UnsupportedAction`、`HttpError`、`BusinessError`、`InvalidResponse`。HTTP 状态和业务错误码分别保存在 `httpCode`、`code`；业务提示保存在可选 `msg`。

字段显式校验，字符串与数字不自动转换。可选字段允许缺失/null；已提供的错误类型、非整数/溢出的整数字段、缺失必填字段均失败。日期和 URL 保持解码后的原始字符串。`expire` 是字符串；分页资源键为 `url`，详情图片键为 `imgUrl`。

## 管理当前请求，过滤退出或重试后的回包

在支持 TME `sendExtRequest` / `onExtResponse` 的 MCC SDK 中，把调用方已初始化的 MCC 绑定到发送接口。`TMERequestManager` 不持有 SDK 类型，也不使用 HTTP 替代 SDK 请求。

```kotlin
import io.agora.karaoke_view_ex.tme.TMERequestManager
import io.agora.karaoke_view_ex.tme.TMEParserDelegate
import io.agora.karaoke_view_ex.tme.TMESongsResult

// mcc 是业务已有的 MCC 对象。
private val requests = TMERequestManager { jsonOption ->
    mcc.sendExtRequest(jsonOption)
}
private val listener = object : TMEParserDelegate {
    override fun onSongs(requestId: String, result: TMESongsResult) {
        // 仅当前请求会到达此处，包括错误回调。
    }
}

// 主线程初始化：requests.delegate = listener
// 主线程发送；业务参数由调用方根据操作构造：
val option = """{"vendorId":2,"actionType":"songs","actionParameter":{}}"""
val sentRequestId = requests.sendRequest(option)
// null 表示 SDK 未接受请求，由调用方展示发送失败。

// 在 MCC 回调中原样转交四参数（允许后台线程）：
fun onMccExtResponse(requestId: String, jsonOption: String, httpCode: Int, responseBody: String) {
    requests.onExtResponse(requestId, jsonOption, httpCode, responseBody)
}

// 主线程暂停/退出页面：requests.stop()
// 主线程销毁管理器：requests.close()
```

一次管理器只维护一个当前请求，后续发送使旧请求失效；停止、重试、销毁之后的旧成功/旧错误，以及重复回包，都不会更新页面。UI 回调执行前已经消费当前请求，可在回调内直接发送下一请求。SDK 没有取消入口时，`stop` 只过滤回包。并行歌曲列表与歌曲资料等独立流程使用独立管理器；解析器自身没有“当前请求”限制。

`sendRequest`、`stop`、`close` 和管理器委托设置必须在主线程；MCC 响应可在任意线程转交。使用页面生命周期明确调用 `close`，以释放后台执行器。Java 可以 `new TMEParser()`，以 `TMEParserAdapter` 的子类接收所需回调，通过 `setDelegate` 设置委托；管理器支持 `new TMERequestManager(option -> mcc.sendExtRequest(option))`。

## 测试

```bash
bash scripts/tests/run_tme_tests.sh
bash scripts/tests/run_tme_controller_tests.sh
# 使用 Gradle 8.7（当前 wrapper 9 milestone 与 AGP 7.3.1 不兼容）：
gradle :karaoke_view_ex:assembleDebug :karaoke_view_ex:testDebugUnitTest --configure-on-demand --no-daemon
# 配套本地 RTC SDK 放好后：
gradle :app:assembleDebug :app:testDebugUnitTest --no-daemon
gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.agora.examples.karaoke_view_ex.agora.tme.TMEParserInstrumentedTest
```

独立脚本先单独编译库，再以 friend 模块方式编译库的 JUnit 测试，避免把生产与测试源码合并编译而漏掉模块边界问题；不依赖 Agora 凭证或服务。它优先使用系统 Kotlin 编译器，macOS 上可使用 Android Studio 的编译器；依赖从 Gradle 缓存和 Android SDK 发现，也可通过 `KOTLINC`、`GSON_JAR`、`JUNIT_JAR`、`HAMCREST_JAR`、`ANDROID_JAR` 指定。脚本注入回调队列以检验真实后台解码和确定的异步顺序；真实 Android 主线程行为由 Demo 仪器测试验证。

主机脚本编译实际模型和 TMEToneParser，并通过一个静态 KaraokeView 主机入口转交真实解析器，避免创建 Android View；实际公开组件类还由 Gradle AAR 构建检查。`TmeSingingScreenInstrumentedTest` 使用模拟媒体/准备接口检查成功或降级的 UI 安装顺序、独立下载状态、暂停继续和中断重建，运行它仍需先构建包含配套 RTC SDK 的测试 APK。

## Demo 演唱流程

`ServiceManager` 持有单曲控制器，`MccManager` 保留现有 RTC engine 初始化的 MCC。列表与资料使用独立请求管理器；预加载以 requestId、内部 songCode 和 sessionId 关联。每首歌的 player/observer 单独创建和释放，旧播放器排队的回调不能开始新歌。

预加载完成后并行打开播放器和 `TMEScoringPreparation.prepare`。准备成功时，演唱页先安装模型，再确认页面就绪；资料失败时先隐藏整个歌词、音准和得分区域，再允许无计分播放。预加载、打开和播放失败作为播放错误处理。详情见 [公共准备接口](../karaoke_view_ex/TME_TONE_DATA.md#自动准备计分资料)。

TME 每 20ms 读取实际播放器位置；本地麦克风 `uid == 0` 的 voicePitch 提交前再次更新位置，然后 `setPitch(pitch, 0F, 0)`。逐句和累计分来自内部 `KaraokeEvent.onLineFinished`，满分为可计分句数 ×100；不会用显示行数、外部 onLineScore 或补造末句音高计算。

演唱页退到后台会暂停，回前台需要手动继续；普通旋转保留原视图。其他重建或进程恢复显示中断并从头开始。返回列表释放当前 player、准备任务和评分视图，保留 RTC/MCC；退出功能由原管理层销毁 SDK。TME 不提供 seek、跳前奏或计分参数入口。

Demo 构建要求配套 `app/libs/agora-rtc-sdk.jar` 和 `app/src/main/jniLibs/arm64-v8a/libagora-rtc-sdk.so` 及其他 SDK 原生库。旧 Maven RTC 包不能验证这里新增的 TME SDK 接口。SDK、签名 URL、凭据和构建产物不提交。
