# TME 音高 JSON 与 LRC

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
