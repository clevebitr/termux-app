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

## 2026-11-22 — M-E1：扩展闸门层（唯一状态源 + 页面声明 + 守卫测试）

目标：把「扩展状态」收为一个来源，把「贡献点」从「隐藏一个 key」升级为可声明的资源，并让设置搜索与页面闸门共用同一判断。

1. ExtensionHost（ext/host/ExtensionHost.java）：进程级单例，持有 ForkExtensions.installed() 快照，statuses()/stateOf()/isRunning()/disabledIds()/dependentsOf()/setEnabled() 全部经由 ExtensionGraph + ExtensionPreferences.storedFlags 解析。不缓存解析结果：扩展为个位数，主线程同步解析，避免用户改标志后读到旧快照。
2. ExtensionManager 不再自行 resolve，也不再接收 installed 列表（构造签名改为 ExtensionManager(Activity)），改为读 host；其 stateOf 仅保留运行时结果（含 FORCED_DISABLED），与 host 的配置态区分。
3. ExtensionPreferences 退回纯偏好层：storedFlags / setEnabled / specs / resolve / applyPreferenceVisibility，不再决定功能是否运行。
4. 新贡献点 ExtensionSettingsPage（titleRes、xmlRes、fragmentClassName）与 Extension.settingsPages()；SettingsSearchIndex 的页面表 = 核心页 + 各扩展声明的页，禁用扩展的条目在 build() 末尾由纯函数 withoutDisabledExtensions 丢弃。搜索索引不再硬编码备份页与扩展 id。
5. applyPreferenceVisibility 增加空分类收起：某分类下的条目全部隐藏时，该分类与其父分类一并隐藏，避免留下指向已禁用功能的孤立标题。
6. 守卫测试 ExtensionRegistrySourceTest（3 项）：preferenceKeys() 的 key 必须存在于 termux_preferences.xml（否则隐藏静默失效）；settingsPages() 指向的 Fragment 源码必须调用 isRunning(（页面必须自行拒绝）；页面类名与 XML 资源不得重复。

约束（写入 Extension 的 KDoc）：扩展的设置项必须声明在根设置页 termux_preferences.xml 上，因为可见性 pass 只在根页运行，由第一条守卫测试兜底。

未验证：仅本机 :app:compileDebugJavaWithJavac 与 :app:testDebugUnitTest 通过（164 项，1 项为既有的 Robolectric + JDK 21 失败），未做设备验证，未跑 :app:assembleDebug（本机 NDK 缺 libc++_shared）。

## 2026-11-22 — 菜单收敛：抽屉按钮与命令面板共用 ShellCommandHost

目标（HANDOFF 第 5 节第 1 项）：同一动作只有一份实现，面板与会话抽屉行为一致。

实施：
1. 新增 commands/ShellCommandHost（实现 ShellCommandCatalog.Host），作为窗口级动作的唯一执行入口：runTerminalMenuAction 转发到 TermuxActivity.runTerminalMenuAction（原 onTerminalMenuActionSelected，改为 public 并更名），addNewSession、addNamedSession、toggleSoftKeyboard 集中在此。
2. 抽屉「设置」按钮改为 runTerminalMenuAction(SETTINGS)，不再自己 new Intent；「新建会话」点击与长按、切换键盘分别改为 addNewSession、addNamedSession、toggleSoftKeyboard；长按新建会话前先关闭抽屉，与抽屉其他按钮一致。
3. 命令目录新增 new-named-session，与抽屉长按共用实现，面板也因此可发现该动作；命令 id 提为常量 ID_NEW_SESSION / ID_NEW_NAMED_SESSION / ID_TOGGLE_KEYBOARD。
4. 会话列表项长按（重命名）保持不动：属标签级动作，不是窗口级动作；终端上下文菜单与标签级菜单的划分保持不变。

缺陷（重构过程中发现并单独提交 a650b69）：命令面板的「切换键盘」在匿名 Host 内调用 toggleSoftKeyboard()，解析到匿名类自身的方法，形成无限递归，执行该命令会 StackOverflowError；抽屉按钮使用的 onToggleSoftKeyboardRequest() 才是带「启用/禁用软键盘」与「显示/隐藏软键盘」两种配置的实现。修正后两条入口行为一致。该缺陷此前未被测试覆盖，因 ShellCommandCatalog 需要 Context 才能构建，未新增单元测试。

未验证：抽屉三个按钮、面板 new-named-session、切换键盘在设备上的表现未验证；本机仅通过 :app:compileDebugJavaWithJavac 与 :app:testDebugUnitTest（164 项，1 项为既有 Robolectric + JDK 21 失败）。

## 2026-11-23 — X1 控制器拆分第一轮：动作映射表与抽屉控制器

目标（todo.md 4.1，HANDOFF 第 5 节第 1 项）：把动作分发与抽屉逻辑从 TermuxActivity 移出，且分发可测。

1. TerminalActionRunner（commands/）：Host 接口 22 个无参方法 + EnumMap<TerminalMenuAction, Runnable>，run(action) 查表执行，未登记的动作记日志而不是抛异常；has 判空。Activity 的 93 行 switch 删除，runTerminalMenuAction 只做委托；TermuxActivity 实现 Host（多数方法原本已有，新增 scrollToPrevious/NextCommand、splitScreenVertically/Horizontally、closeSplitScreen、selectUrl、shareTranscript、shareSelectedText、autofillUsername/Password、resetTerminal、killSession、openHelp、openSettings、reportIssue，并把 showStylingDialog、toggleKeepScreenOn 改为 public）。
2. TerminalActionRunnerTest（3 项，纯 JVM）：记录型假 Host 驱动全部 22 个动作，断言每个动作恰好触发一个宿主方法且方法名符合期望表；断言 handles 覆盖全部动作；断言期望表数量等于枚举数量。新增动作而漏登记会直接失败。
3. TerminalDrawerController（terminal/）：迁移 updateDrawerColors/applyDrawerColors、setUpDrawerHandle、applyDrawerHandleConfig、positionDrawerHandle、applyDrawerConfig、openSessionsDrawer、updateDrawerSummary 与五个颜色 getter，字段 mDrawerConfig 与四个颜色/一个 Drawable 一并移入；公开 API 为 setUp/reloadConfig/openDrawer/updateColors/updateSummary/setSessionsListAdapter 与五个 getter。
4. TermuxActivity：字段改为 final 并在初始化器中创建（reloadProperties 在 setContentView 之前调用 reloadConfig，控制器全部方法容忍视图未创建）；reloadProperties 改为 mDrawerController.reloadConfig()；onCreate 的三段抽屉初始化（配色、把手、开合模式）与抽屉打开监听收进 setUp()；openSessionsDrawer 与 updateDrawerColors 保留为一行委托（前者是 Host 与外部入口，后者由 TermuxTerminalSessionActivityClient 调用）。
5. TermuxSessionsListViewController：构造参数改为 (activity, sessions, TerminalDrawerController)，行背景与文字色从控制器读取，不再从 Activity 取四个 getter；控制器通过 setSessionsListAdapter 回调 notifyDataSetChanged。
6. 效果：TermuxActivity 2544 → 2275 行；删除 getDrawerBackgroundColor 等四个仅供抽屉使用的公开 getter；会话列表适配器对 Activity 的依赖收窄为「当前会话与关闭抽屉」。

未验证：抽屉配色、把手位置、打开刷新、开关配置在真机上未验证；本机仅 :app:compileDebugJavaWithJavac 通过、:app:testDebugUnitTest 167 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。抽屉控制器无单元测试，因它直接依赖 Activity 视图。

## 2026-11-23 — 评估结论与第一批 fork 特性迁移（状态栏）

本轮完成「评估 + fork 特性提取第一批（状态栏）+ API 索引文档」。

一、评估结论（详见 docs/EXTENSION_API.md §10）：
- 状态栏可迁：依赖面小，控制器只依赖 Activity 的一个回调。
- 通知体系需要「事件源/卡片槽位」贡献点，且依赖状态栏配置，下一批。
- 分屏、终端搜索需要「视图装饰器/槽位」贡献点（布局中的一段视图由扩展接管），设计前先汇总三处用途，避免为单特性造接口。
- 终端菜单（纯规则 + 对话框）与音量键（Activity 内独立代码）可小步先迁。
- VT 级行为（OSC 52/133、1004、悬停、滚动保持）无法拆包，只能 builtin.vt 属性开关。

二、新增贡献点（98e63e2）：Extension.onStart()/onStop()/onReload() 默认空实现；ExtensionManager.onStart/onStop/onReload 逐扩展转发并做异常隔离，onReload 先重读 ForkProperties。用途：定时刷新类扩展只在活动可见时工作，属性或配色变化后重新读配置。

三、迁移前置：
1. StatusBarColors 下沉并改名 widget/ColorSchemeColors（d45ef42）：该解析器被对话框、通知卡片、设置页颜色项共用，不属状态栏私有；同步更新 6 处引用与 KDoc。
2. StatusBarController 去 Activity 化（ce694b6）：改为 (Context, StatusBarView, SegmentListener)，分段点击改由监听器接收。

四、状态栏迁移（5e7ebbb）：
- app/statusbar/ 6 文件 + StatusBarPreferencesFragment 迁入 ext/statusbar/，测试同步迁移；包声明与全部引用更新。
- 新增 StatusBarExtension：id=statusbar，默认启用，preferenceKeys=["status-bar"]（XML 补该 key），settingsPages 声明状态栏页；onEnable 接管布局预留的 R.id.status_bar_view 并创建控制器，onStart/onStop 控制定时刷新，onReload 重读配置，onDisable 停止并清空视图；分段点击动作（Wi-Fi/电池/流量/应用设置/新会话）随功能迁入扩展。
- 宿主查询：TermuxActivity.getStatusBarConfig() 经 ExtensionHost.extension(ID) 取实例读 getConfig()，禁用时返回 null，终端不再为状态栏留白；扩展改变可见性后调用新的公开方法 TermuxActivity.onStatusBarLayoutChanged() 让宿主重算窗格内边距与通知卡片内距。
- 设置页闸门：StatusBarPreferencesFragment 首行检查 isRunning，未启用显示停用说明。
- TermuxPropertyPreferenceDataStore 由包内可见改为 public abstract，供跨包扩展页继承。
- SettingsSearchIndex 移除手写的状态栏页（改由扩展声明）。

五、API 索引（docs/API_INDEX.md）：登记扩展框架、命令与动作、终端控制器、颜色与属性、设置页基础设施、通知、已迁移扩展、测试守卫、常见重复实现清单；配套在 AGENTS.md 增加「新增 API 前先查索引」的约束。目的：防止后续会话重复实现同一能力。

效果：TermuxActivity 2275 → 2209 行；状态栏成为第一个带视图、定时器与设置页的可禁用扩展。

未验证：状态栏在真机上的显示、位置（上/下）、overlay 与内边距、分段点击、禁用后停止刷新与留白消失均未验证；本机 :app:compileDebugJavaWithJavac 通过，:app:testDebugUnitTest 167 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。

## 2026-11-23 — 小步迁移：终端菜单提取为 menu 扩展

按评估结论执行第一个小步迁移：终端菜单（`ext.menu`）。

实施：
1. 包整体迁移：`app/terminal/menu/*`（TerminalMenuAction/TerminalMenuConfig/TerminalMenuDialog/TerminalMenuItems）→ `ext/menu/`；设置页 `TerminalMenuPreferencesFragment` 与两个有序列表偏好项 `TerminalMenuItemsPreference`、`SelectionToolbarItemsPreference` 一并迁入；三个测试同步迁到 `app/src/test/java/com/termux/app/ext/menu/`。包声明与全仓引用、XML 中的自定义控件类名同步更新。
2. 新增 `TerminalMenuExtension`：id=menu，默认启用，preferenceKeys=["terminal-menu"]（根设置页 XML 补该 key），settingsPages 声明菜单页；onEnable/onReload 解析配置，onDisable 清空；`showMenu()` 用宿主公开方法（getCurrentSession、getTerminalView、isSplitScreenEnabled、getNotificationManager、runTerminalMenuAction）组装状态并弹菜单；`getConfig()` 在禁用时返回空配置，供文本选择工具条查询。
3. 宿主：删除 `mTerminalMenuConfig`、`reloadTerminalMenuConfig()` 与 `showTerminalMenu()` 的 25 行实现，改为向 `ExtensionHost` 取实例后转发；`getTerminalMenuConfig()` 同样转发（禁用时返回空配置，选择工具条不再显示本分支动作）；新增公开访问器 `getNotificationManager()`。
4. 设置页闸门：`TerminalMenuPreferencesFragment` 首行检查 `isRunning`，未启用显示停用说明。
5. `SettingsSearchIndex` 删除手写的菜单页，改由扩展声明；`PreferenceResetSourceTest` 的路径映射同步更新。

效果：`TermuxActivity` 2209 → 2194 行；菜单成为第二个带设置页与宿主查询的可禁用扩展；禁用手语义为「长按/右键/菜单键不再打开该菜单，选择工具条退回默认」。

未验证：菜品项顺序与样式、长按与右键触发、硬件菜单键、文本选择工具条动作在真机上的表现未验证；本机 :app:compileDebugJavaWithJavac 通过，:app:testDebugUnitTest 167 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。`TerminalMenuExtension` 无单测（直接依赖 Activity 视图）。
