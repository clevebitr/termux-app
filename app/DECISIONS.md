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

## 2026-11-22 — 备份功能提取为扩展包与设置项可见性贡献点

第一个真实扩展已提取（提交 26efb73），用于验证「贡献点 + 用户禁用 + 设置页联动」闭环。

做法：
- 新增贡献点 `Extension.preferenceKeys()`：扩展声明自己拥有的设置项键（`app:key`）。禁用扩展时，`TermuxPreferencesFragment` 在加载 `termux_preferences.xml` 后调用 `ExtensionPreferences.applyPreferenceVisibility()` 隐藏这些键，因此不能出现「页面还能打开但功能已停用」的状态。
- `ext.backup`：`TermuxBackupUtils` 与 `BackupPreferencesFragment` 从 `backup/`、`fragments/settings/termux/` 迁入 `com.termux.app.ext.backup`；新增 `BackupExtension`（id=backup，默认启用，声明 preferenceKeys=["backup"]，onEnable/onDisable 为空实现，因为该功能不持有视图或监听）。`ForkExtensions` 登记该扩展。
- 设置 XML 为备份项补 `app:key="backup"` 并把 `app:fragment` 指向新包；`SettingsSearchIndex` 更新导入。
- `PreferenceResetUtils` 由包内可见改为 public（跨包使用）；`PreferenceResetSourceTest` 的页面清单改为「页面名 → 路径」映射，避免页面移动后守卫测试静默失效。

约定：
1. 扩展若拥有设置页，必须声明 `preferenceKeys()`，否则禁用后入口仍会显示。
2. 扩展的 `onDisable()` 必须释放视图、监听与定时器；不持有资源的扩展写空实现并注释原因。
3. 迁移功能时保持资源（string/drawable/raw）留在 app 主模块，避免单模块内无法按扩展拆分资源的问题；后续拆分 Gradle 模块时再评估资源归属。

验证：`:app:compileDebugJavaWithJavac` 通过；`:app:testDebugUnitTest` 147 项中 146 通过，1 项为既有的 Robolectric 4.10 + JDK 21 失败；`:app:assembleDebug` 仍受本机 NDK（libc++_shared）缺失阻塞。

## 2026-11-22 — 禁用语义定为「入口不可达 + 运行时闸门」，并修复搜索绕过

需求方确认禁用语义取 A：禁用 = 入口不可达 + 运行时闸门，已安装到 $PREFIX/bin 的 shell 命令保留，由页面说明手动清理方式。理由：删除用户已安装命令与「不代用户删除数据」的约束冲突。

缺陷定位（症状：禁用扩展后设置搜索仍能进入其功能页）：
1. 唯一强制点是 ExtensionPreferences.applyPreferenceVisibility 调用 setVisible(false)，且只在 TermuxPreferencesFragment 调用一次，语义等价于「隐藏入口」，不代表「关闭功能」。
2. SettingsSearchIndex.PAGES 为静态数组，硬编码 termux_backup_preferences.xml，build() 无状态过滤，SettingsSearchDialog 直接使用。
3. SettingsSearchNavigation.findPagePreference 只比较 getFragment()，不检查 isVisible()，onPreferenceTreeClick 因此能打开已隐藏的页面。
4. BackupExtension.onEnable/onDisable 为空实现，BackupPreferencesFragment 与 TermuxBackupUtils 无任何状态判断。

本次修复：
- SettingsSearchIndex.Page 与 Entry 增加 extensionId，build() 末尾按禁用集合过滤（纯函数 withoutDisabledExtensions，2 项测试）。
- SettingsSearchNavigation.open 打开前二次校验：条目必须可见，且所属扩展必须处于运行态。
- BackupPreferencesFragment.onCreatePreferences 先查 ExtensionPreferences.isRunning(context, BackupExtension.ID)，未启用则显示占位说明并直接返回，不提供备份、恢复与命令安装动作。
- BackupExtension 增加 ID 常量，PREFERENCE_KEY 复用该常量。

未验证：真机交互与「禁用后搜索无结果」的实际表现未在设备上确认，本机仅通过 :app:compileDebugJavaWithJavac 与 :app:testDebugUnitTest 的相关包测试。
