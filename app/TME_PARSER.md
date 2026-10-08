# Android TME 响应解析接入

TME 音高 JSON 与本地 LRC 合并使用库内 `KaraokeView.parseTMEToneData(toneFilePath, lyricFilePath)`，然后交给 `setLyricData(model, true)`。详见 [音高接入说明](../karaoke_view_ex/TME_TONE_DATA.md)。

实现位于歌词库 `karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme`，公开包名为 `io.agora.karaoke_view_ex.tme`，与 iOS 的 TME 响应契约一致。Demo 通过已有的 `implementation project(':karaoke_view_ex')` 引用，不再包含解析器源码或直接依赖 Gson。当前 Demo 没有 TME 选歌页面，以下示例说明如何在已有 MCC 业务中使用库接口；这些组件不创建 RTC/MCC，不改变原有播放路径。

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
./gradlew :karaoke_view_ex:testDebugUnitTest :app:assembleDebug --no-daemon
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.agora.examples.karaoke_view_ex.agora.tme.TMEParserInstrumentedTest
```

独立脚本先单独编译库，再以 friend 模块方式编译库的 JUnit 测试，避免把生产与测试源码合并编译而漏掉模块边界问题；不依赖 Agora 凭证或服务。它优先使用系统 Kotlin 编译器，macOS 上可使用 Android Studio 的编译器；依赖从 Gradle 缓存和 Android SDK 发现，也可通过 `KOTLINC`、`GSON_JAR`、`JUNIT_JAR`、`HAMCREST_JAR`、`ANDROID_JAR` 指定。脚本注入回调队列以检验真实后台解码和确定的异步顺序；真实 Android 主线程行为由 Demo 仪器测试验证。
