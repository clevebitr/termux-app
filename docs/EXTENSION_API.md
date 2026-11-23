# EXTENSION_API — `app/` 扩展化改造可行性研究

> 目标：在新增功能（命令面板、菜单收敛、右键转发、虚拟按键入口）之前，先把 `app/` 的代码与架构规范化，
> 并让本 fork 新增的功能都能以「扩展包」形式存在：功能只通过 API 与底层 UI 框架或终端核心交互，
> 用户可以查看、启用与禁用各扩展包。
> 前置文档：`docs/APP_ARCHITECTURE_REVIEW.md`（`app/` 现状评估）、`app/DECISIONS.md`（基线纠正与豁免）。

---

## 0. 结论摘要

| 问题 | 结论 |
| --- | --- |
| 完全在 `app/` 内实现「扩展包 + 用户禁用」是否可行 | **可行**，覆盖本 fork 约 6 成功能（状态栏、通知卡片、长按菜单、分屏容器、抽屉、备份、设置页、音量键、面板按钮等） |
| 「所有功能都只通过 API 与底层交互」是否可行 | **不完全可行**。VT 级功能（OSC 52/133、焦点与悬停上报、滚动位置保持、搜索高亮、终端内边距）实现在只读模块内部，只能以属性开关暴露，无法被抽取成独立包 |
| 推荐方案 | **A：单模块包级扩展 + 运行时注册表**，包结构按未来可拆模块的方式命名；VT 级功能登记为 `builtin` 扩展，仅可禁用其行为 |
| 不推荐方案 | Gradle 多模块（编译期组合、用户无法动态增删，且拆分成本高）、动态加载 APK/dex（签名、权限、安全与调试成本） |
| 前置工作 | 拆 `TermuxActivity`（2447 行 / 124 方法）为四个控制器；建立 `ext.api` 契约与 `ExtensionHost`；再迁第一个扩展包试点 |
| 试点建议 | 已完成 `ext.backup`（最小闭环）；下一个为 `ext.statusbar`（6 文件 1961 行） |

---

## 1. 现状证据

### 1.1 fork 功能的分层归属

按 `FORK.md` 的 31 项特性（§1–§32）逐项定位实现位置，得到三类：

| 层 | 代表功能 | 证据 |
| --- | --- | --- |
| `app/`（应用层） | 状态栏 §19、通知卡片与事件 §20/§23/§24、长按菜单 §21、设置页 §22、分屏容器 §18、抽屉与手势 §10、备份 §6、音量键 §11、命令面板（待建） | `statusbar/` 6 文件 1961 行；`notifications/` 13 文件 2704 行；`terminal/menu/` 3 文件 494 行；`fragments/settings/termux/` 15 文件 2014 行 |
| `termux-shared`（只读） | extra-keys 面板 §10.1、上滑提示 §12、按键块背景 §13、属性解析 | `extrakeys/` 与 `settings/properties/` 合计 3894 行；`ExtraKeysView`、`TermuxPropertyConstants`（130 个键常量） |
| `terminal-view` / `terminal-emulator`（只读） | OSC 52 §16、OSC 133 §17、焦点上报 §9.3、滚轮实时坐标 §31、悬停上报 §32、滚动位置保持 §30、搜索高亮 §15、终端内边距 §14、斜体 §8 | `TerminalEmulator.java`（OSC52、1004）；`TerminalView.java`（keepScrollPosition、MOUSE_HOVER、selection toolbar）；`TerminalViewClient.java`（`onShowContextMenu` 等） |

**关键结论**：本 fork 的 VT 级功能**已经写在只读模块内部**，`app/` 只能通过属性与公开回调间接控制。
因此「所有功能都通过 API 交互」在 VT 层无法落地为「扩展包」，只能落地为「能力开关」。

### 1.2 `app/` 的结构性障碍

| 障碍 | 数据 |
| --- | --- |
| 入口类过载 | `TermuxActivity` 2447 行、124 方法、38 字段、109 import |
| 无动作抽象 | 同一个动作在选项菜单、抽屉按钮、长按菜单、extra-keys 各写一份 |
| 静态与进程级状态 | `SystemEventReceiver`、4 个偏好 DataStore 单例 |
| 测试覆盖层次 | 136 项 JVM 测试集中在配置/控制器；Activity 级不可测 |
| 构建验证 | `:app:compileDebugJavaWithJavac` 通过；`:app:testDebugUnitTest` 135/136（1 项 Robolectric 4.10 + JDK 21 崩溃） |

---

## 2. 需求拆解

1. **规范化**：把 `app/` 的入口类按职责拆开，动作与状态有唯一来源，新增功能不再堆进 `TermuxActivity`。
2. **扩展 API**：定义契约，使功能只依赖契约与只读模块的公开 API；不得在功能内部直接读写 Activity 字段。
3. **独立扩展包**：每个 fork 功能一个包（未来可平移为 Gradle 模块）。
4. **用户可管理**：设置页列出扩展包（名称、说明、状态、依赖），可启用/禁用；禁用后界面与行为还原到上游语义。

非目标（本阶段不做）：

- 不支持用户安装第三方扩展 APK；
- 不改变只读模块的对外契约（除非另行豁免）；
- 不追求「所有功能都能热拔插」——`core` 层（会话、终端视图、菜单容器）不可禁用。

---

## 3. 方案对比

| 方案 | 做法 | 启用/禁用 | 工作量 | 风险 | 结论 |
| --- | --- | --- | --- | --- | --- |
| **A 包级扩展 + 运行时注册表** | `app/ext/api` 定义契约，`app/ext/<feature>` 各功能一个包，`ExtensionHost` 在 Activity 启动时按启用状态注册贡献点 | 运行时（SharedPreferences 开关） | 中（契约 1 周 + 试点 3–5 天 + 逐包迁移） | 低中：单模块内包边界靠约定与代码评审维持 | **采纳** |
| B Gradle 多模块 | 每个扩展一个 `:ext:<name>` 模块 | 仅编译期；禁用只能靠开关，模块仍在包内 | 大（改 `settings.gradle`、依赖注入、资源合并） | 高：与只读模块的资源/主题耦合、构建时间上升 | 暂不采纳，保留为后续演进 |
| C 动态加载（DexClassLoader/插件 APK） | 扩展以独立 APK 或 dex 分发 | 运行时装/卸 | 很大 | 高：签名与权限校验、反射限制、崩溃隔离、无法用于商店分发 | 不采纳 |

**A 的关键设计**：包名与依赖方向按 B 的形态设计（`ext.api` 不被任何扩展反向依赖，扩展之间通过 `requires()` 声明依赖），
这样后续若拆成模块，契约与代码不需要重写。

---

## 4. 扩展 API 草案

### 4.1 契约

```java
package com.termux.app.ext.api;

/** 一个扩展包。实现类必须有无参构造。 */
public interface Extension {
    @NonNull String id();                     // "statusbar"
    @StringRes int titleRes();
    @StringRes int summaryRes();
    /** 依赖的扩展 id；任一未启用时不启用本扩展。 */
    @NonNull List<String> requires();
    /** 是否允许用户禁用；core 扩展返回 false。 */
    default boolean isUserDisableable() { return true; }
    /** 默认是否启用；用于首次运行与配置损坏时的回退。 */
    default boolean isEnabledByDefault() { return false; }

    void onEnable(@NonNull ExtensionContext context);
    void onDisable();
}
```

```java
public interface ExtensionContext {
    @NonNull Activity activity();
    @NonNull Properties properties();          // ForkProperties.readTermuxProperties()
    @NonNull SharedPreferences extensionSettings();
    @NonNull ExtensionContributions contributions();
    @NonNull ExtensionLogger log();            // 统一前缀与错误隔离
}
```

```java
/** 贡献点。所有方法可在 onEnable 中调用，注册即生效；onDisable 时由宿主统一回收。 */
public interface ExtensionContributions {
    void addTerminalMenuItems(@NonNull List<TerminalMenuItem> items);
    void addCommands(@NonNull List<PaletteCommand> commands);        // 命令面板（待建）
    void addSettingsEntries(@NonNull List<SettingsEntry> entries);
    void addDrawerButtons(@NonNull List<DrawerButton> buttons);
    void addViewDecorators(@NonNull List<ViewDecorator> decorators); // 仅限 app 层容器
    void addSessionObserver(@NonNull SessionObserver observer);
    void addPropertyDefaults(@NonNull Map<String, String> defaults); // 仅用于读取时的默认值
    void addTerminalTextFilters(@NonNull List<TerminalTextFilter> filters); // OSC/链接等纯文本过滤
}
```

### 4.2 宿主

```java
public final class ExtensionHost {
    void register(@NonNull Extension extension);      // 内置清单，编译期确定
    void applyEnabledState();                          // 读设置 → 拓扑排序 requires → onEnable/onDisable
    void setEnabled(@NonNull String id, boolean on);   // 用户操作：写入设置并即时应用
    @NonNull List<ExtensionStatus> statuses();         // 供设置页展示（id、标题、依赖、是否可用）
}
```

规则：

- **拓扑排序**：按 `requires()` 排序；依赖未启用时本扩展置为「不可用」并在设置页说明原因；
- **错误隔离**：`onEnable` 抛异常时记录日志并把该扩展标记为失败，不影响其它扩展与主界面；
- **回收**：`onDisable` 必须自行移除视图与监听，宿主在 Activity 销毁时统一调用一次；
- **重启无关**：不要求重启应用；但终端视图类功能（属性驱动）需在下次创建会话/视图时生效，设置页给出提示。

### 4.3 用户管理

- 新增设置页「扩展」：列表项显示名称、摘要、依赖、开关；`core` 扩展显示「不可禁用」；
- 状态存储：`SharedPreferences("termux-extensions")`，键 `enabled.<id>`；默认取 `isEnabledByDefault()`；
- 禁用语义分三档：
  1. 贡献点类（菜单项、命令、抽屉按钮、设置项）：立即从 UI 移除；
  2. 视图类（状态栏、通知卡片）：移除视图并停止刷新定时器；
  3. 属性类（VT 级功能）：把属性默认值改为关闭，并在下次创建终端视图时生效（提示用户）；
- 依赖禁用：若扩展 A 依赖 B，禁用 B 时提示「将同时停用 A」并级联禁用。

---

## 5. 扩展包清单与拆分顺序

| 包 | 内容 | 现状规模 | 依赖 | 建议阶段 |
| --- | --- | --- | --- | --- |
| `core` | 会话、终端视图、抽屉容器、菜单宿主、命令面板壳 | `TermuxActivity` 拆分后 | — | 0（不可禁用） |
| `ext.statusbar` | 状态栏分段、配置、绘制 | 6 文件 1961 行 | `core` | 1（试点） |
| `ext.notifications` | 通知卡片、策略、OSC 9/99/777、后台通知、历史 | 13 文件 2704 行 | `core`、`ext.statusbar`（可选） | 2 |
| `ext.menu` | 长按菜单项扩展、选择浮条 | 3 文件 494 行 | `core`、命令面板 | 3 |
| `ext.split` | 分屏容器与分隔条 | 已含在 `terminal/` | `core` | 3 |
| `ext.drawer` | 抽屉外观、把手、开合方式 | `terminal/TermuxSessionsListViewController`、`drawer_handle` | `core` | 3 |
| `ext.backup` | 备份/恢复入口 | 1 文件 199 行 | `core` | 4 |
| `ext.volumekeys` | 音量键动作 | `TermuxTerminalViewClient` 内分支 | `core` | 4 |
| `ext.search` | 终端内搜索 UI（VT 高亮留在核心） | `TerminalSearchController` 323 行 | `core` | 4 |
| `builtin.vt` | VT 级能力开关（OSC52/133、1004、悬停、滚动保持、内边距） | 只读模块内 | — | 4（仅属性开关，不可拆包） |
| `core.commands` | 命令面板（新功能） | 待建 | `core` | 与规范化并行 |

拆分原则：先拆「控制反转成本低、界面独立」的功能（状态栏），再拆「事件源多、与终端交互密」的功能（通知、搜索）。

---

## 6. 规范化前置工作

| 步骤 | 内容 | 产出 | 验收 |
| --- | --- | --- | --- |
| N1 | 抽 `TerminalMenuController`：菜单项构建、动作分发、可用性判定从 `TermuxActivity.showTerminalMenu` 移出 | `app/commands/` 或 `app/terminal/menu/` | 菜单行为等价；`TermuxActivity` 减少约 120 行 |
| N2 | 抽 `DrawerController`：抽屉开合、把手、按钮监听、会话列表配色 | `app/terminal/drawer/` | 抽屉功能等价 |
| N3 | 抽 `SessionTabsController`：会话切换、重命名、关闭、分屏切换 | `app/terminal/session/` | 会话操作等价 |
| N4 | 抽 `ActivityAppearanceController`：日夜模式、状态栏/导航栏颜色、全屏 | `app/` | 外观行为等价 |
| N5 | 建立 `ext.api` 与 `ExtensionHost`，`TermuxActivity` 只保留接线 | `app/ext/api` | 空宿主上线，行为不变 |
| N6 | 迁移 `ext.statusbar` 作为试点 | `app/ext/statusbar` | 状态栏可启用/禁用；禁用后无残留视图与定时器 |

每步单独提交；N1–N4 只做搬迁不改行为，用现有 136 项测试与设备清单验证。

---

## 7. 里程碑与估算

| 里程碑 | 内容 | 估算 | 依赖 |
| --- | --- | --- | --- |
| X0 | 扩展契约 + 宿主 + 设置页「扩展」（空列表） | 4–5 天 | 无 |
| X1 | N1–N2（菜单与抽屉控制器） | 4 天 | X0 契约可先落地 |
| X2 | `ext.statusbar` 迁移 + 用户开关 | 3–4 天 | X0、X1 |
| X3 | `ext.notifications` 迁移 | 5–6 天 | X2 |
| X4 | 命令面板 + 菜单收敛 + 右键转发（原计划的三个功能） | 6–8 天 | X1 |
| X5 | 其余扩展（menu/split/drawer/backup/volume/search） | 8–10 天 | X3、X4 |
| X6 | `builtin.vt` 属性开关与设置页说明 | 2–3 天 | X0 |

总计约 5–6 周（单人），其中 X0–X2 是最小可验收闭环（扩展 API 上线 + 一个功能可禁用）。

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 扩展包边界靠约定，易被绕过 | 包间直接引用私有实现，扩展化名存实亡 | 评审规则：扩展只允许引用 `ext.api`、只读模块公开 API 与自身包；后续可用 Gradle 模块化强制 |
| 贡献点顺序不确定 | 菜单项/命令顺序漂移 | 贡献点带 `order` 与 `id`，宿主统一排序；冲突时日志告警 |
| 禁用不彻底 | 残留视图、定时器、监听导致耗电或崩溃 | 每个扩展的 `onDisable` 必须在试点中逐项验收（视图移除、定时器取消、监听注销） |
| VT 级功能无法拆包 | 用户预期「所有功能都能禁用」落空 | 设置页把 `builtin.vt` 明确标注为「核心能力，仅可关闭行为」 |
| 只读模块限制 | 无法为扩展提供更细粒度 API | 需要时按 `AGENTS.md` 流程向需求方申请豁免，或在 `app/` 侧做包装（如右键转发的子类方案） |
| 测试不足 | 扩展启用/禁用组合多，回归困难 | 为 `ExtensionHost` 补纯 JVM 测试（拓扑排序、级联禁用、错误隔离）；Activity 级用手工清单 |
| Robolectric 4.10 与 JDK 21 不兼容 | 本机 1 项测试失败，削弱验证 | 单独提交升级 Robolectric 4.12+ |

---

## 9. 实施记录（2026-11-23，X0）

| 项 | 状态 |
| --- | --- |
| `ext.api`：`Extension`、`ExtensionContext` | 已实现 |
| `ext.host`：`ExtensionSpec`、`ExtensionState`、`ExtensionStatus`、`ExtensionGraph`、`ExtensionPreferences`、`ExtensionManager` | 已实现；`ExtensionGraph` 为纯 Java，7 项 JVM 测试通过 |
| 扩展登记表 `ForkExtensions` | 已建立，当前为空 |
| 设置页「扩展」 | 已实现（程序化构建），开关写 `SharedPreferences("termux-extensions")`，禁用被依赖项时弹确认 |
| `TermuxActivity` 接线 | `onCreate` 末尾创建并应用、`onResume` 重新应用、`onDestroy` 释放 |
| 贡献点（菜单项/命令/设置项/视图装饰器/会话观察者） | 已定义 `Extension.preferenceKeys()`（设置项可见性）；其余贡献点在对应功能迁移时再加，避免空接口 |
| 第一个扩展 `ext.backup` | 已提取（`app/ext/backup/`，含 `BackupExtension`、`TermuxBackupUtils`、`BackupPreferencesFragment`）；默认启用，禁用后设置项隐藏 |
| 命令面板入口 | 快捷键 `Ctrl+Shift+P`；抽屉头部按钮（`palette_button`）；终端菜单项 `command-palette`（因默认菜单列表定义在只读模块，需在 `terminal-menu-items` 中显式加入）；虚拟按键 `F12`（`TermuxTerminalViewClient.onKeyDown` 拦截无修饰的 F12） |
| 终端右键转发 | 新增 `app/terminal/TermuxTerminalFrame`：包装 `TerminalView`（该视图在只读模块中是 final，无法继承），在 `dispatchTouchEvent` 中检测 `SOURCE_MOUSE + BUTTON_SECONDARY`，若 `mEmulator.isMouseTrackingActive()` 则用 `getColumnAndRow()` + `sendMouseEvent(2, …)` 转发并消费，否则交回视图弹现有终端菜单；布局改为 Frame 包裹 TerminalView |
| 命令面板（第一切片） | 新增 `app/commands/`：`ShellCommand`（标题、分组、快捷键、可用性、执行闭包）、`ShellCommandCatalog`（复用 `TerminalMenuItems` 规则生成命令）、`ShellCommandPalette`（可搜索对话框，过滤规则为纯函数并有 4 项测试）；入口为 `Ctrl+Shift+P`（`TermuxActivity.dispatchKeyEvent`） |
| 菜单规则下沉 | 新增 `terminal/menu/TerminalMenuItems`：可见性与可用性改为纯函数（`State` 快照 + `TitleResolver`），`TermuxActivity.showTerminalMenu` 改为调用它；8 项 JVM 测试 |

已知限制：

- `:app:assembleDebug` 在本机失败于 NDK 链接（`unable to find library -lc++_shared`），属构建环境问题，与本次改动无关；
  `:app:compileDebugJavaWithJavac`、`:app:testDebugUnitTest` 可执行，测试 143 项中 1 项为既有的 Robolectric 4.10 + JDK 21 失败。
- 扩展设置页为程序化构建，暂不进入设置搜索索引（索引只读 XML 页面）。

## 9.1 实施记录（M-E1 扩展闸门层）

问题：X0 的禁用语义只有「把根设置页上的一个 key 设为不可见」。设置搜索索引有独立的静态页面表（硬编码备份页），导航只按 `getFragment()` 匹配、不检查 `isVisible()`，页面本身也不检查扩展状态，因此禁用后仍可从搜索进入并完整使用功能。

实施：

| 项 | 内容 |
| --- | --- |
| 唯一状态源 | `ext.host.ExtensionHost`：进程级单例，`statuses()`/`stateOf()`/`isRunning()`/`disabledIds()`/`dependentsOf()`/`setEnabled()`；`ExtensionManager`、`ExtensionsPreferencesFragment`、`SettingsSearchIndex`、`SettingsSearchNavigation`、备份页闸门全部改为读它 |
| 设置页贡献点 | `ext.api.ExtensionSettingsPage`（`titleRes`、`xmlRes`、`fragmentClassName`）与 `Extension.settingsPages()`；`SettingsSearchIndex` 的页面表 = 核心页 + 扩展声明的页，条目携带 `extensionId` |
| 搜索过滤 | `build()` 末尾用纯函数 `withoutDisabledExtensions(entries, host.disabledIds())` 丢弃禁用扩展的条目（2 项测试） |
| 导航二次校验 | `SettingsSearchNavigation.open` 打开前要求条目可见且所属扩展处于运行态，防止用旧快照或直接构造的条目绕过 |
| 页面闸门 | `BackupPreferencesFragment.onCreatePreferences` 先查 `ExtensionHost.isRunning(BackupExtension.ID)`，未启用则只显示占位说明，不提供备份、恢复与命令安装 |
| 空分类收起 | `applyPreferenceVisibility` 在隐藏条目后逐级隐藏没有可见子项的分类，避免留下空标题 |
| 守卫测试 | `ExtensionRegistrySourceTest` 3 项：声明的 key 必须存在于 `termux_preferences.xml`；声明的页面源码必须调用 `isRunning(`；页面类名与 XML 资源不得重复 |

约束：扩展的设置项必须声明在根设置页上，因为可见性 pass 只在根页运行；新增扩展页时，页面必须自行做闸门，隐藏入口不构成禁用。

## 10. 迁移评估与执行顺序（2026-11-24）

### 10.1 逐特性评估

| 特性 | 现状 | 规模 | 迁移所需前置 | 状态 |
| --- | --- | --- | --- | --- |
| 状态栏 | `ext/statusbar/`（原 `app/statusbar/`） | 7 文件 2413 行 | 生命周期回调、控制器去 Activity 化、共享颜色解析下沉 | **已迁移** |
| 备份恢复 | `ext/backup/` | 3 文件 380 行 | 无 | 已迁移 |
| 通知体系 | `notifications/` 13 文件 2704 行 | 大 | 事件源贡献点；依赖状态栏配置（卡片需避开状态栏）；`TerminalNotificationManager` 需拆分「卡片/后台通知/历史」 | 待迁移 |
| 分屏 | `terminal/TerminalSplitScreenController` + Activity 布局代码 | 中 | 视图装饰器贡献点（窗格容器由扩展接管或宿主提供槽位） | 待迁移 |
| 终端内搜索 | `terminal/TerminalSearchController` + 布局中的搜索条 | 中 | 同上（布局条目需可隐藏） | 待迁移 |
| 终端菜单 | `terminal/menu/` 4 文件 590 行 | 小 | 无（纯规则 + 对话框），但默认列表在只读模块内，只能以属性开关表达 | 待迁移 |
| 会话抽屉 | `terminal/TerminalDrawerController` 等 | 中 | 会话列表视图与抽屉布局在 Activity 中 | 待迁移 |
| 音量键动作 | Activity 中约 80 行 | 小 | 无 | 待迁移 |
| VT 级行为（OSC 52/133、1004、悬停、滚动保持） | 只读模块内 | — | 无法拆包，只能 `builtin.vt` 属性开关 | 不迁移 |

### 10.2 本轮新增的贡献点

| 贡献点 | 位置 | 用途 |
| --- | --- | --- |
| `Extension.onStart()` / `onStop()` | `ext/api/Extension.java` | 定时刷新类扩展在活动可见时才工作（状态栏的 tick） |
| `Extension.onReload()` | 同上 | 属性或配色变化后重新读取配置 |
| `ExtensionManager.onStart/onStop/onReload` | `ext/host/ExtensionManager.java` | 宿主转发上述回调，逐个扩展做异常隔离 |

### 10.3 状态栏迁移要点

1. 包整体迁移：`app/statusbar/*` → `ext/statusbar/*`，设置页 `StatusBarPreferencesFragment` 一并迁入；测试同步迁到
   `app/src/test/java/com/termux/app/ext/statusbar/`。
2. `StatusBarColors` 不属于状态栏：它被对话框、通知卡片与设置页颜色项共用，改名为 `widget/ColorSchemeColors` 下沉。
3. `StatusBarController` 去掉 `TermuxActivity` 依赖，改为 `(Context, StatusBarView, SegmentListener)`；分段的点击动作
   （Wi-Fi/电池/流量/应用设置、新会话）随功能一起迁入扩展。
4. 宿主与扩展的单向查询：`TermuxActivity.getStatusBarConfig()` 通过 `ExtensionHost.get(this).extension(ID)` 取实例再读
   `getConfig()`；扩展禁用时返回 `null`，终端不再为状态栏留白，行为自然正确。宿主只允许调用扩展的**配置查询**方法，
   不得调用其界面方法。
5. 扩展每次改变视图可见性或高度后调用 `TermuxActivity.onStatusBarLayoutChanged()`，由宿主重算窗格内边距与通知卡片内距。

### 10.4 下一批的进入条件

- **通知体系**：需要先定义「事件源」贡献点（命令结束、应用事件）与「卡片容器槽位」，否则迁移后仍要从 Activity 取视图；
  状态栏已提供 `StatusBarExtension.getConfig()`，卡片内距可直接查询。
- **分屏与搜索**：需要「视图装饰器/槽位」贡献点，让扩展接管 `activity_termux.xml` 中的一段布局，禁用时该段回到宿主默认
  （隐藏或不占空间）。设计前先确认三处用途（分屏容器、搜索条、通知叠层），避免为单个特性造接口。
- **终端菜单与会话抽屉**：菜单规则已是纯函数，可先做 `ext.menu`（属性开关 + 设置页），抽屉随后。
- **音量键**：Activity 中独立，无依赖，可作为「小步验证迁移流程」的候选。

## 11. 未验证声明

- 本文件为可行性研究与设计草案，**未编写任何扩展 API 或迁移代码**；
- `app/` 的现状结论来自静态阅读与 `:app:compileDebugJavaWithJavac`、`:app:testDebugUnitTest`（135/136）结果；
- 工作量估算基于当前代码规模与历史提交节奏，未经实际实施校准；
- 设备侧行为（状态栏刷新、通知卡片、右键转发）未验证。
