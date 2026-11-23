# API 索引 — 已有能力清单（新增前必须查阅）

> 目的：把 `app/` 中**已经存在**的 API 与工具类集中登记，避免重复实现同名能力（命令、偏好、颜色解析、闸门、
> 菜单规则等）。新增任何一个类、接口或工具方法之前，先在本表按关键词检索；命中则复用，不命中才新增，并在
> 本表补一行。
>
> 维护规则：
> 1. 只登记**被多处使用或对外承诺**的 API；纯内部实现细节不登记。
> 2. 行格式固定为「类 / 路径 / 职责 / 不要重复做什么」。
> 3. 新增或移动类时同步更新本表，与代码同一提交或紧随的 `docs:` 提交。
> 4. 本表过期等同于没有：路径写错视为缺陷。

---

## 1. 扩展框架（`com.termux.app.ext`）

| 类 | 路径 | 职责 | 不要重复实现 |
| --- | --- | --- | --- |
| `Extension` | `ext/api/Extension.java` | 扩展契约：`id`/`titleRes`/`summaryRes`/`requires`/`isUserDisableable`/`isEnabledByDefault`/`preferenceKeys`/`settingsPages`/`onEnable`/`onStart`/`onStop`/`onReload`/`onDisable` | 不要再给扩展加「启用状态存储」「依赖解析」等成员，这些属于宿主 |
| `ExtensionContext` | `ext/api/ExtensionContext.java` | 扩展可见的宿主能力：`activity()`、`properties()`、`extensionSettings()`、`logInfo`/`logWarn` | 不要在扩展里直接访问 `TermuxActivity` 字段 |
| `ExtensionSettingsPage` | `ext/api/ExtensionSettingsPage.java` | 设置页声明：`titleRes`、`xmlRes`、`fragmentClassName` | 不要在 `SettingsSearchIndex` 里手写扩展页面 |
| `ExtensionViewSlot` | `ext/api/ExtensionViewSlot.java` | 布局区域声明：`viewId`、`viewName`、`description`；配合 `Extension.slots()` | 不要在宿主里另行接管同一视图，也不要接管终端视图与抽屉结构 |
| `ExtensionLayoutHost` | `ext/api/ExtensionLayoutHost.java` | 宿主实现 `requestExtensionLayout()`，供扩展请求重排 | 不要再加 `onXxxLayoutChanged()` 之类按特性命名的钩子 |
| `ExtensionHost` | `ext/host/ExtensionHost.java` | **扩展状态唯一来源**：`installed`/`statuses`/`stateOf`/`isRunning`/`disabledIds`/`extension`/**`find(Class)`**/**`findRunning(Class)`**/`slotOwner`/`dependentsOf`/`setEnabled` | 不要再写「读 SharedPreferences 判断扩展是否启用」的代码，也不要再用 `instanceof` 强转取扩展实例 |
| `ExtensionManager` | `ext/host/ExtensionManager.java` | 扩展生命周期：`apply`/`onStart`/`onStop`/`onReload`/`destroy`，异常隔离 | 不要在 Activity 里逐个调用扩展的 `onEnable`/`onStart` |
| `ExtensionGraph` | `ext/host/ExtensionGraph.java` | 纯函数依赖解析：拓扑顺序、缺依赖、依赖环、级联 | 不要另写依赖排序 |
| `ExtensionPreferences` | `ext/host/ExtensionPreferences.java` | 偏好层：`enabled.<id>` 读写、`applyPreferenceVisibility`（含空分类收起） | 不要另写「隐藏禁用扩展的设置项」 |
| `ForkExtensions` | `ext/ForkExtensions.java` | 本构建的扩展登记表 | 不要在别处 `new` 扩展实例 |
| 已实现扩展 | `ext/statusbar/`、`ext/backup/` | 状态栏、备份恢复 | 功能实现前先确认是否已被某个扩展拥有 |

**闸门规则**：禁用扩展 = 入口不可达 + 运行时闸门。扩展提供的每个设置页都必须在 `onCreatePreferences` 首行检查
`ExtensionHost.get(context).isRunning(<ID>)`，否则 `ExtensionRegistrySourceTest` 失败。

---

## 2. 命令与动作（`com.termux.app.commands`）

| 类 | 路径 | 职责 | 不要重复实现 |
| --- | --- | --- | --- |
| `TerminalActionRunner` | `commands/TerminalActionRunner.java` | `TerminalMenuAction` → 宿主方法的唯一映射（22 个动作），`run`/`handles` | 不要新增第二个 `switch (action)` 分发 |
| `TerminalActionRunner.Host` | 同上 | 动作的宿主实现（`showTerminalSearch`、`splitScreenVertically`…） | 不要在别处定义同名动作方法 |
| `ShellCommand` | `commands/ShellCommand.java` | 面板条目：id、标题、分组、快捷键、可用性、执行闭包 | — |
| `ShellCommandCatalog` | `commands/ShellCommandCatalog.java` | 由 `TerminalMenuItems` 规则生成命令；含 `new-session`/`new-named-session`/`toggle-keyboard` | 不要在面板里手写动作列表 |
| `ShellCommandHost` | `commands/ShellCommandHost.java` | 窗口级动作唯一入口：面板、抽屉按钮、会话新建共用 | 不要在抽屉按钮里另写 `Intent`/`addNewSession` |
| `ShellCommandPalette` | `commands/ShellCommandPalette.java` | 可搜索命令面板；过滤为纯函数 | 不要再写一个「命令列表对话框」 |
| `TerminalMenuAction` | `ext/menu/TerminalMenuAction.java` | 终端菜单动作枚举与默认列表（只读模块定义默认值） | 不要修改既有 id，只允许新增 |
| `TerminalMenuItems` | `ext/menu/TerminalMenuItems.java` | 动作可见性/可用性的纯函数 + `State` 快照 | 不要在 UI 代码里判断「没有选中文本就隐藏」 |
| `TerminalMenuConfig` / `TerminalMenuDialog` | `ext/menu/` | `terminal-menu-items` 等属性解析与菜单对话框 | 不要另写菜单对话框；由 `ext.menu` 扩展拥有 |
| `TerminalMenuItems.State` | 同 `TerminalMenuItems` | 构造一次快照后复用（面板与菜单共用） | 不要各入口各自拼装状态 |

---

## 3. 终端窗口与视图控制器（`com.termux.app.terminal`）

| 类 | 路径 | 职责 | 不要重复实现 |
| --- | --- | --- | --- |
| `TerminalDrawerController` | `ext/drawer/TerminalDrawerController.java` | 抽屉配色、把手、开合模式、头部概要；`setUp`/`reloadConfig`/`openDrawer`/`updateColors`/`updateSummary`/`setSessionsListAdapter` | 不要在 Activity 或适配器里另算抽屉颜色与把手位置 |
| `TerminalSearchController` | `ext/search/TerminalSearchController.java` | 终端内搜索栏与搜索历史 | 不要另写搜索栏 |
| `TerminalSplitScreenController` | `ext/split/TerminalSplitScreenController.java` | 分屏状态与两窗格布局 | 不要在 Activity 里直接算分屏布局参数 |
| `TermuxTerminalFrame` | `terminal/TermuxTerminalFrame.java` | 包装 `TerminalView`，转发鼠标右键 | 不要去改只读模块的 `TerminalView` |
| `TermuxSessionsListViewController` | `ext/drawer/TermuxSessionsListViewController.java` | 抽屉会话列表适配器（依赖 `TerminalDrawerController`） | 不要另写会话列表行布局逻辑 |
| `TermuxTerminalViewClient` | `terminal/TermuxTerminalViewClient.java` | 终端视图事件（按键、软键盘、颜色、属性重载） | 不要另写「切换软键盘」实现，用 `onToggleSoftKeyboardRequest()` |
| `TermuxTerminalSessionActivityClient` | `terminal/TermuxTerminalSessionActivityClient.java` | 会话增删与切换（`addNewSession`、`renameSession`） | 不要直接操作 `TermuxService` 的会话列表 |
| `SessionDetails` | `ext/drawer/SessionDetails.java` | 前台进程名、工作目录、短详情 | 不要另写 `/proc` 解析 |
| `DrawerConfig` / `DrawerHandleView` / `DrawerColorUtils` | `ext/drawer/` | `drawer-*` 属性与把手视图 | 不要另解析 `drawer-*` |

---

## 4. 颜色、属性与配置

| 类 | 路径 | 职责 | 不要重复实现 |
| --- | --- | --- | --- |
| `ColorSchemeColors` | `widget/ColorSchemeColors.java` | 解析 `#rgb`/`#argb`/`#rrggbb`/`#aarrggbb` 与 `theme:<name>` 引用（背景、前景、光标、color0-255） | 不要在别处解析颜色字符串或用 `Color.parseColor` 处理主题引用 |
| `ForkProperties` | `properties/ForkProperties.java` | 读取 `termux.properties` 中**不在共享库列表内**的 fork 属性 | 不要各自 `Properties.load` |
| `TermuxAppSharedProperties` | `termux-shared`（只读） | 共享库已解析的属性（终端边距、内边距等） | 不要重复解析共享库已覆盖的键 |
| `StatusBarConfig` | `ext/statusbar/StatusBarConfig.java` | `status-bar-*` 解析结果（含 `Segment` 与动作常量） | 不要另解析 `status-bar-*` |
| `NotificationConfig` | `ext/notifications/NotificationConfig.java` | `notification-*` 解析结果 | 不要另解析 `notification-*` |

---

## 5. 设置页基础设施（`com.termux.app.fragments.settings`）

| 类 | 路径 | 职责 | 不要重复实现 |
| --- | --- | --- | --- |
| `SettingsSearchIndex` | `fragments/settings/SettingsSearchIndex.java` | 设置搜索索引：核心页常量 + 扩展声明的页；`build`/`search`/`withoutDisabledExtensions` | 不要在搜索里手写页面清单或再写过滤 |
| `SettingsSearchNavigation` | `fragments/settings/SettingsSearchNavigation.java` | 打开搜索结果并高亮；打开前校验扩展状态与条目可见性 | 不要绕过它直接 `startActivity` 打开设置页 |
| `PreferenceResetUtils` | `fragments/settings/termux/PreferenceResetUtils.java` | 长按复位单项设置 | 不要另写长按复位 |
| `PreferenceAccentUtils` | `fragments/settings/PreferenceAccentUtils.java` | 设置页强调色 | 不要另写强调色 |
| `TermuxPropertyPreferenceDataStore` | `fragments/settings/termux/TermuxPropertyPreferenceDataStore.java` | 偏好项 ↔ `termux.properties` 键（公开抽象基类，供跨包继承） | 不要另写属性读写桥 |
| `SettingsActivity` / `SettingsPreferenceFragment` | `fragments/settings/` | 设置入口与页面容器 | 不要新建设置 Activity |

---

## 6. 通知与事件（`com.termux.app.ext.notifications`）

| 类 | 路径 | 职责 | 不要重复实现 |
| --- | --- | --- | --- |
| `TerminalNotificationManager` | `ext/notifications/TerminalNotificationManager.java` | 卡片、后台通知与历史的总调度；`Listener` 回调 | 不要在别处直接展示通知卡片 |
| `NotificationOverlayView` | `ext/notifications/NotificationOverlayView.java` | 卡片叠层与布局（含状态栏内距） | 不要另算卡片位置 |
| `RecentNotifications` / `RecentNotificationsDialog` | `ext/notifications/` | 最近通知列表与对话框 | 不要另写历史列表 |
| `OscNotificationHandler` / `OscRateLimiter` | `ext/notifications/` | OSC 9/99/777 解析与限流 | 不要另写 OSC 通知解析 |
| `ExitCodeNotifier` / `AppEventNotifier` / `BackgroundNotifier` | `ext/notifications/` | 失败命令、应用事件、后台通知 | 不要另写通知触发通道 |

---

## 7. 扩展功能实现（已迁移）

| 扩展 | 路径 | 提供的能力 | 宿主接口 |
| --- | --- | --- | --- |
| `statusbar` | `ext/statusbar/` | `StatusBarConfig`/`StatusBarView`/`StatusBarController`/`StatusBarMetrics`/`SparklineView`/`StatusBarPreferencesFragment`，`StatusBarExtension.getConfig()` 供宿主查询 | 槽位 `status_bar_view`；`ExtensionLayoutHost.requestExtensionLayout()` |
| `builtin.vt` | `ext/builtin/` | `BuiltinVtExtension`（**不可禁用**的核心扩展）与 `BuiltinVtPreferencesFragment`；收纳只能在只读模块内实现的行为开关 | 设置页 `builtin-vt`；不要把可关闭的 fork 功能放进这里 |
| `split` | `ext/split/` | `TerminalSplitScreenController`（窗格排列、分隔条拖拽、活动窗格边框）与 `SplitScreenExtension`；槽位 `terminal_panes_container`/`terminal_split_divider` | `TermuxActivity.setSplitScreen()`（会话挂载仍由宿主负责） |
| `search` | `ext/search/` | `TerminalSearchController`、`TerminalSearchPreferences`、`SearchExtension`；槽位 `terminal_search_bar` | `TermuxActivity.showTerminalSearch()`/`hideTerminalSearch()`/`refreshTerminalSearch()` |
| `drawer` | `ext/drawer/` | `TerminalDrawerController`、`TermuxSessionsListViewController`、`DrawerConfig`/`DrawerHandleView`/`DrawerColorUtils`、`SessionDetails`、`DrawerExtension`；槽位 `left_drawer`/`drawer_handle` | `TermuxActivity.getDrawer()`（布局访问）、`openSessionsDrawer()` |
| `notifications` | `ext/notifications/` | `TerminalNotificationManager`（卡片/后台通知/历史）、三个事件适配器、叠层视图与设置页；`NotificationsExtension` 提供事件转发与 `getManager()` | `TermuxActivity.getTerminalNotificationManager()`、`onCommandFinished`/`onBell`/`onOscNotification` 等转发方法 |
| `volume-keys` | `ext/volumekeys/` | `VolumeKeyActions`（动作派发，`Host` 接口）、`VolumeKeysExtension.perform(...)`、`VolumeKeysPreferencesFragment`；宿主侧状态机留在 `TermuxTerminalViewClient` | `ExtensionHost.isRunning()` 闸门 + `VolumeKeyActions.Host` |
| `menu` | `ext/menu/` | `TerminalMenuAction`/`TerminalMenuItems`/`TerminalMenuConfig`/`TerminalMenuDialog`/`TerminalMenuExtension`/`TerminalMenuPreferencesFragment` 与两个有序列表偏好项；`getConfig()` 供宿主查询选择工具条动作 | `TermuxActivity.showTerminalMenu()`、`getTerminalMenuConfig()` |
| `backup` | `ext/backup/` | `TermuxBackupUtils`（脚本解压与执行）、`BackupPreferencesFragment` | 无 |

---

## 8. 测试守卫（新增功能前后必看）

| 测试 | 路径 | 守卫内容 |
| --- | --- | --- |
| `ExtensionRegistrySourceTest` | `app/src/test/java/com/termux/app/ext/` | 扩展声明的 key 必须存在于 `termux_preferences.xml`；声明的设置页源码必须调用 `isRunning(`；页面声明唯一 |
| `ForkExtensionsTest` | 同上 | 扩展 id 唯一、依赖存在、`preferenceKeys` 唯一非空 |
| `ExtensionGraphTest` / `ExtensionHost` 相关 | `ext/host/` | 依赖拓扑、级联、缺依赖、依赖环 |
| `TerminalActionRunnerTest` | `commands/` | 22 个动作全部有映射且映射到唯一宿主方法 |
| `TerminalMenuItemsTest` / `TerminalMenuConfigTest` / `TerminalMenuActionTest` | `ext/menu/`（测试） | 菜单可见性、可用性与属性解析 |
| `SettingsSearchIndexTest` | `fragments/settings/` | 搜索排序与禁用扩展过滤 |
| `PreferenceResetSourceTest` | `fragments/settings/termux/` | 每个设置页都安装长按复位监听（页面清单是「类名 → 路径」映射，移动页面必须同步） |
| `TerminalActionRunnerTest` / `ShellCommandPaletteTest` | `commands/` | 注意 `ShellCommandCatalog` 需要 `Context`，当前无单测 |

---

## 9. 常见重复实现清单（历史踩坑）

| 想做的事 | 已有实现 | 备注 |
| --- | --- | --- |
| 判断扩展是否启用 | `ExtensionHost.get(ctx).isRunning(id)` | 不要读 `SharedPreferences("termux-extensions")` |
| 隐藏禁用扩展的设置项 | `ExtensionPreferences.applyPreferenceVisibility` | 已含空分类收起 |
| 搜索里过滤禁用扩展 | `SettingsSearchIndex.build` | 已按扩展状态过滤 |
| 打开命令面板 | `TermuxActivity.showCommandPalette()` | 四个入口共用 |
| 执行终端菜单动作 | `TerminalActionRunner.run(action)` | 不要新增 switch |
| 显示终端上下文菜单 | `TermuxActivity.showTerminalMenu()`（转交 `ext.menu`） | 不要在别处再建菜单对话框 |
| 切换软键盘 | `ShellCommandHost.toggleSoftKeyboard()` → `onToggleSoftKeyboardRequest()` | 直接调 Activity 的私有同名方法会递归 |
| 实现音量键动作 | `ext.volumekeys.VolumeKeyActions.perform(...)` | 不要在 `TermuxTerminalViewClient` 里再写 `switch (action)` |
| 解析颜色 / 主题引用 | `ColorSchemeColors.resolve` | dialogs、通知、设置页共用 |
| 打开设置页 | `SettingsActivity` + `SettingsSearchNavigation` | 不要 `startActivity` 绕过校验 |
| 抽屉颜色 | `TerminalDrawerController.get*Color()` | 不要在适配器里重算 |
| 打开/关闭抽屉 | `DrawerExtension.openDrawer()`/`closeDrawer()`（`TermuxActivity.openSessionsDrawer()` 转发） | 不要直接操作 `DrawerLayout` 的开合 |
| 取扩展实例 | `ExtensionHost.get(ctx).findRunning(XxxExtension.class)` | 不要写 `instanceof` 强转，`findRunning` 同时完成「是否启用」判断 |
