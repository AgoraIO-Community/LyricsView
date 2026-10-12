# Android Demo 曲库选择入口

依据用户确认：启动 Demo 后先显示选择页，提供 TME 与音集协两个入口。音集协进入现有 MCC 歌词/计分页面；TME 进入现有歌曲列表，选择歌曲后立即打开独立演唱页，沿用 2026-10-08 的 Android TME 计分设计和 iOS 参考设计。

新增 `LibrarySelectionActivity` 作为唯一 launcher，使用现有 XML/ViewBinding 和 Material 主题。选择页只导航，不创建 RTC、MCC 或播放器。按钮分别向内部 `MainActivity` 传入 TME/MCC 类型。

`MainActivity` 首次创建时应用显式选择，保存相应服务类型和 LRC/XML 歌词类型，并在 RTC/MCC 初始化之前确定当前服务。配置重建不能再次应用原 Intent 覆盖设置页中的后续选择。既有音集协 UI、设置和 MCC_EX 兼容路径保留。

返回顺序为演唱页 → TME 列表 → 曲库选择页；音集协页面返回选择页。退出曲库页沿用现有 RTC/MCC 销毁流程；返回 TME 列表只销毁单曲播放器与资料准备任务。重新启动时始终进入选择页，不因上次保存的服务类型跳过选择。页面标题标明当前曲库。

验证启动不创建 RTC、旧选择不覆盖当前按钮选择、两条路径正确显示及返回后可切换。运行现有 TME 单元测试与演唱页 instrumentation，构建 APK 并更新安装连接的手机。真实歌曲演唱是否通过单独报告，不以模拟页面测试替代真实曲库验证。
