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

## 2026-11-23 — 小步迁移：音量键动作提取为 volume-keys 扩展

按评估顺序完成第二个小步迁移：音量键（`ext.volumekeys`）。

切分原则：状态机留在输入层，动作实现进扩展。
- `TermuxTerminalViewClient` 保留 `handleVirtualKeys`/`handleVolumeKey`/`setVirtualVolumeKeyDown`，因为它们参与 `readControlKey()`/`readFnKey()` 的虚拟修饰键判断，属于输入层。
- 新增 `VolumeKeyActions`（`ext/volumekeys`）：`perform(action, isVolumeUp)` 只做属性值到宿主行为的派发，`Host` 接口提供 toggleSoftKeyboard/toggleExtraKeys/scroll*/switchTo*/addNewSession/paste/sendKeySequence/sendHardwareKey；音量调节用 AudioManager 就地完成。
- 新增 `VolumeKeysExtension`（id=volume-keys，默认启用，preferenceKeys=["volume-key-actions"]，settingsPages 声明音量键页）；`perform(context, host, action, isVolumeUp)` 每次由调用方传入 Host，扩展不保存视图或客户端引用；`onEnable/onDisable` 无资源，注释说明原因。
- 闸门：`handleVirtualKeys` 首行检查 `ExtensionHost.get(mActivity).isRunning(VolumeKeysExtension.ID)`，禁用后返回 false，音量键回到系统字面音量行为。
- 设置页拆分：从 `termux_keyboard_preferences.xml` 抽出 6 个 `volume-*` 偏好项 + 复位入口到新页 `termux_volume_keys_preferences.xml`，新增 `VolumeKeysPreferencesFragment`（含闸门与数据存储），根设置页新增 `app:key="volume-key-actions"` 条目；`TerminalKeyboardPreferencesFragment` 与其数据存储删除音量键相关代码；`PreferenceResetSourceTest` 登记新页。

效果：音量键动作（约 150 行 switch）离开视图客户端；`ext/` 现有 4 个扩展（statusbar/menu/volumekeys/backup）；`TermuxActivity` 行数不变（该切片不涉及 Activity）。

未验证：音量键的单按/长按动作、虚拟修饰键、禁用后的字面音量行为、设置页复位与 `send-keys` 条件显示均未在真机验证；本机 :app:compileDebugJavaWithJavac 通过，:app:testDebugUnitTest 167 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。`VolumeKeyActions` 无单测（依赖 AudioManager 与 Host 实现）。

## 2026-11-23 — 视图槽位贡献点：ExtensionViewSlot 与通用布局请求

按建议顺序先做「视图槽位」贡献点，为通知叠层、分屏容器与搜索条三处迁移解锁，避免为单个特性造接口。

新增 API：
- `ext/api/ExtensionViewSlot`：`viewId`、`viewName`、`description`。`viewName` 与 id 冗余，因为单元测试无法把资源 id 反查为名字，而守卫测试只能靠名字核对布局；KDoc 已写明理由。
- `Extension.slots()`：默认空列表，声明扩展接管的布局区域。
- `ExtensionContext.view(int)`：扩展从宿主取槽位视图，泛型返回，避免扩展自行搜索 Activity。
- `ExtensionContext.requestExtensionLayout()` + `ext/api/ExtensionLayoutHost`：扩展改变槽位高度或可见性后请求宿主重排；由 Activity 实现，Context 在宿主未实现时静默跳过，因此 ext.host 不需要依赖 TermuxActivity。
- `ExtensionHost.slotOwner(int)`：判断某视图属于扩展还是宿主核心，供宿主与后续迁移使用。

宿主侧：
- `TermuxActivity` 实现 `ExtensionLayoutHost`，新增 `requestExtensionLayout()`（= setTerminalPadding + updateNotificationOverlayLayout），删除按特性命名的 `onStatusBarLayoutChanged()`。
- `StatusBarExtension` 声明槽位 `status_bar_view`，改用 `context.view(...)` 取视图、`context.requestExtensionLayout()` 请求重排，并保存 `ExtensionContext` 以脱离对 Activity 类型的布局依赖。

守卫测试（新增 2 项）：槽位必须存在于 `activity_termux.xml`（改名不会静默失效）；槽位 id 与名字不得重复、必须有描述。

克制点（写入文档）：不引入「扩展自行 inflate 布局」与「宿主预留空容器」两种更重机制。三处候选视图都由宿主布局提供，扩展只接管行为与显示，因此槽位只需「声明归属 + 取视图 + 请求重排」。

未验证：真机上状态栏布局与内边距在改回通用钩子后是否与之前一致未验证；本机 :app:compileDebugJavaWithJavac 通过，:app:testDebugUnitTest 169 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。

## 2026-11-23 — 迁移通知体系：notifications 扩展与事件转发

按既定顺序迁移通知体系（`ext.notifications`），并修正此前的评估结论。

评估修正：原先设想新增「事件源贡献点」，实际检查后发现事件只有唯一的消费者（通知扩展），因此沿用「宿主向扩展的单向查询」模式即可，不为单消费者造事件总线。宿主侧只在产生事件的调用点通过 `ExtensionHost.extension(ID)` 取实例并调用其转发方法。

实施：
1. 包迁移：`app/notifications/*`（13 文件）→ `ext/notifications/`，`NotificationPreferencesFragment` 一并迁入并加闸门；7 个测试迁到 `app/src/test/java/com/termux/app/ext/notifications/`；布局中的自定义控件全限定名、`SettingsSearchIndex` 的手写页面条目、`PreferenceResetSourceTest` 路径同步更新。
2. 新增 `NotificationsExtension`：id=notifications，默认启用，slot=`notification_overlay`，preferenceKeys=["notifications"]，settingsPages 声明通知页；onEnable 接管叠层并创建 `TerminalNotificationManager` 与 `ExitCodeNotifier`/`OscNotificationHandler`/`AppEventNotifier`，onReload 重读属性并重算内距，onDisable 停止管理、清空并隐藏叠层。
3. 宿主改为转发：`TermuxTerminalSessionActivityClient` 删除三个适配器字段，改为在事件点查询扩展并调用 `onCommandFinished`/`onTitleChanged`/`onSessionFinished`/`onOscNotification`/`onOsc99Close`/`onOsc99AliveQuery`/`onBell`/`isOscIconsEnabled`；禁用时事件被丢弃。
4. Activity 删除 `mNotificationOverlay`、`mNotificationManager`、`mTestNotificationCount`、`setUpNotificationOverlay()`、`onTerminalNotificationClicked()`；`showRecentNotifications()`/`sendTestNotification()` 变为转发；`getTerminalNotificationManager()` 返回扩展的管理器，供终端菜单状态与 `getNotificationManager()` 使用；叠层内距计算移入扩展，经 `ExtensionHost` 查询 `StatusBarExtension.getConfig()`。
5. 效果：`TermuxActivity` 2194 → 2084 行；`ext/` 现有 5 个扩展（statusbar/menu/notifications/volumekeys/backup）。

未验证：卡片位置与内距、四种测试通知、OSC 图标开关、后台通知与历史在禁用后的停止行为，均未在真机验证；本机 `:app:compileDebugJavaWithJavac` 通过，`:app:testDebugUnitTest --rerun-tasks` 169 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。注意：增量执行该测试任务时会漏跑部分迁移后的测试类，需加 `--rerun-tasks` 才能得到真实数量，已记录在此避免误判。

## 2026-11-23 — 迁移搜索与抽屉扩展，分屏不按小步处理

本轮把剩余的小功能扩展一次做完：`ext.search` 与 `ext.drawer`；`ext.split` 经检查不属于小步，单独说明。

一、`ext.search`（7a47843）
- `TerminalSearchController`、`TerminalSearchPreferences` 从 `terminal/` 迁到 `ext/search/`，`TerminalSearchPreferencesTest` 同步迁移。
- 新增 `SearchExtension`：id=search，默认启用，slot=`terminal_search_bar`，无设置页（选项与历史存于 `TerminalSearchPreferences`，不写 `termux.properties`）；onEnable 创建控制器并 `setUp()`，onDisable 隐藏并释放。
- 宿主：删除 `mTerminalSearchController` 字段与创建代码，`showTerminalSearch()`/`hideTerminalSearch()`/`isTerminalSearchBarVisible()`/`refreshTerminalSearch()` 改为查询扩展后转发。

二、`ext.drawer`（08f64b7）
- 迁入 `ext/drawer/`：`TerminalDrawerController`、`TermuxSessionsListViewController`、`DrawerConfig`、`DrawerHandleView`、`DrawerColorUtils`、`SessionDetails`；4 个测试同步迁移。
- 新增 `DrawerExtension`：id=drawer，默认启用，槽位 `left_drawer` 与 `drawer_handle`；onEnable 创建控制器、装配、按属性解析并绑定会话列表适配器；onReload 重新解析配置与配色；onDisable 解绑适配器并关闭抽屉。
- `TerminalDrawerController` 新增 `getSessionsListView()`、`closeDrawer()`、`isOpen()`、`setLockMode()`，使扩展不必直接操作 `DrawerLayout`。
- 宿主：删除 `mDrawerController` 与 `mTermuxSessionListViewController` 字段、`setTermuxSessionsListView()`；`updateDrawerColors()`/`openSessionsDrawer()`/`termuxSessionListNotifyUpdated()` 改为转发；服务连接后调用 `DrawerExtension.onSessionsAvailable()` 绑列表（扩展在服务连接前启用，此时会话列表尚不可用，这是迁移中发现的顺序约束）。
- 保留 `TermuxActivity.getDrawer()` 作为布局访问器，因为视图客户端、虚拟键与抽屉按钮仍有 16 处需要开合或锁模式。

三、`ext.split` 不按小步处理
经检查，分屏不是「一个控制器」：副窗格 `mTerminalViews[1]` 的创建、副窗格会话的查找与创建、`ARG_SPLIT_SCREEN_*` 的状态保存与恢复、活动窗格切换与 `ON_*` 常量分散在 Activity 约 200 行内，且与会话生命周期耦合。要迁移必须先决定「副窗格视图归扩展还是归宿主」，属设计决策而非机械搬迁，因此留待设备验证后再评估。

结果：扩展增至 7 个（statusbar/menu/notifications/volumekeys/search/drawer/backup）；`TermuxActivity` 2084 → 2089 行（搜索与抽屉的转发方法略多于被删除的创建代码，但功能实现已全部离开终端包）。

未验证：搜索条与抽屉在真机上的显示、把手位置、列表绑定时机（服务连接后）与禁用后的行为未验证；本机 `:app:compileDebugJavaWithJavac` 通过，`:app:testDebugUnitTest --rerun-tasks` 169 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。

## 2026-11-23 — 迁移分屏扩展，扩展查询收敛为类型化查找

本轮两件事：扩展查询 API 收敛（767c575），分屏迁移（596e946）。

一、`ExtensionHost.find/findRunning`
- 新增 `find(Class<T>)` 与 `findRunning(Class<T>)`：前者只按类型取实例，后者同时判断「可运行」，宿主调用点一律用后者，避免忘记状态检查而运行已禁用的功能。
- 替换 6 处 `instanceof` 强转（Activity 的 search/drawer/notifications/statusbar/menu 查询、会话客户端的通知查询、视图客户端的音量键查询、通知扩展的状态栏配置查询）。
- 约束写入 `docs/API_INDEX.md`：取扩展实例只用 `findRunning`，不再写 `instanceof` 强转。

二、`ext.split`
- `TerminalSplitScreenController` 迁入 `ext/split/`，构造参数由 `TermuxActivity` 改为 `Context`（它只用 `getDrawable` 与 `dpToPx`），测试同步迁移。
- 新增 `SplitScreenExtension`：id=split，默认启用，槽位 `terminal_panes_container` 与 `terminal_split_divider`；onEnable 创建控制器并装配分隔条拖拽；onDisable 关闭分屏、恢复单窗格布局并清除活动窗格边框；对外提供 isEnabled/setEnabled/getOrientation/setOrientation/getPaneRatio/setPaneRatio/updateLayout/updatePaneActiveStates。
- 归属划分（迁移前必须先定的设计点）：扩展拥有几何与状态（排列、比例、分隔条、边框），宿主拥有会话与视图（副窗格会话的查找与创建、`mTerminalViews` 的挂载）。因此 `TermuxActivity.setSplitScreen()` 保留为编排入口，分屏请求在扩展缺失时直接忽略；`saveInstanceState`/恢复同样经由扩展读写方向与比例。
- 效果：`ext/` 现含 8 个扩展（statusbar/menu/notifications/volumekeys/search/drawer/split/backup），`terminal/` 包只剩 6 个文件。

未验证：分屏的排列、拖拽比例、活动窗格边框、状态保存与恢复、禁用后回到单窗格，均未在真机验证；本机 `:app:compileDebugJavaWithJavac` 通过，`:app:testDebugUnitTest --rerun-tasks` 169 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。

## 2026-11-23 — 交付 builtin.vt 核心行为页并修正该里程碑设想

交付 `builtin.vt` 核心行为设置页（4053e59），并修正该里程碑的原始设想。

一、事实核对
原计划写「仅属性开关（OSC 52/133、1004、悬停、滚动保持、内边距）」，实际检查发现：
- 已有属性的：`terminal-osc52-clipboard`、`mouse-hover-sends-mouse-motion`、`keep-scroll-position-on-output`、四个 `terminal-padding-*`；
- **没有属性开关的**：OSC 133 与焦点上报 1004，二者行为固定在只读模块与宿主代码里，要提供开关必须先改只读模块（需书面豁免）。因此本轮不实现这两项，已在文档标注。

二、实现
1. 新页面 `termux_builtin_vt_preferences.xml`：顶部不可选中的说明项「核心能力，仅可关闭行为」，随后两个分类——行为（滚动保持、鼠标悬停、OSC 52）与内边距（上/下/左/右）。上述 6 项从 `termux_terminal_view_preferences.xml`（5 项）与 `termux_keyboard_preferences.xml`（悬停）迁出，原页面相应分类清空后删除。
2. 新增 `BuiltinVtExtension`（`ext/builtin/`）：id=`builtin.vt`，`isUserDisableable()` 返回 false（宿主永远视为启用），`isEnabledByDefault()` true，`preferenceKeys=["builtin-vt"]`，`settingsPages` 声明新页；无视图与事件。
3. `BuiltinVtPreferencesFragment`：使用 `TermuxPropertyPreferenceDataStore` 子类写属性，安装长按复位与搜索高亮；首行仍做 `isRunning` 检查并显示停用说明——今天不可达，但把该页与可禁用扩展的页面统一，避免将来把 `builtin.vt` 改成可禁用后留下过时页面。
4. 根设置页新增 `app:key="builtin-vt"` 条目（沿用终端视图图标）；`PreferenceResetSourceTest` 登记新页；搜索索引无需改动，新页由扩展声明后自动进入索引。

三、边界
`builtin.vt` 只收纳「实现位于只读模块、只能关闭行为」的开关；可关闭的 fork 功能一律属于各自扩展。这条边界已写入 `BuiltinVtExtension` 的 KDoc 与 `docs/API_INDEX.md`。

未验证：迁移后的四个内边距项与两个开关在真机上的读写、搜索能否在新页找到这些条目、长按复位是否正常，均未验证；本机 `:app:compileDebugJavaWithJavac` 通过，`:app:testDebugUnitTest --rerun-tasks` 169 项中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。

## 2026-11-23 — 真机启动崩溃：资源文件中的旧类名；补资源级守卫测试

真机试机时启动即崩溃，无弹窗；崩溃日志从系统「分享」导出到 Download/OnePlus Share/crash_log.md 后定位。

根因：资源文件里的类名是字符串，编译器不检查，包迁移后失效。
- `activity_termux.xml` 第 34 行仍写 `com.termux.app.terminal.DrawerHandleView`，实际类已迁到 `com.termux.app.ext.drawer`；
- 同一文件仍写 `com.termux.app.statusbar.StatusBarView`，实际已迁到 `com.termux.app.ext.statusbar`；
- `termux_preferences.xml` 的通知页仍写 `com.termux.app.fragments.settings.termux.NotificationPreferencesFragment`，实际已迁到 `com.termux.app.ext.notifications`。

崩溃栈：`setContentView(activity_termux)` → `InflateException` → `ClassNotFoundException: com.termux.app.terminal.DrawerHandleView`。前两处是启动崩溃，第三处是打开通知设置页时崩溃，后者的隐患被前者的崩溃掩盖了。

迁移脚本的漏洞：批量改引用时只扫了 `**/*.java`，没有扫 `src/main/res/**/*.xml`；XML 里的自定义控件标签与 `app:fragment` 值同样是类名。

修复与防线：
1. 修正三处失效类名（6d203ab）。
2. 新增守卫测试 `ResourceClassNameTest`：扫描 `src/main/res/layout`、`res/xml`、`res/menu` 下所有 XML，抽取 `com.termux.*` 形式的类名（末段首字母大写者），逐个 `Class.forName`，缺失即失败并列出文件。该测试在修复前的三条失效类名上确实失败，修复后通过。
3. 约束：以后迁移包时，必须同时更新 `res/**/*.xml` 中的自定义控件标签与 `app:fragment`；`ResourceClassNameTest` 是这条约束的机器检查。

代价与教训：此类错误编译与 169 项单测全绿也发现不了，只有真机 inflate 时才暴露，因此必须有资源级守卫。

未验证：修复后的布局 inflate、通知设置页打开均未在真机上确认；本机 `:app:compileDebugJavaWithJavac` 通过，`:app:testDebugUnitTest --rerun-tasks` 170 项（新增 1 项）中 1 项既有失败（FileReceiver + Robolectric 4.10/JDK 21）。

## 2026-11-23 — 修正抽屉选中状态与配置即时生效

真机反馈两项体验问题：抽屉会话选中状态不正确；部分配置改动需要重启 App 才生效。均已修复（3411203）。

一、抽屉选中状态
1. 行背景是 `StateListDrawable`，只在 `state_activated` 时叠加强调色，但适配器从未调用 `setActivated`，因此「当前会话行的底色」这条设计一直是死代码，只有 3dp 强调条在起作用。现在 `getView` 里按 `sessionAtRow == getCurrentSession()` 设置 `setActivated`。
2. 切换会话后列表不刷新：`setCurrentSession` 只做 `attachSession`，没有通知适配器，强调条要等抽屉重新打开才更新。现在 `attachSession` 成功后调用 `termuxSessionListNotifyUpdated()`（转发到 `DrawerExtension.notifySessionsChanged()`）。

二、配置改动即时生效
1. 根因：`reloadProperties()` 里原本有抽屉/状态栏的重载调用，迁移时被清空，扩展的状态重载只剩 `reloadActivityStyling()` 与 `updateExtraKeysViewColors()` 两处；任何不经过这两处的配置重载都不会通知扩展。
2. 现在把 `mExtensionManager.onReload()` 放进 `reloadProperties()`（所有配置重载的唯一入口），并删除 `reloadActivityStyling()` 里的重复调用，避免一次改动重载两遍。
3. `ExtensionManager.start()` 改为先把扩展放进 running 集合再调用 `onEnable`。此前扩展在 `onEnable` 期间查询宿主时（状态栏 `applyLayout()` → 宿主重算内边距 → 查询状态栏配置）会被判为「未运行」，导致首次布局按「没有状态栏」计算，要等下一次重载才正确。失败时从 running 中移除，不会留下半启动状态。
4. `DrawerExtension.onDisable()` 现在还清空会话列表的适配器，避免禁用后列表仍持有指向已释放控制器的适配器。

未验证：抽屉选中行的底色、切换会话后强调条跟随、以及各扩展设置改动后的即时生效，均未在真机确认；本机 `:app:compileDebugJavaWithJavac` 通过、`:app:testDebugUnitTest --rerun-tasks` 170 项中 1 项既有失败；CI（PR #2）lint/单测/双 ABI 构建全部通过，Fork Build 已产出 `termux-app_fork-20261123-0426-testkey_arm64-v8a.apk`。

## 2026-11-23 — 修正抽屉高亮与命名对话框，主题迁移 Material 3

真机反馈四项，前三项为缺陷、第四项为主题改造，均已提交并通过 CI（b5e8de0、0e5854a）。

一、抽屉「多个会话一起高亮」
根因：`TerminalDrawerController` 只持有一个 `Drawable` 实例，所有行 `setBackground` 同一实例。Drawable 的状态保存在实例上，因此一行的 activated/ripple 状态会画到所有行上。修正为 `newSessionRowBackground()`：用 `getConstantState().newDrawable().mutate()` 为每行生成独立副本。同一处修复同时解决了长按多个行一起亮的问题（ripple 状态共享）。

二、抽屉「只有侧边蓝条正常」
根因：行背景是 `StateListDrawable`，只在 `state_activated` 时叠加强调色，但适配器从未调用 `setActivated`。现在 `getView` 按「是否当前会话」设置 `setActivated`，强调底色与 3dp 侧条同时生效；`setCurrentSession` 成功后立即刷新列表。

三、设置会话名称对话框黑字
根因：`renameSession` 走只读模块的 `TextInputDialogUtils`，它用裸 `AlertDialog` + `EditText`，不设置任何颜色，深色配色下文字与背景同色。新增 `app/widget/TerminalTextInputDialog`：文字色取当前会话的前景色、背景取背景色、提示色为前景的半透明、窗口背景用 `TerminalDialogStyles` 的方角 1dp 描边；替换会话重命名与新建命名会话两处调用，不再使用只读模块的对话框。附带移除了新建命名会话里未使用的「failsafe」按钮分支。

四、Material 3 主题
只读模块 `termux-shared` 的主题基类仍是 Material 2，因此 app 自己声明父主题，保持主题名不变：
- `Theme.TermuxApp.Light/DagNight.DarkActionBar` → `Theme.Material3.Light` / `Theme.Material3.DayNight`（Material 1.12 没有 `DarkActionBar` 变体，非 `NoActionBar` 即带 ActionBar）；
- 定义 MD3 颜色角色：`colorPrimary/OnPrimary/PrimaryContainer/OnPrimaryContainer`、`colorSecondary` 系列、`colorOutline`、链接色；
- 新增 `ThemeOverlay.Termux.Dialog`（父 `ThemeOverlay.Material3.MaterialAlertDialog` + 0dp 圆角的 shape overlay），使平台与只读库的对话框也遵循 MD3 表面色，同时保留方角 tui 风格；
- 终端活动主题保留原有窗口/抽屉/虚拟键属性。

边界：`values-night` 中 app 只覆盖了 `Theme.TermuxActivity`，其余 DayNight 主题由 Material 3 自动切换；「全局跟随当前配色方案」目前只做到「按配色方案的明暗选择 DayNight 变体」，抽屉、对话框、状态栏等自分叉表面仍用配色方案的具体颜色。若要连 MD3 表面色也映射到配色方案，需要按配色方案生成 theme overlay（运行期 `applyStyle` 只能引用静态资源），属后续设计。

未验证：行高亮是否只作用于当前会话、长按只亮一行、命名对话框在深色配色下的可读性、MD3 主题在设置页与各对话框中的观感，均需真机确认。CI：PR #2 的 lint/单测/双 ABI 构建全部通过；Fork Build 已产出 `termux-app_fork-20261123-0453-testkey_arm64-v8a.apk`。

## 2026-11-23 — 命令面板 Material 3 重做与快捷键触发

命令面板重做（886e940）：样式改为 Material 3，快捷键可真正触发命令，并修正了错误的快捷键标签。

一、样式
- 原实现是 `AlertDialog` + 裸 `EditText` + `android.R.layout.simple_list_item_1`，没有任何主题适配。
- 新增 `res/layout/dialog_command_palette.xml`：`TextInputLayout`（FilledBox.Dense + clear_text 图标）作为头部，`ListView` 显示结果，空结果用独立提示文本。
- 新增 `res/layout/item_command_palette.xml`：标题 + 分组两行，右侧快捷键 chip；新增 `bg_command_shortcut.xml`（`colorSurfaceVariant` 圆角）与 `bg_command_palette.xml`（`colorSurface` 16dp 圆角窗口背景）。
- 全部颜色使用 MD3 主题属性（`colorOnSurface`/`colorOnSurfaceVariant`/`colorSurfaceVariant`/`colorSurface`），因此跟随 Material 3 的日/夜与配色方案明暗。
- 不可用的命令不再隐藏而是降低不透明度到 0.45，避免列表在状态变化时跳动。

二、快捷键
- 新增 `commands/ShellCommandShortcut`：把 `Ctrl+Alt+F` 这类显示文本解析为修饰键 + 主键，提供 `matches(KeyEvent)` 与 `normalize`；无法解析的标签永不匹配，避免错误标签抢走终端的按键。
- 面板打开期间按命令声明的快捷键即执行该命令（监听器装在输入框与列表上，因为焦点在输入框）。
- 过滤按快捷键归一化后精确命中的命令排在最前。

三、修正错误的快捷键标签
原 `ShellCommandCatalog` 给「上/下一条命令」标注 `Ctrl+Alt+Up/Down`，但视图客户端里这两个组合实际是**切换会话**，标签是错的。现改为：
- 搜索 `Ctrl+Alt+F`、切换键盘 `Ctrl+Alt+K`、新建会话 `Ctrl+Alt+C`（与视图客户端已有处理一致）；
- 上/下一条命令不再标注快捷键。
常量集中在 `ShellCommandCatalog.SHORTCUT_*` 并注明来源，避免标签与实现再次漂移。

测试：新增 `ShellCommandShortcutTest` 5 项（修饰键精确匹配、按字符而非物理键匹配、命名单键、无法解析不匹配、归一化顺序），总测试数 170 → 175。

未验证：面板在真机上的观感（圆角、chip、空状态、输入法弹出与列表高度）、快捷键在面板内触发、MD3 深色下的可读性，均未确认。CI：PR #2 lint/单测/双 ABI 构建全部通过；Fork Build 产出 `termux-app_fork-20261123-0521-testkey_arm64-v8a.apk`。

## 2026-11-23 — UI 层形状统一为 Material 3 圆角

按需求方三项决策执行 UI 层第一批优化：全部表面统一 Material 3 圆角；终端内对话框、设置页、抽屉、状态栏、命令面板全在范围内；MD3 表面色暂不跟随 `colors.properties`，仅日夜跟随。提交 1f0d811、c10a35c、c43fe8b、a2345f5、e39607c、70010a1、3db4815、12324f6、6d1ee61。

一、形状令牌
- 新增 `res/values/dimens.xml`：`termux_dialog_corner_radius`=16dp（对话框与浮动窗口，对应 MD3 large）、`termux_row_corner_radius`=12dp（行、卡片、抽屉会话行，对应 medium）、`termux_chip_corner_radius`=8dp、宽度/外边距/内距/按钮高度。
- Java 侧另有 `TerminalDialogStyles.DIALOG_CORNER_DP`/`ROW_CORNER_DP`：终端菜单与通知配置的默认值在解析时没有 `Context`，只能读常量。两处来源会漂移，因此新增 `ShapeTokenTest` 读取 `dimens.xml` 与常量比对。
- 默认圆角变更：`terminal-menu-corner-radius` 0 → 16dp，`notification-corner-radius` 0 → 12dp。显式设过值的用户不受影响；影响面已写入 `FORK.md` §35。

二、对话框主题修正（踩坑）
- 原实现只在 `ThemeOverlay.Termux.Dialog` 里设 `shapeAppearanceOverlay`，对对话框无效：Material 3 对话框（`MaterialAlertDialogBuilder`）的形状来自 `alertDialogStyle`（`MaterialAlertDialog.Material3`）的 `shapeAppearance`，`shapeAppearanceOverlay` 只被自身读取该属性的控件（如 `MaterialCardView`）使用。
- 现改为覆盖 `alertDialogStyle` 为 `TermuxAlertDialogStyle`（parent `MaterialAlertDialog.Material3`，`shapeAppearance` 指向 `ShapeAppearance.Termux.Dialog`），并删除 `styles.xml` 中已无引用的旧 `TermuxAlertDialogStyle`（平台 `Theme.Material.Light.Dialog.Alert`）。

三、共用构建器
- 新增 `widget/TerminalDialogBuilder`：负责窗口背景（配色方案颜色 + 令牌圆角 + 1dp 强调色描边）、标题（18sp）、自动滚动正文（超过屏幕七成滚动）、右对齐文字按钮栏与宽度上限。终端菜单、命名对话框、通知历史迁入；`Style` 封装配色、圆角、字号与等宽字体。
- 迁移中修正一处实现错误：构建器最初把窗口背景设为透明，正文没有背景，真机上将出现浮空文字；现由 `TerminalDialogStyles.createDialogBackgroundPx` 绘制窗口背景。

四、其余表面
- 设置搜索：新增 `dialog_settings_search.xml` 与 `item_settings_search.xml`，改为 Material 3 输入框（`TextInputLayout` FilledBox.Dense）+ 行布局 + 空状态；新增字符串 `settings_search_empty` 并补 `values-zh`。
- 应用级对话框改用 `MaterialAlertDialogBuilder`：终止会话、URL 选择（含长按打开）、会话数上限、风格未安装、备份恢复确认、扩展级联停用、长按复位。
- 偏好控件：`ColorPreference` 色板改用 12dp 圆角与主题 `colorPrimary`/`colorOutline` 描边，自定义颜色输入包 `TextInputLayout`；`OrderedItemsPreference` 行按下态改用令牌圆角 + `colorPrimary`，文字与分区标题取 `colorOnSurface`/`colorOnSurfaceVariant`。
- 抽屉：会话行涟漪掩码与选中底色改为 12dp 圆角，列表左右 8dp 内距，强调条改圆头，头部两个 `ImageButton` 补 `selectableItemBackgroundBorderless`，底部按钮 parent 改 `Widget.Material3.Button.TextButton`。
- 状态栏：分段 4dp 内距，可点击分段加 8dp 圆角按下态。
- 命令面板：背景与 chip 改用令牌，行高 12dp，列表高度按屏幕高度收缩（键盘弹出后不再挤压输入框）。

五、新增 API
- `TerminalDialogStyles.themeColor(Context, attr, fallback)` 统一读取主题色，禁止再手写 `obtainStyledAttributes` 或硬编码颜色。
- 已登记到 `docs/API_INDEX.md` §10，并在 §9 增加三条「不要重复实现」条目。

未验证：本机无 `adb`，全部观感（圆角、描边、标题层级、按钮按下态、设置搜索与色板、键盘弹出后的面板高度）未在真机确认。本机 `:app:compileDebugJavaWithJavac`、`:app:processDebugResources` 通过；`:app:testDebugUnitTest --rerun-tasks` 177 项、1 项既有失败（`FileReceiverActivityTest`，Robolectric 4.10 + JDK 21）。`:app:lintDebug` 仍因 NDK 缺 `libc++_shared` 无法本地执行，lint 以 CI 为准。
