# UI_REVIEW — 页面架构与易用性评估报告

> 评估对象：`app_new/` 新外壳的全部主源与测试源。
> 评估基准：`AGENTS.md` 的协作约束、`docs/DELIVERY_PLAN.md` 的验收口径、`docs/UPGRADE.md` 的目标形态。
> 本文件只给出评估与方案，不含代码改动；实施批次见第 7 节，需先在计划中登记里程碑。

---

## 0. 结论摘要

| 维度 | 权重 | 评分（五分制） | 结论 |
| --- | --- | --- | --- |
| 分层与依赖 | 20% | 3.5 | 纯逻辑层（`pane`、`tabs`、`config`、`input`、`session`）边界清晰，可脱离 Android 测试；界面层与状态层耦合偏重 |
| 页面架构 | 15% | 2.5 | 无导航模型，返回键与抽屉状态存在多真源；主界面为单体组合，进出设置页会销毁并重建主界面 |
| UI 易用性 | 25% | 2.5 | 桌面端骨架完整，但设置页缺搜索、确认与撤销，标签与会话的入口不一致，文案与行为存在不一致 |
| 无障碍 | 10% | 1.5 | 触摸目标小于 48dp 的有两处，多数自绘交互缺 `Role` 与 `Semantics`，分割条无键盘与读屏替代操作 |
| 健壮性 | 15% | 3.0 | 失败路径大多被吞掉并回退，配置写入存在非原子窗口，会话状态与真实进程脱节 |
| 代码质量 | 15% | 3.5 | KDoc 完整、命名一致、纯函数多；三个文件超过 350 行，设置项需改四处才能新增一项 |
| **加权总分** | 100% | **2.8 / 5** | **可用原型级**：闭环已通，但距「桌面级体验 + 发布级稳健」仍有明确差距 |

三条最关键的结论：

1. **页面架构缺一层导航抽象**。设置页、命令面板、会话抽屉分别用布尔量、覆盖层与抽屉状态表达，
   返回键由三处独立 `BackHandler` 竞争，抽屉状态同时存在于 `ShellViewModel` 与 `DrawerState`。
   新增任何一层界面（设置子页、会话详情、键位编辑器）都会放大这一结构的问题。
2. **设置页仍是一次性罗列**。十个配置项已占满一屏，`DELIVERY_PLAN` 的 M15/M16/M18 还要追加
   指针模式、虚拟按键行、终端字号与配色等十余项；当前形态没有搜索、折叠、分组索引与宽屏分栏，
   M18-1 的「宽屏左右分栏」验收项尚未实现。
3. **会话列表显示的信息与真实进程无关**。`SessionRegistry` 只由工作区派生，从不读取
   `SessionProvider.describe`，也不订阅 `SessionHost.Observer`，因此运行中的会话会显示
   「无会话」占位文案，`EXITED` 与「脱离会话」两个状态永远没有生产者。

等级定义：**P0** 崩溃或数据丢失；**P1** 正确性或效率明显受损；**P2** 打磨与可维护性。本报告未发现
可确认的 P0 缺陷；A5 若在设备上复现即为 P0。

---

## 1. 评估范围与方法

| 项 | 内容 |
| --- | --- |
| 静态阅读 | `app_new/src/main/kotlin` 53 个文件 6529 行、`app_new/src/test/kotlin` 16 个文件 1709 行、资源 4 个目录 |
| 文档核对 | `AGENTS.md`、`docs/DELIVERY_PLAN.md`、`docs/UPGRADE.md`、`app_new/PLAN.md`、`app_new/DECISIONS.md` |
| 测试清点 | 123 个 `@Test`，分布在 16 个测试类；无 `androidTest` 源集、无 Compose UI 测试、无截图测试 |
| 构建尝试 | `./gradlew :app_new:testDebugUnitTest` 未通过：`kotlinx-coroutines-android:1.10.2` 未在本地 Gradle 缓存中，且 `repo.maven.apache.org` 返回 403（本机网络受限） |
| 设备验证 | 本机无 `adb`，全部结论均未经设备复现 |

**未验证声明**：本报告的全部结论来自静态阅读与文档比对，未在设备上运行，也未完成构建与单元测试。
涉及「会崩溃」「会卡顿」的判断均为代码推理结论，须按第 8 节逐条复现后再定级。

---

## 2. 页面架构评估

### 2.1 现状结构

```
ShellActivity                    窗口宿主：边到边、系统栏、快捷键分发、工作区保存
└─ ShellScreen                   ShellViewModel 状态 + 覆盖层
   ├─ AnimatedContent(settingsVisible)
   │  ├─ ShellContent            按宽度档位二选一
   │  │  ├─ CompactShell         ModalNavigationDrawer + ShellBody
   │  │  └─ WideShell            常驻 SessionList + ShellBody
   │  │     └─ ShellBody         ShellHeaderBar + TabBarSlot + PaneArea
   │  └─ SettingsScreen          单页滚动 + ProfileEditorDialog
   ├─ CommandPaletteView         覆盖层（淡入 + 缩放）
   └─ SnackbarHost
```

### 2.2 问题清单

| 编号 | 级别 | 现象 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| A1 | P1 | 导航无单一抽象：设置页用布尔量、命令面板用覆盖层、会话抽屉用抽屉状态；返回键由 `ShellScreen.kt:114`、`:115`、`:196` 三处 `BackHandler` 注册，优先级取决于组合嵌套顺序 | `ui/ShellScreen.kt`、`ui/ShellViewModel.kt:85-165` | 引入 `ShellDestination`（`Main`／`Settings`／`Palette`／`SessionDrawer`）与导航栈，只注册一个 `BackHandler` 并按栈顶分派 |
| A2 | P1 | 进出设置页销毁并重建整个主界面组合：终端 `AndroidView` 被解绑再挂载，可能触发 `updateSize` 与滚动位置重算 | `ui/ShellScreen.kt:122-136` | 主界面常驻，设置页以独立 destination 叠加（双栏时右侧面板），或用 `SaveableStateHolder` 保留主界面状态 |
| A3 | P1 | 抽屉状态双真源：`sessionDrawerVisible` 与 `DrawerState` 通过两个 `LaunchedEffect` 互相同步 | `ui/ShellScreen.kt:184-195` | 以导航状态为唯一真源，抽屉只做受控组件；手势关闭走同一回调 |
| A4 | P1 | `ShellViewModel` 592 行，同时承担工作区动作、设置写回、profile CRUD、会话绑定、导航布尔量、提示事件与新窗口令牌 | `ui/ShellViewModel.kt`（`85-96` 导航状态、`206-223` 标签关闭、`370-406` profile 写回） | 拆为 `WorkspaceController`、`SettingsController`、`ProfileController`、`ShellUiState`（导航与提示）；VM 只做装配与生命周期 |
| A5 | P1 | 组合期副作用：`remember(sessionId, pool) { pool.obtain(...) }` 在组合过程中创建视图、挂载会话；`AndroidView` 只提供 `factory`，无 `onRelease` | `ui/PaneArea.kt:222-260`、`terminal/TerminalViewPool.kt:53-95` | `obtain` 移出组合期；`AndroidView` 补 `onRelease` 解除父容器；`TerminalViewPool` 明确「单视图单挂载」不变量与 `release(view)` 契约 |
| A6 | P2 | 主界面为单体组合：`ShellScreen.kt` 内含 8 个私有组件，`SettingsScreen.kt` 内含 5 个行式组件 | 两个文件均 390 行以上 | 按「屏幕 — 区块 — 行」拆包：`ui/shell/`、`ui/settings/`，单文件控制在 200 行以内，并补 `@Preview` |
| A7 | P2 | `ConfigSource` 抽象名不副实：`ApplicationConfigSource` 仅转接静态单例，构造参数被 `@Suppress("unused")`，与「不直接依赖静态字段」的约束声明不符 | `config/ApplicationConfigSource.kt`、`config/ConfigSource.kt` | 二选一：改为显式依赖容器（`ShellAppGraph`）注入，或删掉该参数并修正约束说明 |

### 2.3 状态管理一致性

| 编号 | 级别 | 现象 | 建议 |
| --- | --- | --- | --- |
| A8 | P1 | 状态暴露方式不统一：`workspace`、`uiSettings`、`paletteVisible` 等用 Compose `mutableStateOf` 直接放进 ViewModel，`profiles` 却用 `StateFlow` | ViewModel 统一输出 `StateFlow<ShellUiState>`，Compose 侧用 `collectAsStateWithLifecycle()` |
| A9 | P1 | ViewModel 依赖 Compose runtime：为回避单元测试中的 Parcel 依赖而弃用 `mutableIntStateOf`（`ui/ShellViewModel.kt:99-103`），状态层被 UI 框架污染 | 与 A8 同批解决：状态层只保留 Kotlin 标准库与协程，快照类型由界面自行包装 |
| A10 | P2 | 一次性提示用「可空字段 + 手动消费」表达，依赖 `LaunchedEffect` 键值变化；提示排队与取消语义不明确 | 改为 `Channel(Channel.BUFFERED).receiveAsFlow()`，界面侧一次收集 |
| A11 | P2 | 同一设置存在两种持久化语义：`setTabBarVisible` 写入配置文件，`toggleTabBar` 只改内存（`ui/ShellViewModel.kt:481`） | 统一为「界面开关即写回」，临时开关另立名称并在文案中说明 |

---

## 3. UI 易用性评估

### 3.1 设置页

| 编号 | 级别 | 现象 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| B1 | P1 | 设置页为线性滚动单页，无搜索、无折叠、无分组索引；宽屏下仍是单栏满宽 | `ui/SettingsScreen.kt:70-90` | 补搜索框（复用 `CommandFilter` 的匹配思路）、分组可折叠、宽屏左右分栏（即 M18-1 的验收项） |
| B2 | P1 | 开关行整行不可点，仅右侧 `Switch` 可触发；行高 52dp 但点击目标≈32dp | `ui/SettingsScreen.kt:313-338` | 整行 `Modifier.toggleable(value, role = Role.Switch)`，`Switch` 设 `onCheckedChange = null`，保留 `heightIn(min = 48.dp)` |
| B3 | P1 | 删除会话配置无二次确认、无撤销；删除按钮与「编辑」同权重、未用错误色 | `ui/SettingsScreen.kt:189`、`:265-270` | `AlertDialog` 确认 + Snackbar「撤销」；删除按钮改 `colors = ButtonDefaults.textButtonColors(contentColor = error)` |
| B4 | P1 | 文案与行为不一致：`shell_config_reloaded` 声称「主题变化在窗口重建后生效」，实际主题修改立即重组生效；`SettingsSpec` 中主题说明同样要求「重新加载配置文件」 | `res/values/strings.xml:15`、`res/values-zh/strings.xml:15`、`config/SettingsSpec.kt:91-98` | 逐条核对文案与实现，主题改为「立即生效」；「重新加载配置文件」只描述配置与键位表 |
| B5 | P1 | 设置项无「生效时机」「已修改」「恢复默认值」三类信息；写入失败时界面保留乐观更新且无重试入口 | `ui/ShellViewModel.kt:552-556` | 行尾标注生效时机；失败时回滚并在行内给出重试；为可回退项提供「恢复默认」 |
| B6 | P1 | 缺终端相关分组（字号、字体、配色、滚动回滚行数、虚拟按键行、粘贴入口），这些是终端类应用的高频项 | `ui/SettingsScreen.kt` 全文 | 预留「终端」分组，与 M16-4、M16-5、M18 一并交付 |
| B7 | P2 | 设置入口不一致：紧凑档设置藏在抽屉顶部，宽屏在侧栏顶部；10 项窗口动作只挂在侧栏的溢出菜单 | `ui/ShellScreen.kt:359-410` | 标题行统一提供溢出菜单（新建、分屏、设置、重载），侧栏保留快捷按钮 |
| B8 | P2 | `fullscreen` 默认 `true`，首次启动即隐藏系统栏，用户难以判断如何返回 | `ui/ShellSettings.kt:35`、`config/SettingsSpec.kt:127` | 默认改为 `false`，或首次启动给一次性提示 |

### 3.2 会话列表与标签栏

| 编号 | 级别 | 现象 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| B9 | P1 | 运行中的会话显示「窗格 pane-x · 无会话」：`title` 恒为 `null` 时回退到「无会话」文案 | `ui/SessionList.kt:183`、`session/SessionRegistry.kt:131-155` | 见 C1：接入 `SessionProvider.describe`，并区分「未绑定」「运行中」「已退出」三种文案 |
| B10 | P2 | 标签栏无拖拽排序、无滑动手势切换到相邻标签；选中末尾标签后不自动滚动到可见位置 | `ui/TabStripBar.kt:54-100` | 改用 `LazyRow` + `animateScrollToItem(activeIndex)`；长按拖拽排序（`detectDragGesturesAfterLongPress`） |
| B11 | P2 | 标签菜单与窗口菜单项重复（新建、左移、右移、关闭），两者实现各写一份 | `ui/TabStripBar.kt:213-243`、`ui/ShellScreen.kt:391-410` | 抽出菜单模型（`ShellMenuModel`），两处共享同一份条目与可用性判定 |
| B12 | P2 | 命令面板不按分组渲染，`category` 仅参与筛选；无最近使用、无命中高亮；筛选变化后选中项跳到首个「可执行」项 | `ui/CommandPaletteView.kt:90-125` | 按 `category` 分组并显示分组标题；高亮命中字符；按命令标识记忆选中项 |

### 3.3 无障碍与触摸目标

| 编号 | 级别 | 现象 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| B13 | P1 | 标签关闭按钮 28dp，低于 48dp 最小触摸目标 | `ui/TabStripBar.kt:277-286` | 视觉尺寸不变，命中区扩到 40–48dp（`Modifier.size(48.dp)` + 内层图标 14dp） |
| B14 | P1 | 分割条 6dp，触屏难以精确拖动 | `ui/PaneArea.kt:54` | 视觉 2dp、热区 12dp，拖动中显示提示线；另提供「重置为 1:1」菜单项 |
| B15 | P1 | 自绘交互缺语义：标签用 `combinedClickable` 未设 `role`/`selected`；关闭按钮为 `Box + clickable` 无 `Role.Button`；会话行无选中语义 | `ui/TabStripBar.kt:155-200`、`:270-300`、`ui/SessionList.kt:154-200` | 补 `Modifier.semantics { role = Role.Tab; selected = ... }`；把 `Box + clickable` 换成 `IconButton` 或补 `Role.Button` |
| B16 | P2 | 按下态靠手动轮询指针事件（`while (true) { awaitPointerEvent() }`）实现，每个标签常驻一个协程 | `ui/TabStripBar.kt:167`、`:289-298` | 改用 `interactionSource.collectIsPressedAsState()`；右键菜单改用 `onPointerEvent(PointerEventType.Press)` |
| B17 | P2 | 分割条无读屏与键盘替代操作，TalkBack 用户无法调整比例 | `ui/PaneArea.kt:170-215` | 补 `semantics { customActions = listOf(增大/缩小/重置) }`，并响应 `Key.DirectionLeft/Right` |

### 3.4 文案与本地化

| 编号 | 级别 | 现象 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| B18 | P2 | 硬编码拼接：`"SSH · ${profile.sshHost}"`；`shell_header` 用 `·` 连接标签与窗格数 | `ui/SettingsScreen.kt:247`、`res/values/strings.xml:4` | 拼接移入资源并用 `plural`，分隔符纳入资源文本；`SessionProfile` 提供可测的 `summary()` |
| B19 | P2 | 空态与新功能提示偏薄：会话空态只有一行文字，会话宿主已接入后仍显示「会话接入后可用」 | `ui/SessionList.kt:99-140`、`res/values/strings.xml:47` | 空态给出下一步动作（「新建会话」按钮）；提示文案随真实会话状态收敛 |

---

## 4. 健壮性评估

| 编号 | 级别 | 现象 | 定位 | 建议 |
| --- | --- | --- | --- | --- |
| C1 | P1 | 会话列表与真实进程脱节：`SessionRegistry.fromWorkspace` 不读 `describe`，`title`/`workingDirectory` 恒为 `null`，`state` 只有 `PENDING`/`RUNNING`，`EXITED` 与 `detached()` 无生产者；`SessionHost.Observer` 已定义但无人注册 | `session/SessionRegistry.kt:121-155`、`session/LocalSessionHost.kt:128-160` | 注册 `SessionHost.Observer`，由标题变化、退出与创建事件增量更新 `StateFlow<SessionRegistry>`；`PENDING` 仅用于 `sessionId == null` |
| C2 | P1 | 配置写入存在非原子窗口：先 `delete()` 原文件再 `renameTo()` 临时文件，两者之间进程被杀即丢失配置 | `config/PropertiesSettingsRepository.kt:146-158`、`config/FileProfileStore.kt:93-101` | 同目录 `rename` 直接覆盖（保留原文件到最后一刻），或在改名前后保留 `.bak`；设置写入补 `FileDescriptor.sync()`（profile 写入已有） |
| C3 | P1 | 键盘弹出会逐帧改变终端尺寸：`ShellBody`/`WideShell` 用 `safeDrawing`（含 ime）整体收放，`TerminalView.updateSize()` 随之连续触发 PTY resize | `ui/ShellScreen.kt:257-283`、`terminal-core/.../TerminalView.java:1425` | 键盘期间用固定 inset 或动画 padding，仅在动画结束后单次 resize（去抖 100–150ms），与 M12-5/M12-6 一并验收 |
| C4 | P1 | 视图挂载副作用可在组合被丢弃时遗留（A5 的运行期表现），并可能在重新挂载时因父容器未解除而抛异常 | `ui/PaneArea.kt:222-260` | 同 A5 |
| C5 | P2 | 异常处理粒度粗：`TerminalViewPool.obtain` 捕获 `Throwable`（含 `OutOfMemoryError`）；持久化失败统一并入 `runCatching{}.getOrNull()`，界面只得到一句「保存失败」 | `terminal/TerminalViewPool.kt:93`、`ui/ShellViewModel.kt:370-406` | 区分 IO 可恢复失败与编程错误；失败信息落盘并提供「导出诊断」（M15-4） |
| C6 | P2 | 布局无冷启动恢复：只有 `workspace` 存入实例状态，进程被回收后从桌面重新打开回到单标签 | `ui/ShellActivity.kt:127-155` | 落地 M13-3：布局写入 `filesDir/layout/`（`ShellPaths` 已预留），带版本号与损坏回退 |
| C7 | P2 | 窗口销毁按 detach 处理，会话进程与 PTY 保留但无任何界面可达，直到进程结束 | `ui/ShellViewModel.kt:206-223`、`session/LocalSessionHost.kt:112-122` | 提供 `multi-window-session-policy`（detach/kill），detach 的会话进入「脱离会话」列表（依赖 C1） |
| C8 | P2 | `closeSession` 移除条目后不通知观察者，会话列表无法得知会话已结束 | `session/LocalSessionHost.kt:112-122` | 关闭前发出 `onExit`，并在 `describe` 缺失时向界面回传「已退出」 |
| C9 | P2 | 全量组合与常驻协程：`SessionList`、`SettingsScreen` 用 `Column + verticalScroll`；每个标签常驻一个指针事件协程；`WindowMenu` 每次重组新建条目列表 | `ui/SessionList.kt:99`、`ui/SettingsScreen.kt:77`、`ui/TabStripBar.kt:167`、`ui/ShellScreen.kt:391` | 会话与配置列表改 `LazyColumn`；菜单条目抽为常量并以 `when` 分发 |
| C10 | P2 | 平台惯例：`documentLaunchMode="always"` 使从桌面图标启动也新建任务，任务列表膨胀 | `app_new/src/main/AndroidManifest.xml:24` | 改为 `intoExisting`，「新建窗口」继续用 `FLAG_ACTIVITY_NEW_DOCUMENT` |

---

## 5. 代码质量评估

| 编号 | 级别 | 现象 | 建议 |
| --- | --- | --- | --- |
| D1 | P1 | 设置项新增需同步改四处（`SettingsSpec`、`ShellSettings`、`ShellViewModel` 的写回方法、`SettingsScreen` 的行式组件），且每项的 `selected` 计算与 `copy` 变换高度重复 | 引入「配置项 → 控件」描述层（布尔项、选项项、文本项、路径项），设置页从 `SettingsSpec.entries` 与 `ShellSettings` 派生渲染 |
| D2 | P1 | 页面重构无回归保护：`ui` 层仅有 `ShellViewModelTest`、`WindowWidthClassTest`、`AdaptiveLayoutTest` 三个测试，无 Compose UI 测试、无截图测试、无 `androidTest` 源集 | 引入 `androidx.compose.ui:ui-test-junit4`（配 Robolectric 或 instrumented），至少覆盖「开关写回」「删除确认」「返回键逐层关闭」「抽屉开合」四条路径 |
| D3 | P2 | 注释与实现不一致：多处 KDoc 声明的行为尚未实现（`SessionRegistry` 的「由上层 upsert 补充」、`ApplicationConfigSource` 的「不依赖静态字段」、主题生效时机） | 未实现项统一改为 `TODO(里程碑编号)`，与 `AGENTS.md` 第 2.5 条一致，便于检索与验收 |
| D4 | P2 | 三个文件超过 350 行：`ShellViewModel` 592、`ShellScreen` 413、`SettingsScreen` 390 | 与 A4、A6 同批拆分 |
| D5 | P2 | 重复结构：10 个 `updateXxx` 写回方法模板相同；`ChoiceRow` 的 `selected` 用 `if/else` 映射常量；`ProfileRow` 的类型摘要分支应移入模型层 | 用描述层（D1）与 `SessionProfile.summary()` 消除重复 |
| D6 | P2 | 构建验证依赖外部脚本或 CI：本机 `:app_new:testDebugUnitTest` 因依赖未缓存且仓库不可达而失败，历次里程碑以「`kotlinc` + `JUnitCore` 替代验证」记录 | 固化 `tools/verify.sh`（参数化的替代验证入口），或在 `~/.gradle/init.d` 配置可达镜像，使本地可重复验证 |

### 5.1 值得保留的做法

- 纯逻辑层（`PaneLayout`、`TabWorkspace`、`KeymapTable`、`SettingsSpec`、`Codec`）不依赖 Android 运行时，
  123 项单元测试基本都落在这层，重构安全边界清晰。
- `SettingsSpec` 把键名、类型、默认值、合法取值与说明集中在一处，为 D1 的描述层提供了现成基础。
- KDoc 结构统一为「目的 — 行为 — 约束」，`ShellNotice`、`ShellViewOptions`、`SessionRegistry` 等类型的
  职责说明准确，可作为后续新增类型的模板。
- 原子写、单视图单挂载、`argv` 非空等不变量已在契约层用 `require` 与 KDoc 声明。

---

## 6. 与既有计划的关系

| 计划项 | 当前状态 | 本报告的处置 |
| --- | --- | --- |
| M18-1 设置页骨架（宽屏左右分栏） | 部分完成：分节存在，分栏未实现 | 并入第 7 节批次 2 的验收 |
| M18-2 双向同步 | 已完成 | 由 B5 补充「失败回滚与重试」 |
| M18-3 权限与状态提示 | 未开始 | 与批次 3 合并 |
| M17-3 会话注册表 | 部分完成：派生实现存在但不含真实状态 | 并入批次 4 |
| M13-3 布局落盘与恢复 | 未开始 | 列为 C6，不纳入本报告的实施批次 |
| M15-4 按键诊断与导出 | 未开始 | 由 C5 引用 |

---

## 7. 优化方案（实施批次）

批次顺序按「先结构、后体验」排列；每批单独提交，逐批复跑 `:app_new:assembleDebug`、
`:app_new:testDebugUnitTest`、`:app_new:lintDebug`，并在设备清单上逐条记录结果。

### 批次 1 — 导航与状态分层（对应 A1–A4、A8–A11）

- 目标：单一导航真源、单一返回键分派、主界面在进入设置页时不被销毁。
- 改动文件：`ui/ShellViewModel.kt`（拆分）、新增 `ui/nav/ShellDestination.kt`、`ui/nav/ShellNavigator.kt`、
  `ui/WorkspaceController.kt`、`ui/SettingsController.kt`、`ui/ShellUiState.kt`；修改 `ui/ShellScreen.kt`、
  `ui/ShellActivity.kt`、`ui/SettingsScreen.kt`、`ui/PaneArea.kt`。
- 验收：连续按返回键按「命令面板 → 抽屉 → 设置页 → 主界面」顺序逐层关闭；进出设置页后终端光标位置与
  滚动位置不变；`ShellViewModel.kt` 降至 200 行以内；新增导航与返回键单元测试各一组。
- 风险：主界面常驻需要处理终端视图与覆盖层的焦点竞争；需在设备上确认输入法不因覆盖层而反复收放。

### 批次 2 — 设置页易用性（对应 B1–B8、D1）

- 目标：设置页从「手写列表」升级为「由配置项描述驱动」，并补齐确认、撤销与生效时机。
- 改动文件：新增 `ui/settings/SettingsEntry.kt`（描述层）、`ui/settings/SettingsSection.kt`；
  重写 `ui/SettingsScreen.kt`；修改 `config/SettingsSpec.kt`（补 `Entry` 的控件类型与生效时机）、
  `ui/ShellViewModel.kt`（写回方法收敛为一项）、`res/values*/strings.xml`。
- 验收：新增一项布尔配置只需修改 `SettingsSpec` 与 `ShellSettings` 两处；设置页提供搜索，宽屏左右分栏；
  删除会话配置有确认与撤销；开关行整行可点；主题与重载相关文案与实际行为一致。
- 风险：描述层过度抽象会增加理解成本，优先只覆盖当前三类控件（开关、选项、信息行）。

### 批次 3 — 无障碍与触摸热区（对应 B13–B17）

- 目标：全部可交互元素命中区不小于 48dp，自绘交互具备读屏语义与键盘替代操作。
- 改动文件：`ui/TabStripBar.kt`、`ui/PaneArea.kt`、`ui/SessionList.kt`、`res/values*/strings.xml`。
- 验收：TalkBack 可朗读标签的「选中 / 未选中 / 可关闭」并执行关闭；分割条可通过自定义操作与方向键调整；
  触摸目标核查通过（关闭按钮与分割条）。
- 风险：扩大热区会挤压标签文字宽度，需要在紧凑宽度下复核省略行为。

### 批次 4 — 会话列表真实状态（对应 B9、C1、C7、C8）

- 目标：会话列表展示真实标题、工作目录与运行状态，并区分「未绑定」「运行中」「已退出」「已脱离」。
- 改动文件：`session/SessionRegistry.kt`（改为接收 `SessionProvider` 快照 + 增量更新）、
  新增 `session/SessionMonitor.kt`（订阅 `SessionHost.Observer`）、修改 `ui/ShellViewModel.kt`、
  `ui/SessionList.kt`、`res/values*/strings.xml`。
- 验收：新标签显示的标题为前台进程标题；执行 `exit` 后状态变为「已退出」；关闭窗格后会话从列表移除；
  `EXITED` 与「已脱离」两个分组可通过构造场景复现。
- 风险：观察者回调在主线程之外触发，需要自行切回主线程并做去抖（标题变化可能高频）。

### 批次 5 — 持久化与平台细节（对应 C2、C3、C5、C6、C9、C10）

- 目标：配置写入原子化、键盘弹出不再逐帧重排终端、列表虚拟化、任务栈行为符合平台惯例。
- 验收：模拟「写入时杀进程」后配置文件仍可用；键盘弹出与收起仅触发一次 PTY resize（日志计数）；
  百条会话下列表滚动不掉帧；从桌面图标启动不新增任务。
- 说明：本批与 `DELIVERY_PLAN` 的 M12-5/M12-6、M13-3 有重叠，实施时以计划编号为准。

---

## 8. 复现清单（设备侧）

以下条目在实施前应先确认，用于把报告中的推理结论转成实测结论：

1. 进出设置页后终端光标是否复位、回滚位置是否丢失（A2）。
2. 手机竖屏与平板横屏之间旋转，或拖动自由窗口跨越 600dp 断点，是否出现视图重挂载异常（A5、C4）。
3. 标签关闭按钮与分割条的实际可点范围（B13、B14）。
4. 键盘弹出与收起期间终端是否出现逐行跳动（C3），以 `TerminalView.updateSize()` 日志计数验证。
5. 配置文件写入过程中强杀进程后重启，配置是否丢失（C2）。
6. 运行 `exit` 后会话列表状态（C1、B9）。

---

## 9. 未验证声明

本报告的所有结论均来自静态阅读与文档比对，未执行构建、单元测试、Lint 或设备验证。
构建未执行的原因为：`kotlinx-coroutines-android:1.10.2` 不在本地 Gradle 缓存中，且
`repo.maven.apache.org` 在本机网络环境下返回 HTTP 403。设备侧无 `adb`，不可执行。

---

## 10. 实施记录（2026-11-23）

本节记录第 7 节批次的落地情况。终端核心层（`app_new/terminal-core/`）未作任何修改，符合「稳定性交给权威实现、
外壳只动界面与接线」的约束，因此 C3（键盘重排去抖）与脏行渲染不在本批范围。

### 10.1 已交付

| 批次 | 提交 | 内容 | 验证 |
| --- | --- | --- | --- |
| 1 导航 | `617c934` | 新增 `ui/nav/ShellNavigation`：层级栈（主界面／设置页）+ 单一覆盖层（命令面板／会话抽屉），返回键统一分派；设置页改为覆盖层，主界面与终端视图不再随页面切换销毁 | `ShellNavigationTest` 9 项；`testDebugUnitTest` 通过 |
| 2 设置页 | `a194b09` | 新增 `ui/settings/ShellSettingsCatalog` 描述层（开关、选项、动作、信息四类 + 会话配置块）；设置页按描述渲染，补搜索、分组卡片、宽屏两栏、删除二次确认、整行可点、路径可选中；修正主题与重载提示的生效时机 | 编译通过；`testDebugUnitTest` 通过 |
| 3 协作对象 | `d2909c5` | 抽取 `SettingsWriter`（写回管道）与 `SessionBinder`（叶子与会话绑定），`ShellViewModel` 由 592 行降至约 500 行，写回线程策略集中一处 | `ShellViewModelTest` 全通过 |
| 4 会话状态 | `8daf0e4` | `SessionRegistry.fromWorkspace` 接受 `describe` 查询，区分未绑定／运行／已退出并填充标题与工作目录；视图模型订阅 `SessionHost.Observer` 并以 `sessionRevision` 通知界面；`LocalSessionHost.closeSession` 补发退出通知；修正运行中会话显示「无会话」的文案缺陷 | 新增 5 项测试（注册表 3 项、视图模型 2 项）；共 137 项通过 |
| 5 无障碍 | `c1c7419` | 标签行高 48dp、关闭按钮改用 `IconButton`（触摸目标 48dp）、`Role.Tab` 与 `selected` 语义；分割条命中区 12dp + 2dp 细线，新增双击复位与三个无障碍动作；会话行补 `Role.Button`、`selected`、`stateDescription`；会话列表改 `LazyColumn`；标题行固定提供窗口菜单 | 编译与 Lint 通过 |
| 6 样式 | `bd1697c`、`1eb0666` | 命令面板按分组呈现（分组标题 + `secondaryContainer` 选中态 + 搜索图标）；主题在 Android 12 及以上跟随系统取色，低版本回退默认配色 | 编译与 Lint 通过 |

### 10.2 复核评分

| 维度 | 初次 | 复核 | 说明 |
| --- | --- | --- | --- |
| 分层与依赖 | 3.5 | 3.5 | 新增协作对象，界面层仍直接读取控制器状态 |
| 页面架构 | 2.5 | 3.5 | 单一导航真源与单一返回键；主界面常驻 |
| UI 易用性 | 2.5 | 3.5 | 设置页可搜索、可分组、宽屏分栏、破坏性操作有确认 |
| 无障碍 | 1.5 | 3.0 | 触摸目标与语义已补；分割条以 12dp 命中区折中 |
| 健壮性 | 3.0 | 3.0 | 会话状态已真实化，但配置写入仍非原子 |
| 代码质量 | 3.5 | 3.5 | 描述层消除设置项四处改动，仍缺 Compose UI 测试 |
| **加权总分** | **2.8** | **3.3** | |

### 10.3 仍未处理

| 编号 | 级别 | 剩余工作 | 阻塞原因 |
| --- | --- | --- | --- |
| C2 | P1 | 配置写入原子化（同目录 `rename` 覆盖、保留 `.bak`） | 需改动 `config/` 写盘策略，建议单独提交 |
| C3 | P1 | 键盘弹出期间单次 PTY resize | 去抖点在终端核心层，本轮约束为不修改 |
| C6 | P2 | 布局落盘与冷启动恢复（M13-3） | 需新增布局存储与损坏回退 |
| D2 | P1 | Compose UI 测试与截图基线 | 需新增测试依赖与 `androidTest` 源集 |
| A4 | P2 | `ShellViewModel` 剩余拆分（工作区动作、profile、导航） | 无设备验证前置，分步进行 |
| B6 | P1 | 终端分组设置（字号、配色、虚拟按键行） | 依赖 M16-4/M16-5 |
| B10 | P2 | 标签拖拽排序、滑动切换、自动滚动到选中标签 | 独立交互批次 |
| A5 | P1 | 视图池与组合期副作用彻底分离 | 已加 `onRelease` 与重新挂载保护，仍建议改为显式视图宿主 |

### 10.4 本机验证

| 项 | 结果 |
| --- | --- |
| `:app_new:assembleDebug` | 通过，debug APK 约 11.4 MB |
| `:app_new:testDebugUnitTest` | 通过，137 项 |
| `:app_new:lintDebug` | 通过（`abortOnError = true`） |
| 设备侧 | 未验证：本机无 `adb` |

构建环境补充：本机无法访问 `repo.maven.apache.org`（HTTP 403），已在仓库外的
`~/.gradle/init.d/mirror.gradle` 中配置可达镜像。该文件不进入仓库，也不改变仓库的依赖声明。
