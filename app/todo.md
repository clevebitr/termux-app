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
| 最近提交 | `d418a90 refactor: 将音量键动作提取为 volume-keys 扩展` |
| 规模 | `TermuxActivity` 2194 行；`commands/` 5 文件 491 行；`ext/` 34 文件 4700 行（`statusbar` 7、`menu` 7、`volumekeys` 4、`backup` 3） |
| 验证 | 本机 `:app:compileDebugJavaWithJavac` 通过；`:app:testDebugUnitTest` 164 项、1 项既有失败 |

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

### 2.5 文档与治理

- [x] `FORK.md` §34 扩展管理与禁用语义；§33.1 补充 `ShellCommandHost` 与命名会话命令
- [x] `docs/EXTENSION_API.md` §9.1 M-E1 实施记录
- [x] `docs/HANDOFF.md` 刷新至当前状态
- [x] 本文件建立

---

## 3. 进行中

无。下一项为第 4.1 节剩余的检查项，之后进入 `ext.statusbar`。

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

### 4.3 `ext.notifications`（13 文件 2704 行）

- [ ] 依赖 `ext.statusbar`（可选依赖）与事件源贡献点
- [ ] 迁移卡片、后台通知、历史列表与设置页
- [ ] 验收：禁用后卡片、后台通知与历史全部停止

### 4.4 其他扩展

- [x] `ext.menu`（终端菜单项与选择工具栏配置）
- [ ] `ext.split`（分屏控制与状态持久化）
- [ ] `ext.drawer`（会话抽屉与配色）
- [ ] `ext.search`（终端内搜索）
- [ ] `ext.volumekeys`（音量键动作）
- 每个扩展的验收：可单独禁用，禁用后其视图、监听与定时器全部释放

### 4.5 `builtin.vt` 属性开关页

- [ ] 仅属性开关：OSC 52、OSC 133、焦点上报 1004、悬停上报、滚动保持、内边距
- [ ] 设置页标注「核心能力，仅可关闭行为」
- [ ] 说明「下次创建终端视图时生效」的边界

### 4.6 设备验证（无 `adb`，需出包后手工确认）

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
| `:app:testDebugUnitTest` | 167 项，1 项失败：`FileReceiverActivityTest > testIsSharedTextAnUrl`（既有，Robolectric 4.10 + JDK 21，CI 不复现） |
| `:app:assembleDebug` | 未执行，本机 NDK 缺 `libc++_shared` |
| 设备验证 | 未执行，无 `adb` |
| CI | 推送后由 PR 触发；本机无 `gh`，状态待确认 |

---

## 6. 维护约定

1. 完成一项即在第 4 节勾选并补提交号，与代码改动分开提交。
2. 计划顺序变化时同步 `docs/HANDOFF.md` 第 5 节，避免两处结论不一致。
3. 决策、取舍与踩坑写 `app/DECISIONS.md`，本文件只记录「做什么、做到哪」。
4. 未实际执行的构建与测试不得标记为完成，须在第 5 节写明「未验证」与原因。
