# TME 音高 JSON 与 LRC

需要从 SDK 选歌、播放一路接入歌词与计分时，先阅读 [Android TME 开发接入参考](../docs/tme-android-integration-guide.md)。本文补充文件解析规则和公共准备组件契约。

库新增同步 Java/Kotlin 入口，参数均为本地文件完整路径：

```java
LyricModel model = KaraokeView.parseTMEToneData(toneFilePath, lyricFilePath);
if (model != null) {
    karaokeView.setLyricData(model, true);
}
```

文件读取和解析适合放到业务后台线程；`setLyricData` 和现有歌词播放、pitch 更新仍按组件原来的线程方式调用。本接口不请求网络，也不新增线程或得分回调。

音高输入示例：

```json
[{"st":"43300","d":"500","p":"44"},{"st":44456,"d":200,"p":56}]
```

`st/d` 为非负起点和正时长，单位毫秒，可用整数数值或数字字符串；`p >= 32`，频率按 `55 * (2^((p - 32) / 12) - 1e-6)` 转换。单条错误记录跳过，文件不可读、格式错误、无合法时间行或无可计分音符返回 `null`。

专属 LRC 解析按十进制秒换算时间，`[00:43.30]` 为 43300ms，`[00:44.456]` 为 44456ms。支持多个时间标签，保存整行原文并按时间稳定排序。每行使用 `[起点, 下一行起点)`；音符按起点归行，跨句部分截断，不转给下一句。最后一行按该行有效音符的最晚结束时间结束。

标题（根据 `ti/ar`）、词曲署名、空行和无音高行继续显示，但不进入评分。内部音符的文字、发音为空，语言未知；不推断逐字时间。完整显示行使用原 LRC 整行渲染，音符和音高条保留浮点频率。

沿用 `KaraokeEvent.onLineFinished`：回调模型为原显示行，`index` 只对可计分行从 0 连续计数，`lineCount` 是可计分行总数。无音高行不回调零分、不计入分母。沿用原 native 音高比较，先平均每个音符采样分，再平均整句音符分；未采样音符得 0，累计分是逐句分之和（加原有 initialScore）。句末更新先结算前句，再处理当前采样，边界归下一音符。

仅本 API 生成的内部模型使用该评分规则；复制模型保留标记。旧 `parseLyricData`、XML/KRC/LRC 解析和旧模型评分路径保持兼容。

验证：

```bash
bash scripts/tests/run_tme_tone_tests.sh
bash scripts/tests/run_tme_tests.sh
```

第一条编译实际 Java 模型/解析器/评分器，以及项目原 JNI/C 算法为主机动态库；只有 Android 注解和日志使用主机替代类。`src/testNative` 评分集成测试由该脚本执行，不放入普通 Android Gradle JVM 测试，避免 JVM 加载 Android `.so`。`src/test` 的 tone 文件解析测试可由正常 Gradle 单元测试运行。Demo 的 instrumentation 测试覆盖公开入口、`setLyricData` 和旧解析入口，需要 Android 设备。完整 Android 构建需要项目指定的 NDK/CMake。

## 自动准备计分资料

公共 `TMEScoringPreparation` 将 song-info、并行文件下载和解析封装在组件中，适合直接随 AAR 发布。它依赖调用方已经创建的 MCC 的发送函数；不要求复制 Demo、ServiceManager、列表、凭据或 SDK 生命周期代码。

```kotlin
import android.os.Handler
import android.os.Looper
import io.agora.karaoke_view_ex.tme.*

val main = Handler(Looper.getMainLooper())
val preparation = TMEScoringPreparation(cacheRoot)
preparation.listener = object : TMEScoringPreparationListener {
    override fun onStage(stage: TMEScoringPreparationStage) {
        // 主线程，分别保存歌词和 pitch 的下载状态。
    }
    override fun onSuccess(result: TMEScoringPreparationResult) {
        karaokeView.setLyricData(result.lyricModel, true)
        showScoringArea()
        fullScore = result.scorableLineCount * 100
        modelApplied = true
        tryStartPlayback() // 另需媒体已打开、页面前台；首次 play 只提交一次。
    }
    override fun onFailure(error: TMEScoringPreparationError) {
        karaokeView.reset()
        hideScoringArea() // 整个歌词、音准与得分区域。
        unscoredUIApplied = true
        tryStartPlayback() // 媒体错误由播放器处理，不作为资料降级。
    }
}
// 原始字符串 songId（不要传 SDK 内部 long songCode）：
preparation.prepare(songId, TMERequestSender { mcc.sendExtRequest(it) })

// MCC onExtResponse 内；始终异步 post，原始四参数不改写：
main.post {
    if (!preparation.handleExtResponse(requestId, jsonOption, httpCode, responseBody)) {
        songListRequests.onExtResponse(requestId, jsonOption, httpCode, responseBody)
    }
}

// 仅当前会话处于 PLAYING、计分启用且页面前台时：
main.post {
    karaokeView.setProgress(player.getPlayPosition())
    karaokeView.setPitch(localMicrophoneVoicePitch.toFloat(), 0F, 0)
}
// 主线程离开歌曲：preparation.cancel()；最终释放：preparation.close()
```

示例中的显示函数和播放门槛是调用方已有 UI/播放逻辑。返回模型已经加载到内存，组件会清理自己创建的临时文件。成功结果直接提供可计分句数，调用方不需引用内部模型。仅选择匹配原始 ID 的 song-info 中第一个精确 `type == "lrc"` 的资源和非空 pitch URL，不使用列表缓存或 KRC 替代。

默认下载器使用 HttpURLConnection，保持 minSdk 17，并流式写入 `cacheRoot/tme-scoring/<UUID>/song_lyric.lrc` 与 `song_pitch.json`。签名 URL 保持原字符串，遵守 Android 明文网络策略；Demo 不为此全局启用 HTTP。可注入 `TMEResourceDownloader`；实现必须在写入停止后恰好回调一次，包括取消。注入的共享下载器仍由调用方管理，preparation.close 不关闭它。

`TMEScoringPreparationTimeouts` 默认查询 30 秒、每份下载 60 秒、总体 120 秒。准备入口、listener 设置、回包转交、cancel、close 均在主线程；网络、文件、解析在后台，结果回主线程。cancel 不通知完成，close 幂等。错误只有类别、资源类型和必要 HTTP/业务码，不带 URL、响应正文或底层异常 message。

源码引用使用 `isSdkMode=false`；现有 Maven 3.0.4 尚无此 API。验证公共组件可使用 Gradle 8.7：`gradle :karaoke_view_ex:assembleDebug :karaoke_view_ex:testDebugUnitTest --configure-on-demand --no-daemon`。
