# APP_ARCHITECTURE_REVIEW — `app/` 架构评估（交付基线纠正后）

> 评估对象：真实应用模块 `app/`（`com.termux`），不含只读模块 `terminal-view/`、`terminal-emulator/`、
> `termux-shared/` 的内部实现（仅在依赖关系层面计入）。
> 前置说明：`docs/UI_REVIEW.md` 与 `docs/ARCHITECTURE_REVIEW.md` 评估的是新外壳原型 `app_new/`，
> 该原型已于 2026-11-23 冻结（见 `app/DECISIONS.md`）。本文件取代两者作为后续交付的评估依据。

---

## 0. 结论摘要

| 维度 | 权重 | 评分（五分制） | 结论 |
| --- | --- | --- | --- |
| 分层与依赖方向 | 20% | 3.0 | `app → termux-shared → terminal-view/emulator` 方向清晰；`app` 内部没有分层，Activity 同时是视图、控制器与状态缓存 |
| 模块化与文件规模 | 15% | 1.5 | `TermuxActivity` 2447 行、124 个方法、38 个字段、109 个 import；另有 3 个 700–1100 行的类 |
| 状态与真源 | 15% | 3.0 | 会话真源在 `TermuxService`；Activity 缓存大量派生状态；属性读取有 `ForkProperties` 统一入口 |
| UI 一致性与可发现性 | 15% | 2.5 | 入口分散在选项菜单、抽屉、长按、extra-keys 四处；无命令面板；设置页已有搜索 |
| 可测试性 | 10% | 3.0 | 136 项 JVM 测试（JUnit4 + Robolectric），覆盖配置与控制器；Activity 级几乎不可测 |
| 扩展成本 | 15% | 3.5 | 新增属性可只改 `app/`（`ForkProperties` 直接读 raw properties）；新增 extra-key 类型受只读模块限制 |
| 健壮性 | 10% | 3.0 | 属性解析有默认值回退；错误提示有硬编码英文；静态单例与 Handler 分散 |
| **综合** | 100% | **2.8 / 5** | 功能完备、可用性经过长期验证；结构上是典型的「单 Activity + 服务」遗留形态 |

三条关键结论：

1. **体量集中在入口类**。`TermuxActivity` 2447 行占 `app/` 全部 18451 行的 13%，承担服务绑定、会话切换、
   抽屉、菜单、属性重载、键盘、分屏、状态栏、通知入口。任何新交互都要改动它，是后续改动的主要冲突点。
2. **没有命令面板，但已有设置搜索**。`fragments/settings/SettingsSearchIndex`、`SettingsSearchDialog` 已实现
   设置项搜索与跳转，可作为命令面板的现成交互与样式参照；终端菜单是 `TerminalMenuAction`（22 项）+
   `TerminalMenuDialog`（按 `terminal-menu-items` 配置排序）。
3. **扩展受限点不在 `app/`，在只读模块**。属性与终端菜单项都能在 `app/` 内扩展；只有「新增 extra-keys 键类型」
   需要改 `termux-shared`，因此命令面板的虚拟按键入口应走「保留键码 + app 侧拦截」而不是新增键类型。

---

## 1. 评估方法

| 项 | 内容 |
| --- | --- |
| 代码度量 | 按包统计文件与行数、最大文件、入口类的方法/字段/import 数、生命周期回调位置 |
| 依赖检查 | 核对 `app` 对只读模块的依赖方式（`project(":terminal-view")`、`project(":termux-shared")`）与反向引用 |
| 结构走查 | 会话生命周期（Activity ↔ Service ↔ Client）、属性体系、菜单与设置入口、错误处理与线程 |
| 测试清点 | `app/src/test/java` 26 个测试类、136 个 `@Test`；依赖 JUnit 4.13.2 + Robolectric 4.10 |
| 构建验证 | `./gradlew :app:compileDebugJavaWithJavac` 通过；`./gradlew :app:testDebugUnitTest` 136 项中 135 通过，1 项失败见第 8 节 |
| 未执行 | 设备侧运行（无 `adb`）、`:app:lintDebug`、真机手感 |

---

## 2. 结构

### 2.1 分层与依赖

```
┌──────────────────────────────────────────────────────────────┐
│ app/（本文件评估对象）                                        │
│  TermuxActivity ─ TermuxService ─ 客户端适配层               │
│  terminal/（会话、视图客户端、分屏、搜索、鼠标、菜单）        │
│  fragments/settings/（偏好页 28 个类 + 19 个 preference XML） │
│  statusbar/ notifications/ properties/ backup/ event/ widget  │
├──────────────────────────────────────────────────────────────┤
│ termux-shared（只读）：属性解析、Logger、数据工具、extra-keys │
├──────────────────────────────────────────────────────────────┤
│ terminal-view（只读）：TerminalView、TerminalViewClient       │
│ terminal-emulator（只读）：VT 解析与鼠标事件编码               │
└──────────────────────────────────────────────────────────────┘
```

依赖方向单向：`app` 引用三个库模块，库模块不引用 `app`；`terminal-view` 依赖 `terminal-emulator`。

### 2.2 包分布（`app/src/main/java/com/termux/app`）

| 包 | 文件 | 行数 | 职责 |
| --- | --- | --- | --- |
| 根 | 7 | 4500 | `TermuxActivity`（2447）、`TermuxService`（959）、安装器、Receiver 等 |
| `terminal` | 13 | 3879 | 视图/会话客户端、分屏、搜索、抽屉控制、鼠标与键序处理 |
| `notifications` | 13 | 2704 | 通知卡片、策略、注册表、限流 |
| `fragments/settings/termux` | 15 | 2014 | 外观、键盘、通知、状态栏、终端 I/O、菜单、备份等偏好页 |
| `statusbar` | 6 | 1961 | 状态栏布局、配置、度量 |
| `fragments/settings` | 9 | 947 | 偏好基类、设置搜索、重置、颜色偏好 |
| `terminal/menu` | 3 | 494 | 终端上下文菜单与动作枚举、配置 |
| `terminal/io` | 4 | 448 | extra-keys、工具栏 ViewPager |
| `activities` | 2 | 248 | 设置页与帮助页宿主 |
| `backup` / `api/file` / `event` / `properties` / `widget` / `models` | 7 | 816 | 备份恢复、文件接收、系统事件、fork 属性读取、桌面组件 |
| 合计 | 83 | 18451 | |

### 2.3 最大的文件

| 文件 | 行数 | 主要职责 |
| --- | --- | --- |
| `TermuxActivity.java` | 2447 | 入口、生命周期、会话切换、抽屉、菜单、属性、键盘、分屏、颜色 |
| `terminal/TermuxTerminalViewClient.java` | 1046 | 键序编码、鼠标语义、属性读取、命令分发 |
| `TermuxService.java` | 959 | 前台服务、会话生命周期、通知、执行意图 |
| `terminal/TermuxTerminalSessionActivityClient.java` | 774 | 会话创建/切换/重命名、视图挂载、尺寸同步 |
| `statusbar/StatusBarMetrics.java` | 530 | 状态栏度量与布局计算 |
| `notifications/NotificationCardView.java` | 520 | 通知卡片绘制与交互 |

---

## 3. 度量

| 指标 | 数值 | 说明 |
| --- | --- | --- |
| 主源规模 | 83 文件 / 18451 行 | Java，无 Kotlin |
| `TermuxActivity` 方法数 | 124 | 平均每个方法约 20 行，方法数量本身即职责信号 |
| `TermuxActivity` 字段数 | 38 | 含 2 个 `TerminalView`、服务引用、抽屉、菜单配置、状态栏等 |
| `TermuxActivity` import 数 | 109 | 覆盖 8 个 `app` 子包与 3 个只读模块 |
| 生命周期回调 | `onCreate` 350、`onStart` 464、`onResume` 500、`onDestroy` 571 | `onCreate` 约 114 行 |
| 线程/异步点 | 13 处 `Handler`/`ExecutorService`/`Thread`/`runOnUiThread` | 分散在 Activity、Service 与客户端 |
| 静态可变单例 | `SystemEventReceiver` 与 4 个偏好 DataStore | 进程级状态，测试需要额外重置 |
| 用户可见硬编码英文 | `TermuxTerminalExtraKeys:128/140/147` | `Logger.showToast` 直接拼接英文字符串 |
| 无障碍标注 | 4 处 `setContentDescription` | 自定义视图（状态栏、通知卡）覆盖不足 |
| 布局资源 | 6 个 layout、19 个 preference XML | 界面主要由偏好 XML + 代码构建 |
| 测试 | 26 类 / 136 项 | JUnit 4.13.2 + Robolectric 4.10，其中 4 个类使用 Robolectric |

---

## 4. 架构发现

| 编号 | 级别 | 发现 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| A-1 | P1 | 入口类承担 9 类职责：服务绑定、会话切换、抽屉、菜单、属性、键盘、分屏、状态栏、颜色 | `TermuxActivity.java`（124 方法） | 按职责抽出控制器：`SessionTabsController`、`DrawerController`、`MenuController`、`ActivityLifecycle`；本轮先抽「命令面板」与「菜单」两块 |
| A-2 | P1 | 菜单入口分散为四类：选项菜单（直接弹终端菜单）、抽屉头部/底部按钮、会话长按菜单、extra-keys | `TermuxActivity:1524`、`activity_termux.xml`、`TermuxSessionsListViewController`、`TermuxTerminalExtraKeys` | 新增命令面板作为统一入口；旧入口保留为快捷方式并逐步指向面板 |
| A-3 | P1 | 没有命令式动作抽象：动作散落在 `showTerminalMenu` 的 `switch`、抽屉按钮监听、`option` 菜单 | `TermuxActivity:1547` 起 | 引入「命令 + 目录」模型（标题、分组、快捷键、可用性、执行闭包），面板、菜单、extra-keys、快捷键共用同一份定义 |
| A-4 | P1 | 右键固定弹菜单，不区分终端鼠标跟踪状态 | `TerminalView.onTouchEvent`（只读）→ `TermuxTerminalViewClient:577` | 在 `app/` 新增 `TerminalView` 子类：跟踪开启时用 `mEmulator.sendMouseEvent(2, col, row, pressed)` 转发，否则 `super` 保留现有菜单 |
| A-5 | P2 | 客户端类体积过大：`TermuxTerminalViewClient` 1046 行混合键序、鼠标、属性、命令 | 同上 | 抽出 `KeySequenceEncoder`、`MouseEventEncoder`、`TerminalViewProperties`；本轮只动鼠标分支，避免大改 |
| A-6 | P2 | 静态可变单例与进程级状态（`SystemEventReceiver`、偏好 DataStore） | `event/SystemEventReceiver.java:21` 等 | 保持现状，新增代码不再引入静态单例；命令面板使用实例状态 |
| A-7 | P2 | 错误提示硬编码英文 | `TermuxTerminalExtraKeys:128/140/147` | 迁入 `strings.xml`（本轮顺带修正） |
| A-8 | P2 | 无障碍标注不足（4 处） | `statusbar/`、`notifications/` | 新增面板按钮与菜单项时全部带 `contentDescription`；面板列表项使用 `android:accessible` 语义 |
| A-9 | P3 | 属性体系存在两套读取路径：`TermuxSharedProperties`（内部属性列表）与 `ForkProperties`（raw 读取） | `app/properties/ForkProperties.java` | 现状可接受且是扩展优势；新属性一律走 `ForkProperties`，避免改只读模块 |
| A-10 | P3 | Robolectric 版本过旧（4.10）在本机 JDK 21 下 `ShadowActivityThread.reset` 崩溃 | `app/build.gradle:184` | 升级到 4.12+ 后可消除该失败；属依赖变更，单独提交 |

---

## 5. 与 `app_new` 评估的对照

| 先前结论（`app_new`） | 在 `app/` 是否成立 | 说明 |
| --- | --- | --- |
| 设置页缺搜索、缺分组卡片 | **不成立** | `app/` 已有 `SettingsSearchIndex` / `SettingsSearchDialog` / `SettingsSearchNavigation` |
| 无命令面板、菜单入口分散 | **成立** | 且入口比原型更多（选项菜单、抽屉、长按、extra-keys） |
| 导航状态多真源、返回键多处注册 | **部分成立** | `app/` 用 `DrawerLayout` + 系统返回，没有显式返回栈，但 `onBackPressed` 分支集中在 `TermuxActivity:1959` |
| 视图模型过载 | **转化为** Activity 过载 | 无 ViewModel 层，派生状态直接存在 Activity 字段中 |
| 配置写入非原子 | **不适用** | `app/` 的配置由 `termux-shared` 的偏好体系写入，本轮不涉及 |
| 会话列表状态失真 | **不成立** | 会话真源在 `TermuxService`，列表直接绑定 `TermuxSession` |
| 终端核心只读导致限制 | **同样成立** | 右键转发必须用子类包装；extra-keys 新键类型需改只读模块 |
| 文件规模预算可执行 | **难度更高** | `TermuxActivity` 拆分涉及大量字段与回调，需要分步骤并逐项验证 |

综合评分从原型的 3.7 下调到 2.8，主要差在模块化（1.5）、UI 一致性与可发现性（2.5）；
扩展成本反而优于原型（3.5），因为属性与菜单项都能在 `app/` 内扩展。

---

## 6. 可扩展性

### 6.1 扩展场景矩阵（`app/` 视角）

| 场景 | 需改文件 | 处数 | 风险 | 备注 |
| --- | --- | --- | --- | --- |
| 新增 fork 属性（如 `command-palette-key`） | `app/properties/ForkProperties.java`（键常量与读取）、消费方 | 2 | 低 | 直接读 raw properties，不改 `termux-shared` |
| 新增终端菜单项 | `terminal/menu/TerminalMenuAction.java`、`TermuxActivity.showTerminalMenu`、`strings.xml` | 3 | 低 | `terminal-menu-items` 属性自动支持排序与显隐 |
| 新增命令面板命令 | 新建 `commands/ShellCommandCatalog.java` | 1 | 低 | 面板、快捷键、extra-keys、菜单共用 |
| 新增设置页 | `fragments/settings/termux/*`、`res/xml/*.xml`、`root_preferences.xml`、`SettingsSearchIndex` | 4 | 中 | 搜索索引需同步，否则搜不到 |
| 新增 extra-keys 键类型 | `termux-shared`（只读）+ `app/terminal/io` | 2 | 高 | 建议用「保留键码 + app 侧拦截」绕开 |
| 右键语义改造 | 新增 `app/terminal/TermuxTerminalView.java`、`activity_termux.xml` | 2 | 中 | 不改 `terminal-view` |
| 新增分屏方向/布局 | `TerminalSplitScreenController`、`TermuxActivity` | 2 | 中 | 已有左右/上下两种方向 |
| 接入外部命令执行 | `RunCommandService`、`termux-shared` 契约 | 2 | 中 | 涉及权限与安全，需单独评审 |

### 6.2 扩展点地图

| 扩展点 | 入口 | 位置 |
| --- | --- | --- |
| 属性 | `ForkProperties.readTermuxProperties()` + 键常量 | `app/properties/` |
| 终端菜单动作 | `TerminalMenuAction` 枚举 + `TerminalMenuConfig` | `app/terminal/menu/` |
| 键位/鼠标语义 | `TerminalViewClient` 实现类 | `app/terminal/TermuxTerminalViewClient` |
| 会话生命周期 | `TermuxService` + `TermuxTerminalSessionActivityClient` | `app/`、`app/terminal/` |
| 偏好页 | `fragments/settings/**` + `res/xml/*` | `app/` |
| 设置搜索 | `SettingsSearchIndex` | `app/fragments/settings/` |
| extra-keys | `termux-shared` 解析 + `TermuxTerminalExtraKeys` 样式 | 只读模块 + `app/terminal/io/` |
| 状态栏与通知 | `statusbar/`、`notifications/` | `app/` |

### 6.3 短板

| 短板 | 影响 | 缓解 |
| --- | --- | --- |
| 入口类过载 | 新功能都改 `TermuxActivity`，冲突与回归风险高 | 本轮新增代码独立成 `commands/` 包，Activity 只加接线 |
| 无统一动作模型 | 每个入口各写一份动作，易出现行为不一致 | 命令目录作为唯一动作来源 |
| extra-keys 扩展受只读模块限制 | 无法按常规方式新增键类型 | 保留键码拦截 + 属性配置 |
| Robolectric 4.10 与 JDK 21 不兼容 | 本机 1 项测试失败，削弱本地验证 | 计划升级到 4.12+ |
| 无设备验证 | 手感与亮暗色适配只能靠代码推断 | 出包后按清单逐项验收 |

---

## 7. 对即将实施功能的落点

| 功能 | 落点 | 关键约束 |
| --- | --- | --- |
| 命令面板 | 新增 `app/commands/{ShellCommand,ShellCommandCatalog,ShellCommandPalette}`；`TermuxActivity` 增加 `showCommandPalette()` 与 `dispatchKeyEvent` 拦截 | 复用 `TerminalMenuDialog` 的配色解析与设置搜索的列表样式，不引入新 UI 框架 |
| 菜单收敛 | `TermuxActivity.showTerminalMenu`、抽屉按钮监听、`TerminalMenuAction` | 保留 `TerminalMenuDialog` 作为终端上下文菜单；面板作为统一入口 |
| 右键转发 | 新增 `app/terminal/TermuxTerminalView` 子类 + 布局替换 | 只读模块零改动；`BUTTON_SECONDARY` 且 `isMouseTrackingActive()` 时转发，否则走 `super` |
| extra-keys 入口 | `TermuxTerminalViewClient.onKeyDown` 拦截保留键码（默认 `F12`） | 需在 `FORK.md` 说明配置方式；键码可后续改为属性 |

---

## 8. 验证与未验证

| 项 | 结果 |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | 通过 |
| `:app:testDebugUnitTest` | 136 项，**135 通过、1 失败**：`FileReceiverActivityTest.testIsSharedTextAnUrl` 因 Robolectric 4.10 在 JDK 21 下 `ShadowActivityThread.reset` 抛 `NullPointerException` |
| `:app:lintDebug` | 未执行 |
| 设备侧 | 未验证：本机无 `adb` |

Robolectric 需要下载 `android-all` 工件，本机已通过仓库外 `~/.gradle/init.d/mirror.gradle` 指向可达镜像；
该配置不进入仓库。

---

## 9. 2026-11-23 复盘（扩展化与 UI 统一之后）

本节是对 §0–§8 的重新评估，取代其中的度量数字；结论以本节为准。

### 9.1 度量

| 指标 | 扩展化之前 | 现在 |
| --- | --- | --- |
| `app/` Java 文件 | 124 | 120 |
| `app/` Java 行数 | 18451 | 23451（新增扩展框架、面板、控件与测试支撑代码） |
| 最大文件 | `TermuxActivity` 2447 行 / 124 方法 / 109 import | `TermuxActivity` 2235 行 / 137 方法 / 103 import |
| 第二梯队 | `TermuxTerminalViewClient` 约 1100 行 | `TermuxTerminalViewClient` 1072、`TermuxService` 959、`TermuxTerminalSessionActivityClient` 785 |
| 功能扩展 | 无（功能散在 Activity） | 9 个（`statusbar`、`menu`、`notifications`、`volumekeys`、`search`、`drawer`、`split`、`backup`、`builtin.vt`） |
| 单元测试 | 136 项 | 180 项 / 36 类 |

Activity 的方法数没有下降反而上升：拆分把状态与逻辑移进扩展，但宿主的公开方法（供扩展回调）增加了，
行数下降 8.7%。这说明「拆分」改善的是**职责归属**，不是入口类的体量；体量问题需要另一套手段（见 9.4）。

### 9.2 分层现状

实际依赖方向（省略只读模块）：

```
fragments/settings  ──▶ ext/host, ext/api, properties, widget
commands            ──▶ ext/menu（动作与规则）, widget, properties
terminal (view/session clients) ──▶ commands, ext/*, widget
ext/<feature>       ──▶ ext/api, ext/host, properties, widget, TermuxActivity, TermuxService
ext/api, ext/host   ──▶ 无 app 内部依赖
widget, properties  ──▶ 共享库
```

| 层 | 现状评价 |
| --- | --- |
| `ext/api` + `ext/host` | **健康**。契约（`Extension`、`ExtensionContext`、`ExtensionViewSlot`）与状态机（`ExtensionGraph`、`ExtensionHost`、`ExtensionManager`）只依赖共享库，可纯 JVM 测试；禁用语义只有一个判定入口（`findRunning`） |
| `ext/<feature>` | **方向正确但未倒置**。扩展持有自己的状态、解析与控制器，宿主通过 `findRunning` 取用；代价是 13 个扩展类直接 import `TermuxActivity`/`TermuxService`，扩展层依赖宿主实现而不是宿主的窄接口 |
| `commands` | **良好**。面板、抽屉按钮、快捷键共用 `ShellCommandHost` 与 `TerminalActionRunner`；目录、过滤、快捷键解析都是可测纯函数 |
| `terminal` | **宿主适配层**。视图与会话客户端仍是最大的两个遗留类，负责把只读模块的事件转给扩展；可用性判定已收敛到 `TerminalMenuItems` |
| `widget` | **新增的共享层**。形状令牌、`TerminalDialogBuilder`、主题色读取集中在这里；已移除唯一一处对 `ext.drawer` 的反向依赖 |
| `fragments/settings` | **良好**。页面声明、搜索索引、长按复位、扩展闸门都有守卫测试 |
| `properties` | **良好**。fork 自有属性统一从 `ForkProperties` 读，新增键集中在 `ForkPropertyKeys` |

### 9.3 已解决的旧问题

1. 「入口分散」：终端动作、会话、键盘、搜索、面板统一到面板与 `ShellCommandHost`，旧的菜单对话框已删除，
   右键/菜单键/多选「更多」/快捷键都打开同一份命令列表。
2. 「UI 不一致」：形状令牌 + 共用对话框构建器，终端内对话框与平台对话框使用同一圆角与同一套令牌。
3. 「配置分散」：`reloadProperties()` 是唯一重载入口（扩展钩子 + 两个宿主属性），快捷键与右键行为也从这里读。
4. 「规则不可测」：可见性/可用性、面板过滤、快捷键解析、属性解析都有纯 JVM 测试。

### 9.4 仍存在的问题（按影响排序）

1. **`TermuxActivity` 仍是上帝对象**（2235 行、137 方法）。拆分降低了逻辑密度，但没有减少宿主 API 面；
   任何新交互仍会扩大它。建议：把「窗口/外观」与「动作派发」拆成两个宿主门面类
   （`TerminalAppearanceHost`、`TerminalActionsHost`），Activity 只做装配与生命周期。
2. **扩展依赖宿主实现**。13 个扩展类 import `TermuxActivity`。建议定义 `TerminalHost` 窄接口
   （会话、面板、布局请求、Toast），由 Activity 实现；扩展只依赖接口，便于替换与测试。
3. **`TerminalMenuItems.State` 与宿主耦合**。状态快照的构造仍在 Activity，语义扩展（新扩展可用性）需要改 Activity；
   建议由 `TerminalHost` 提供 `snapshot()`。
4. **测试集中在纯函数**。Activity、视图客户端、真实布局没有测试；形状与 insets 只能靠真机。
   可引入 Robolectric 的 View 级金丝雀测试（对话框构建器、行背景状态、insets 计算）。
5. **只剩一处跨层反向依赖的同类风险**：`ext/*` 与 `commands` 都可能再次直接 import 宿主，缺少静态守卫。
   建议加一个源码扫描测试（类似 `ResourceClassNameTest`），限制 `ext/**` 只允许 import 白名单。

### 9.5 建议的优先级

| 优先级 | 动作 | 收益 |
| --- | --- | --- |
| P0 | `TerminalHost` 窄接口 + 源码导入守卫测试 | 把扩展层从 Activity 解耦，阻止继续恶化 |
| P1 | 拆 `TerminalAppearanceHost`（窗口背景、状态栏配色、内边距、insets） | 直接缩小 Activity，缓解最大冲突点 |
| P2 | 视图级 Robolectric 测试（对话框、抽屉行、状态栏分段） | 让观感改动有自动回归 |
| P3 | `preferenceTheme` 与设置页顶栏配色统一（需先定设计） | 补齐最后一处 Material 2 观感 |

### 9.6 未验证

本节的分层判断基于源码与测试，不包含运行时剖析；Activity 的实际耦合度、扩展回调的耗时、视图重建开销均未测量，
本机也没有 `adb` 与性能工具。
