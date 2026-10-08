# Android TME 音高与 LRC 合并

依据 iOS `2026-09-29-tme-tone-data-design.md`，在 `karaoke_view_ex` 中实现。

1. 新增同步 `KaraokeView.parseTMEToneData(String toneFilePath, String lyricFilePath)`，失败返回 null；独立解析十进制 LRC 时间及严格 JSON 数组。
2. 用内部 TME 模型区分显示行与评分行。显示行沿用整行 LRC 渲染，评分行保存无文字音符与浮点频率；复制保留该标记。旧解析与旧模型不变。
3. 在 ScoringMachine 增加仅 TME 模型使用的半开区间和逐句结算，复用 native 音高比较、默认单音符/逐句平均及现有回调。标题、署名、空行、无音高行不进入计分索引和总句数。
4. 添加文件解析、坏数据、裁剪、评分回调、边界、拖动和旧模型回归测试。用现有 JNI 源码编译主机库验证真实算法；仅替换主机无法运行的 Android 日志/注解。
5. 运行新测试和已有 response parser 测试，审查 diff，补充调用说明。完整 Android 构建仍需项目要求的 NDK/CMake。

完成验证：主机解析/JNI 评分测试 11 项通过，原响应解析测试 24 项通过。独立审查发现并修复末句被已丢弃/截断音符延长的问题，增加回归测试确认。`git diff --check` 通过。

完整 Gradle `:karaoke_view_ex:testDebugUnitTest :app:compileDebugKotlin --offline` 在配置阶段因 NDK 未配置及 publishing 的 `release` component 不可用而失败；没有执行到 Android 编译。设备 instrumentation 测试已补充，尚未执行。保留用户预先修改的 Gradle wrapper。
