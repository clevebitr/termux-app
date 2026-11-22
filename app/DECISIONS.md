# DECISIONS

> 决策与踩坑日志，只追加，不参与日常上下文。可用 grep 检索。条目按文件顺序编号（第一个 `##` 是 #1）。

## 2026-11-22 — 交付基线纠正：目标为 app/，app_new 降级为原型

需求方于 2026-11-23 书面确认：交付目标是真实应用 app/（com.termux），而非新外壳原型 app_new/。前期 27 个提交全部落在 app_new/，属于基线误判。

决定：
1. 授予 app/ 修改豁免：app/ 可直接修改并逐项单独提交；terminal-view/、terminal-emulator/、termux-shared/ 仍只读，如需改动须另行书面豁免。AGENTS.md 第 3 节已同步更新。
2. app_new/ 保留为原型，不再新增功能；docs/UI_REVIEW.md、docs/ARCHITECTURE_REVIEW.md、docs/UPGRADE.md 中与 app_new 相关的内容仅作参考，验收与交付计划以 app/ 为准。
3. 后续改造在 app/ 内进行：命令面板、菜单收敛、鼠标右键转发、虚拟按键入口等，遵循「包装层优先」，不修改只读模块。

基线事实：app/ 为 83 个 Java 文件、18719 行；已有 TerminalMenuDialog 终端上下文菜单、会话抽屉 TermuxSessionsListViewController、extra-keys（termux-shared 的 ExtraKeysView）、TerminalView 由布局 XML 实例化；无命令面板实现。右键当前固定走 TerminalView.onTouchEvent → onShowContextMenu → TermuxActivity.showTerminalMenu()，不区分鼠标跟踪状态。

影响：app_new 的提交与两份报告不再作为交付物；后续在 app/ 重新实施，并复用其中的设计结论（导航真源、设置描述层、命令面板分组、原子写入、会话状态真实化）。

## 2026-11-22 — 扩展 API 骨架落地与扩展化方向

方向：在 app/ 内建立扩展 API（包级扩展 + 运行时注册表），把可提取的 fork 功能逐个迁为扩展包；VT 级功能留在只读模块内，仅以属性开关暴露，不改只读模块。

已落地（提交 8ffc65e）：
- com.termux.app.ext.api：Extension（id/标题/摘要/requires/isUserDisableable/isEnabledByDefault/onEnable/onDisable）、ExtensionContext（Activity、ForkProperties 属性、扩展 SharedPreferences、日志）。
- com.termux.app.ext.host：ExtensionSpec、ExtensionState、ExtensionStatus、ExtensionGraph（依赖拓扑排序、缺依赖、依赖环、级联依赖查询；纯 Java 可 JVM 测试）、ExtensionPreferences（读写 enabled.<id>）、ExtensionManager（按解析结果启动/停止，单扩展异常隔离，Activity 生命周期内持有）。
- com.termux.app.ext.ForkExtensions：本构建的扩展登记表，当前为空。
- 设置页「扩展」：ExtensionsPreferencesFragment 程序化构建，开关写 SharedPreferences，禁用被依赖的扩展时弹确认；TermuxActivity 在 onCreate 末尾创建管理器、onResume 应用、onDestroy 释放。
- 测试：ExtensionGraphTest 7 项（默认值、核心不可禁用、缺依赖、顺序、级联、依赖环、未知依赖）。

约束与取舍：
1. 扩展只允许引用 ext.api、只读模块公开 API 与自身包；不得访问 TermuxActivity 字段。
2. 扩展状态存 SharedPreferences("termux-extensions")，键 enabled.<id>；不写 termux.properties，避免与用户手改属性冲突。
3. 禁用语义：贡献点立即移除、视图与定时器由扩展 onDisable 释放、VT 级行为在下次创建终端视图时生效。
4. 扩展设置页为程序化构建，暂不进入 SettingsSearchIndex（该索引只读 XML 页面）；后续加页时再评估。
5. 本机 :app:assembleDebug 因 NDK 缺 libc++_shared 失败（环境问题，非本次改动）；:app:compileDebugJavaWithJavac 与 :app:testDebugUnitTest 可运行，测试 143 项中 1 项为既有的 Robolectric 4.10 + JDK 21 失败。
