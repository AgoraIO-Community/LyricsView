# Android TME Response Parser Implementation Plan

**Goal:** 实现参考 iOS 设计的八种 TME 操作解析和 Android 异步回调，并提供请求生命周期管理。

**Architecture:** 歌词库内提供独立 Kotlin 类型、严格 Gson 解码器、串行任务/主线程回调解析器和 MCC 发送闭包管理器。Demo 通过库公开 API 引用，既有播放调用方保持原流程。

**Tech Stack:** Kotlin 1.8、Gson 2.10.1、ExecutorService、Android Handler/Looper、JUnit 4.13.2。

## Task 1：模型与解码

- [x] 在 `karaoke_view_ex/src/test/java/io/agora/karaoke_view_ex/tme/TMEJsonDecoderTest.kt` 写八类结果和错误测试，先运行确认缺少新 API。
- [x] 新增 `TMEParserModels.kt`、`TMEParserDelegate.kt`、`TMEParseError.kt`、`TMEJsonDecoder.kt` 和 `TMEStrictJson.kt`。
- [x] 复用 `karaoke_view_ex/build.gradle` 已有的 Gson 2.10.1，移除 Demo 的直接依赖。
- [x] 验证完整嵌套字段、所有列表条目、可选字段、错误优先级与严格类型；共享三种歌曲操作的解码。

## Task 2：异步解析和管理器

- [x] 测试真实单线程执行器与受控回调队列：调用立即返回，回调均异步，次序等于提交次序。
- [x] 新增 `TMEParser.kt` 和 `TMEMainThread.kt`，主线程默认使用 Handler；解析器弱引用委托，close 后已提交任务完成。
- [x] 写 `TMERequestManagerTest.kt`，覆盖旧成功/失败、重复响应、重试、stop、委托重入和关闭。
- [x] 新增 `TMERequestManager.kt`，通过调用方的 `sendExtRequest` 函数发送，四参数 SDK 响应转交解析器，仅交付当前 requestId。

## Task 3：验证与接入说明

- [x] 新增 `scripts/tests/run_tme_tests.sh`，编译同一组生产 Kotlin 与 JUnit 测试：`bash scripts/tests/run_tme_tests.sh`，24 个测试全部通过。
- [x] 新增 `app/src/androidTest/java/io/agora/examples/karaoke_view_ex/agora/tme/TMEParserInstrumentedTest.kt`，用于验证真实主线程异步回调；本机无设备，未运行。
- [x] 新增 `app/TME_PARSER.md`，给出 caller-owned MCC 请求/响应转交、委托持有、stop/close 和 Java/Kotlin 入口说明。
- [x] 尝试 Gradle 单元测试/Debug 构建，记录具体结果，不修改用户原有 wrapper 变动。
- [x] 执行 `git diff --check`，检查改动范围、线程/弱引用/错误路径、完整字段及无敏感正文日志；不提交用户工作。

## 2026-10-08 验证记录

- 独立 Kotlin/JUnit 脚本：24 个测试通过。三轮分别先确认解码器、异步解析器、请求管理器缺失造成失败，再加入实现。
- 额外使用缓存中的 Kotlin 1.8.0 编译器、JVM 1.8 target、`-Werror` 编译所有新生产代码及本地测试，编译无警告；同一套 24 个测试通过。
- `javac --release 8` 编译 Java 调用样例：无错误，覆盖默认构造器、SAM 发送接口、委托 adapter、类型化结果和错误属性。
- 独立代码审查发现 Gson 2.10.1 非宽松模式仍接受大写 literal 和未转义控制字符。新增失败回归后，以严格 token 校验修复；审查复核未发现其他生产问题。
- 原 wrapper 的 Gradle 9.0 milestone 在项目配置阶段因旧插件 API 不兼容失败；本机 Gradle 7.3.3 低于 AGP 的 7.4 最低要求。
- 使用本机 Gradle 8.7 再次尝试完整测试和 Debug 构建：原库模块缺少 NDK，配置阶段失败并伴随不存在 `release` SoftwareComponent 的错误；本机也无 CMake 安装。未宣称完整工程测试或 APK 构建通过。
- `adb devices -l` 无设备或模拟器，未运行 Android 仪器测试。SDK 网络接入亦未使用真实凭证验证。
- `bash -n scripts/tests/run_tme_tests.sh`、`git diff --check` 通过；保留用户原有 wrapper 修改，没有提交或推送。

## Task 4：移入歌词库并由 Demo 引用

- [x] 将全部八个生产 Kotlin 文件移到 `karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme`。
- [x] 将四个单元测试文件移到库 `src/test`；Demo 仪器测试从新包导入公开接口。
- [x] 为库启用 Kotlin 插件和 JVM 1.8 target，添加 JUnit 测试依赖，移除 Demo 的 Gson 直接依赖。
- [x] 公开关闭接口改为 `Closeable`，兼容歌词库 API 17 下限。
- [x] 更新测试脚本、Demo 接入 import 和文档，保留 Demo 已有的本地模块依赖。
- [x] 重新执行回归测试、Kotlin 1.8 编译和独立 Demo Java API 编译，记录完整 Gradle 验证结果。

迁移验证：独立脚本先编译库，再按 Gradle 单元测试的 friend 模块方式编译测试，24 个测试通过。使用实际 Kotlin 1.8.0 编译器再次独立编译库与测试（`-Werror`），24 个测试通过；Java 8 Demo 调用样例仅依赖库 jar，编译通过。拆分模块后测试中的三个 nullable 属性读取不能跨模块 smart cast，已改为先保存非空本地变量。

本机 Gradle 8.7 的 `:karaoke_view_ex:testDebugUnitTest :app:compileDebugKotlin --offline` 仍在库模块配置阶段因原有 NDK 缺失失败，并伴随缺少 release SoftwareComponent 的错误；完整 Android 构建未通过。`git diff --check` 和 shell 语法检查通过；原 wrapper 修改保留，未提交或发布。
