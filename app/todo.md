# app/todo.md — 代码进度与计划

> 本文件记录 `app/` 模块的代码进度与近期计划，供跨会话续接使用。
> 决策、取舍与踩坑写入 `app/DECISIONS.md`；用户可见特性写入 `FORK.md`；会话交接摘要写入 `docs/HANDOFF.md`。
> 每完成一项立即在此勾选并补提交号，再单独提交本文件。

---

## 1. 当前状态

| 项 | 值 |
| --- | --- |
| 分支 | `refactor/app-extensions`（已推送，远端 `601cc22`） |
| 阶段 | 动工（骨架与规划已完成） |
| 最近提交 | `a5cba39 feat: 新增 ime-input-type 输入法兼容模式` |
| 规模 | `TermuxActivity` 2235 行；`commands/` 7 文件 1062 行；`ext/` 9 个扩展 + api/host；`terminal/` 6 文件 |
| 验证 | 本机 `:terminal-view:compileDebugJavaWithJavac`、`:app:compileDebugJavaWithJavac`、`:app:processDebugResources` 通过；`:app:testDebugUnitTest --rerun-tasks` 186 项、1 项既有失败 |

---

## 2. 已完成

### 2.1 扩展化

- [x] 扩展 API 骨架与「扩展」设置页（`8ffc65e`、`#2`）
- [x] 备份功能提取为 `ext.backup`（`26efb73`、`#3`）
- [x] 扩展禁用语义定为「入口不可达 + 运行时闸门」，修复设置搜索绕过（`27c5d47`、`a7dcfcf`、`#4`）
- [x] M-E1 闸门层：`ExtensionHost` 唯一状态源、`Extension.settingsPages()` 贡献点、搜索与导航过滤、页面闸门、空分类收起、`ExtensionRegistrySourceTest` 三项守卫（`2015ea9`、`a2193dd`、`d8362be`、`#5`）

### 2.2 命令与菜单

- [x] 终端菜单规则下沉为纯函数 `terminal/menu/TerminalMenuItems`（`3aebe2e`）
- [x] 命令面板 `ShellCommand`／`ShellCommandCatalog`／`ShellCommandPalette` 与四个入口（`00e1849`、`1064e17`）
- [x] 终端右键转发 `terminal/TermuxTerminalFrame`（`1064e17`）
- [x] 菜单收敛：抽屉按钮与会话新建按钮改走 `ShellCommandHost`，面板新增 `new-named-session`（`1c39234`、`#6`）
- [x] 修正命令面板「切换键盘」的递归调用（`a650b69`）

### 2.3 X1 控制器拆分

- [x] `TerminalActionRunner`：`runTerminalMenuAction` 的 93 行 `switch` 改为「动作 → 宿主方法」映射表，Activity 只保留委托；`TerminalActionRunnerTest` 3 项覆盖全部 22 个动作（`cc35d8f`、`#7`）
- [x] `TerminalDrawerController`：抽屉配色、把手、开合配置与头部概要从 Activity 迁出，Activity 由 2544 行降至 2275 行；会话列表适配器改为依赖控制器而非 Activity（`d5ca8ba`、`#8`）

### 2.4 扩展迁移

- [x] 迁移评估：逐特性列出规模、前置条件与顺序，见 `docs/EXTENSION_API.md` §10（`#8`）
- [x] 新增扩展生命周期贡献点 `onStart`/`onStop`/`onReload`（`98e63e2`）
- [x] 前置：`StatusBarColors` 下沉为 `widget/ColorSchemeColors`（`d45ef42`）；`StatusBarController` 去 Activity 化（`ce694b6`）
- [x] `ext.statusbar` 迁移：6 文件 + 设置页迁入扩展包，新增 `StatusBarExtension`（默认启用、页面闸门、点击动作、定时刷新），宿主经 `StatusBarExtension.getConfig()` 查询配置（`5e7ebbb`、`#8`）
- [x] `docs/API_INDEX.md`：已有 API 索引与重复实现清单，`AGENTS.md` 增加「新增前先查」约束
- [x] `ext.menu` 迁移：动作枚举、规则、配置、对话框与设置页迁入扩展包；新增 `TerminalMenuExtension`（默认启用、页面闸门、属性重载、`showMenu()`/`getConfig()`）；宿主 `showTerminalMenu()` 与 `getTerminalMenuConfig()` 改为转发（`2a623ce`、`#9`）
- [x] `ext.volumekeys` 迁移：动作派发迁入 `VolumeKeyActions`（`Host` 接口），音量键设置从键盘页拆出为独立页，新增 `VolumeKeysExtension` 与闸门；状态机仍由视图客户端持有（`d418a90`、`#10`）
- [x] 视图槽位贡献点：`ExtensionViewSlot` + `Extension.slots()`、`ExtensionContext.view()`、`ExtensionContext.requestExtensionLayout()` + `ExtensionLayoutHost`、`ExtensionHost.slotOwner()`，2 项守卫测试（`53079b2`、`#11`）
- [x] `ext.notifications` 迁移：管理器、三个事件适配器、叠层、历史、测试通知与设置页迁入扩展包；宿主改为转发命令结束/标题/会话结束/响铃/OSC 事件（`db99dc9`、`#12`）
- [x] `ext.search` 迁移：控制器与偏好迁入扩展包，宿主转发显示/隐藏/刷新；槽位 `terminal_search_bar`（`7a47843`、`#13`）
- [x] `ext.drawer` 迁移：控制器、会话列表适配器、配置、把手、颜色工具与会话详情迁入扩展包；扩展负责创建并绑定列表，宿主在服务连接后通知（`08f64b7`、`#13`）
- [x] `ext.split` 迁移：控制器改为只依赖 `Context` 并迁入扩展包，新增 `SplitScreenExtension`（槽位、启停、比例与边框）；会话挂载仍由宿主 `setSplitScreen()` 负责（`596e946`、`#14`）
- [x] 扩展查询 API 收敛为 `ExtensionHost.find/findRunning`，删除各处的 `instanceof` 强转（`767c575`、`#14`）
- [x] `builtin.vt`：新增核心终端行为设置页（OSC 52、悬停、滚动保持、内边距）与不可禁用的 `BuiltinVtExtension`；四个内边距项与两个开关从原页面迁入（`4053e59`、`#15`）

### 2.5 文档与治理

- [x] `FORK.md` §34 扩展管理与禁用语义；§33.1 补充 `ShellCommandHost` 与命名会话命令
- [x] `docs/EXTENSION_API.md` §9.1 M-E1 实施记录
- [x] `docs/HANDOFF.md` 刷新至当前状态
- [x] 本文件建立

---

### 2.6 UI 层优化（2026-11-23）

- [x] 形状令牌：`dimens.xml` 新增 `termux_dialog_corner_radius`（16dp）、`termux_row_corner_radius`（12dp）、`termux_chip_corner_radius`（8dp）与宽度/边距/内距；`ShapeTokenTest` 守卫 Java 常量与资源一致（`1f0d811`）
- [x] 对话框主题修正：`ThemeOverlay.Termux.Dialog` 改为覆盖 `alertDialogStyle`（`shapeAppearanceOverlay` 对对话框无效），删除未使用的旧 `TermuxAlertDialogStyle`（`c43fe8b`）
- [x] 共用构建器：`widget/TerminalDialogBuilder` 统一窗口背景、标题、自动滚动正文、按钮栏与尺寸；终端菜单、命名对话框、通知历史迁入（`c10a35c`、`6d1ee61`）
- [x] 默认圆角：终端菜单 0 → 16dp、通知卡片 0 → 12dp（`c10a35c`）
- [x] 设置搜索：`dialog_settings_search.xml` + `item_settings_search.xml`，Material 3 输入框、行布局与空状态（`c43fe8b`）
- [x] 应用级对话框改 `MaterialAlertDialogBuilder`：终止会话、URL 选择、会话数上限、风格未安装、备份恢复、扩展停用、长按复位（`a2345f5`、`e39607c`）
- [x] 偏好控件：`ColorPreference` 色板圆角与主题色描边、自定义颜色输入框；`OrderedItemsPreference` 行按下态、文字与分区标题取主题色（`e39607c`）
- [x] 抽屉：会话行 12dp 圆角涟漪、选中底色圆角、强调条圆头、列表 8dp 内距、头部按钮涟漪、底部按钮 Material 3（`70010a1`、`e39607c`）
- [x] 状态栏：分段 4dp 内距、可点击分段 8dp 圆角按下态（`70010a1`）
- [x] 命令面板：圆角改用令牌、chip 8dp、行高 12dp、列表高度随屏幕收缩（`3db4815`）
- [ ] 待评估：设置页的 `preferenceTheme`（androidx 未提供 Material 3 变体）；MD3 表面色跟随 `colors.properties`（需运行期 theme overlay，见第 6 节待决策）

---

### 2.7 交互与系统界面打磨（2026-11-23）

- [x] 扩展可用性：`TerminalMenuItems.State` 增加 `splitScreenAvailable`/`searchAvailable`/`sessionsDrawerAvailable`，扩展禁用后分屏、搜索、抽屉动作从菜单与命令面板隐藏；测试 10 项（`c917360`）
- [x] 抽屉强调条改为 3dp 内缩胶囊（距左 6dp、上下各 8dp、圆角 1.5dp），行最小高度 56dp，详情 12sp；列表去掉平台选择高亮与分隔线（`32d7c52`）
- [x] 系统栏：底部手势条留白（`5b85fb5`，实现已在真机反馈后回退，见 2.8）
- [x] 刘海：`setDisplayCutoutMode()` 在 `setContentView()` 前按 `fullscreen` 设置 `SHORT_EDGES`/`DEFAULT`；对话框宽度加 200dp 下限（`91f9f0b`、`55b3012`）
- [x] 命令面板与设置搜索按结果数收缩列表高度（`8db2e1b`）
- [x] 终端搜索条改为底部 16dp 圆角（`0a45190`）
- [ ] 待决策：额外键中的 `SEARCH`/`DRAWER`/`SPLIT_*` 在扩展禁用后仍显示但无动作，是否随扩展可用性过滤
- [ ] 待决策：设置/帮助/报告页顶栏仍取 `colorPrimary`（Material 2 观感），是否改为 `colorSurface`

### 2.8 真机反馈修复（2026-11-23）

- [x] 额外键闪烁：回退 inset 监听器里的 `setPadding`，恢复 `fitsSystemWindows` 与 `FLAG_FULLSCREEN` 原时机；保留 `setDisplayCutoutMode()` 的刘海适配（`DECISIONS.md` #22）
- [x] 命令面板改为跟随终端配色方案（`TerminalDialogBuilder` + 自绘视图），删除 4 个失效布局/背景资源；`TerminalDialogBuilder.setScrollable(false)` 支持自带列表
- [x] 抽屉会话列表上下各留 6dp 内距，会话卡片与头部、底部按钮不再贴边
- [ ] 待验证：闪烁是否消失（键盘弹起/收起、切换输入法、全屏开关各一次）
- [ ] 待验证：命令面板在深色/浅色与彩色强调色配色下的可读性、chip 与行按下态
- [ ] 待验证：抽屉首末行间隙与胶囊强调条的配合

### 2.9 面板接管与可配置入口（2026-11-23）

- [x] 面板搜索框加高（四边 16dp 内距、最小高度 56dp）（`71708e0`）
- [x] 通知卡片强调条改为 3dp 内缩胶囊，与抽屉会话行一致（`4d39d05`）
- [x] 命令面板接管终端动作：删除 `TerminalMenuDialog` 与 8 个外观属性，`terminal-menu-items` 决定面板动作集合与顺序，设置/帮助保证可达；扩展禁用时用 `TerminalMenuConfig.defaults()` 回退（`7c0ed84`）
- [x] 右键/菜单键/多选「更多」统一打开面板；`mouse-right-click` 支持 `auto`/`forward`/`palette`（`7c0ed84`）
- [x] `command-shortcuts` 属性：面板标签与按键处理同源，键盘页新增设置项，新增 `CommandShortcutsTest` 5 项（`7c0ed84`）
- [x] 选择浮条背景改 12dp 圆角；移除 `TerminalTextInputDialog` 对抽屉扩展的颜色依赖（`272248d`）
- [x] 架构复盘：`docs/APP_ARCHITECTURE_REVIEW.md` §9（分层、度量、剩余问题与优先级）
- [ ] 待验证：右键三种模式、快捷键改绑、额外键宏按键、面板接管「更多」、通知胶囊观感
- [ ] 待决策：`TerminalHost` 窄接口与源码导入守卫（架构复盘 P0/P1）

### 2.10 键盘兼容层（2026-11-23）

- [x] `ShellCommandShortcut`：字符匹配失败时回退到物理键码（覆盖键盘在修饰键下不报字符的情况）（`a1b589e`）
- [x] `keyboard-compat-log` 属性：`dispatchKeyEvent`、命令快捷键分发、视图客户端统一输出按键字段（`a1b589e`）
- [x] 设计方案 `docs/KEYBOARD_COMPAT.md`：三层（采集/路由/IME）与只读模块钩子（`docs` 提交）
- [x] 需求方书面豁免 `terminal-view`，新增 `getInputType`/`getImeOptions` 客户端钩子（`8a1fc86`）
- [x] `ime-input-type`（`auto`/`text`/`password`/`none`）与 `ImeInputType`、键盘页设置项、`ImeInputTypeTest` 6 项（`a5cba39`）
- [x] 输入法事件日志钩子（`onImeEvent`）与 Tab / Shift+Tab 直通（只读模块 + `app/`）
- [x] 真机日志定位：字母只出现在 `[activity]`、随后逐字符 `[ime] commitText(...)`，Tab 由 `handleTerminalTab` 直接写入 PTY，字符与按键分属两条通道导致顺序错乱
- [x] `ime-input-type=hardware`：接有物理键盘时上报 `TYPE_NULL`，物理按键直通终端；`HardwareKeyboard` 依据 `Configuration.keyboard` 与 `InputDevice.KEYBOARD_TYPE_ALPHABETIC`，键盘接入/拔出时重启输入连接
- [x] Tab 直通并入额外键栏锁定的虚拟 SHIFT，日志补 `imeInputType`、`hardwareKeyboard`
- [x] `ImeInputTypeTest` 扩至 8 项、新增 `HardwareKeyboardTest` 2 项
- [ ] 待真机验收：`hardware` 模式下 lazyvim 插入模式 Tab、Ctrl/Alt 组合键、node TUI 输入顺序
- [ ] 待真机验收：`hardware` 模式下软键盘 CJK 提交仍可用；`text` 模式下 Gboard 悬浮栏、组合文本残留
- [ ] 待验证：`keyboard-compat-log` 日志能否区分三条按键路径；改绑快捷键在硬件键盘下的表现

---

## 3. 进行中

无。UI 层优化第一批已提交，下一步是出包做真机观感验证（第 4.6 节新增项），之后回到 4.1 的 `TerminalStylingController` 评估。

---

## 4. 待办（按执行顺序）

### 4.1 X1 控制器拆分

- [x] `TerminalActionRunner`：把 `TermuxActivity.runTerminalMenuAction` 的 `switch` 迁入 `app/commands/`，改为「动作 → 执行器」映射
- [x] 为映射表补纯 JVM 测试（动作全覆盖、映射唯一、期望表完整）
- [x] `TerminalDrawerController`：抽屉配色、把手、开合配置与概要收拢到一处
- [x] 验收：`TermuxActivity` 行数下降（2544 → 2275）；同一动作只有一份实现
- [ ] 待观察：`TermuxActivity` 仍持有约 30 个与终端会话/样式相关的方法，下一轮优先评估 `TerminalStylingController`（窗口背景、状态栏配色、终端内边距与边距的重算）

### 4.2 `ext.statusbar`（已迁移，待真机验证）

- [x] 迁移状态栏实现与设置页，`ForkExtensions` 登记
- [x] 页面闸门：禁用后停止刷新并清空视图
- [ ] 待验证：位置（上/下）、overlay、内边距、分段点击、禁用后留白消失

### 4.3 `ext.notifications`（已迁移，待真机验证）

- [x] 视图槽位（`notification_overlay`）与状态栏内距查询
- [x] 迁移卡片、后台通知、历史、测试通知与设置页
- [x] 事件转发：命令结束、标题变化、会话结束、响铃、OSC 99 由宿主转给扩展
- [ ] 待验证：禁用后卡片与历史停止、测试通知四种样式、OSC 图标开关

### 4.4 其他扩展

- [x] `ext.menu`（终端菜单项与选择工具栏配置）
- [ ] `ext.split`（分屏控制与状态持久化）
- [ ] `ext.drawer`（会话抽屉与配色）
- [ ] `ext.search`（终端内搜索）
- [ ] `ext.volumekeys`（音量键动作）
- 每个扩展的验收：可单独禁用，禁用后其视图、监听与定时器全部释放

### 4.5 `builtin.vt` 属性开关页（已交付）

- [x] OSC 52、悬停上报、滚动保持与四个内边距项收拢为独立页面
- [x] 页面顶部标注「核心能力，仅可关闭行为」，扩展不可禁用
- [ ] 待评估：OSC 133 与焦点上报 1004 目前没有属性开关，需改只读模块才能提供，暂不实现

### 4.6 设备验证（无 `adb`，需出包后手工确认）

- [ ] UI 层：终端菜单/命名对话框/通知历史的圆角、边框与标题层级
- [ ] UI 层：抽屉会话行圆角涟漪与选中底色、强调条圆头、头部与底部按钮
- [ ] UI 层：状态栏分段间距与按下态；命令面板在键盘弹出后的列表高度
- [ ] UI 层：设置搜索的输入框、空状态与行布局；色板圆角；有序列表对话框行按下态
- [ ] 系统界面：手势小横条留白（终端底行与额外键是否被覆盖）、`fullscreen=false` 时状态栏与挖孔避让、全屏刘海是否真正填满、键盘弹起时额外键与小横条的关系
- [ ] 抽屉：胶囊强调条的位置、高度与选中行底色配合，切换会话后是否即时跟随
- [ ] 扩展禁用：分屏、搜索、抽屉动作不再出现在终端菜单与命令面板
- [ ] 禁用扩展后设置搜索不再出现其条目
- [ ] 禁用后经其他路径进入功能页只显示停用说明
- [ ] 命令面板四个入口（`Ctrl+Shift+P`、抽屉按钮、`command-palette` 菜单项、虚拟键 `F12`）
- [ ] 抽屉三个按钮与面板 `new-named-session`、`toggle-keyboard` 行为一致
- [ ] 右键在 `vim`／`tmux` 与 shell 中的两种表现；分屏下坐标换算
- [ ] 抽屉按钮着色与暗色主题
- [ ] 扩展设置页开关后返回终端的即时生效

---

## 5. 验证状态与已知限制

| 项 | 状态 |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | 通过（本机） |
| `:app:testDebugUnitTest` | 190 项，1 项失败：`FileReceiverActivityTest > testIsSharedTextAnUrl`（既有，Robolectric 4.10 + JDK 21，CI 不复现）；`terminal/*` 21 项全部通过 |
| `:app:assembleDebug` | 未执行，本机 NDK 缺 `libc++_shared` |
| 设备验证 | 2026-11-23 首次真机试机：启动即崩溃（资源文件中的旧类名，见 `app/DECISIONS.md` #16），已修复并补 `ResourceClassNameTest`；其余行为仍未验证 |
| CI | 推送后由 PR 触发；本机无 `gh`，状态待确认 |

---

## 6. 维护约定

1. 完成一项即在第 4 节勾选并补提交号，与代码改动分开提交。
2. 计划顺序变化时同步 `docs/HANDOFF.md` 第 5 节，避免两处结论不一致。
3. 决策、取舍与踩坑写 `app/DECISIONS.md`，本文件只记录「做什么、做到哪」。
4. 未实际执行的构建与测试不得标记为完成，须在第 5 节写明「未验证」与原因。
