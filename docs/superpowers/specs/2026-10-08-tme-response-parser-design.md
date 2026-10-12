# Android TME 响应解析器

参考 `LrcView-iOS/docs/superpowers/specs/2026-09-29-tme-response-parser-design.md`，实现其 Android 对等约定。用户已指定该设计作为实施依据。

## 范围与位置

按用户要求，组件放在歌词库的 `karaoke_view_ex/src/main/java/io/agora/karaoke_view_ex/tme`，包名为 `io.agora.karaoke_view_ex.tme`。解析器、结果/错误模型、委托和请求管理器均为公开 API；严格 JSON 解码、线程调度细节为 internal。库启用工程现有 Kotlin 插件，复用现有 Gson 依赖，关闭接口使用 API 17 可用的 `Closeable`。

单元测试放在库的 `src/test`，允许测试内部解码与注入调度入口。Demo 的仪器测试仅导入库公开 API，接入示例亦通过 `implementation project(':karaoke_view_ex')` 引用；Demo 不保留解析器源码，不直接依赖 Gson。当前 Android Demo 没有 TME 列表或 `TmeManager`，因此提供可接入 MCC 的请求管理器，不改变现有 MCC/MCC_EX 播放流程、歌词评分算法或 SDK 版本。

`TMEParser.parse(requestId, jsonOption, httpCode, responseBody)` 只提交任务。单线程执行器按提交顺序解码，主线程 `Handler(Looper.getMainLooper())` 异步投递八种独立成功回调或一个错误回调。委托使用弱引用，调用方须自行持有委托。关闭解析器停止接受新任务，已接受任务继续完成；关闭后调用属于生命周期错误，直接抛出异常。

## 数据与错误

八种操作、所有歌曲嵌套字段、分页的 `url`、详情的 `imgUrl`、字符串媒体过期时间均与参考设计一致。共享歌曲解码函数供三种歌曲操作使用。解析器返回完整列表；Android 当前没有最多六首的展示规则，不引入该限制。

使用工程已有 Gson 版本的 JSON 树及显式字段检查，不用会将数字自动转为字符串的反射解码。必填字段缺失/null、已提供字段类型错误、整数溢出或非整数均报错；可选字段允许缺失/null。未知字段忽略。严格 token 检查加非宽松 Gson 结构读取，拒绝非 JSON 语法及尾随内容，日期和 URL 不改写。

错误依次检查请求结构（vendorId 为数值 2、actionType 为字符串）、操作支持情况、2xx HTTP、业务 code/msg、操作 data。错误模型区分无效请求、未知操作、HTTP 错误、业务错误、无效响应。错误消息不包含原始 JSON 或解码异常正文；错误回调单独携带原始请求/响应，不打印带签名 URL。

## 请求关联与接入

`TMERequestManager` 接受调用方提供的 MCC `sendExtRequest` 函数，负责发送后保存 requestId，将 SDK 四参数响应转交自身解析器，且仅向仍存活的 UI 委托交付当前请求的成功/失败。重试、stop、close 都使旧请求失效，重复响应不重复交付。同一管理器仅维护一个当前请求；并行独立业务使用独立管理器。

管理器不创建 RTC/MCC，不构造 HTTP 请求，不读账号配置。发送、stop、close 和管理器委托设置从主线程调用；SDK 响应允许从任意线程转交。已有 Demo 使用旧 MCC 流程，本次提供接入示例而不伪造 SDK 回调或更换依赖以增加 TME 页面。

## 验证

JUnit 本地测试覆盖八类回调、完整元数据、空数组、字段类型和 JSON 格式、错误优先级、原字符串保留、后台线程、异步有序回调、弱委托、关闭后的在途响应，以及旧成功/失败/重复响应过滤。注入任务队列只用于控制调度，不替代真实 JSON 解码。

Android 仪器测试使用默认解析器验证实际主线程 Handler。提供独立 Kotlin/JUnit 运行脚本，能够在完整 Demo 依赖不可用时运行同一组本地测试。尝试 Gradle 单元测试及 Debug 构建，分别报告环境限制与未执行的设备测试。
