# ARCHITECTURE_REVIEW — 架构复审与可扩展性报告

> 评估对象：`app_new/` 外壳模块（不含只读的 `terminal-core` 复制基线）。
> 评估时点：页面层拆分完成之后。
> 关联文档：`docs/UI_REVIEW.md`（界面与易用性）、`docs/UPGRADE.md`（方案与依赖）、`docs/DELIVERY_PLAN.md`（里程碑）。

---

## 0. 结论摘要

| 维度 | 评分（五分制） | 结论 |
| --- | --- | --- |
| 分层与依赖方向 | 4.0 | 纯逻辑层不再引用界面层，依赖方向可用一条命令校验；接线对象仍有部分放在 `ui` 包 |
| 模块划分粒度 | 4.0 | 主源 79 文件 8517 行，超过 300 行的只剩 1 个门面类型；页面层按「屏幕 — 区块 — 行」分包 |
| 状态与真源 | 4.0 | 导航、工作区、设置、会话配置各自单真源；界面只读快照 |
| 扩展成本 | 3.5 | 新增设置项约 4 处、新增页面约 4 处、新增输入源约 2 处；提示文案与会话类型仍需手工接纳 |
| 可验证性 | 3.5 | 单元测试 164 项覆盖逻辑层；界面层测试只能编译、无法在本机执行 |
| 平台与依赖约束 | 3.0 | `compileSdk 36` 限制依赖升级；终端核心只读限制了三项优化 |
| **综合** | **3.7 / 5** | 已具备「可长期演进」的结构，剩余风险集中在测试执行环境与少量硬编码 |

三条关键结论：

1. **依赖方向已经可校验**。`pane/`、`tabs/`、`config/`、`input/`、`session/`、`commands/`、`render/`
   没有任何 `import com.termux.shell.ui.*`（第 4.1 节的命令可复现），因此这些层的单元测试与后续移植不依赖界面。
2. **超级文件已消除到只剩门面**。拆分前 `ShellViewModel` 592 行、`ShellScreen` 413 行、`SettingsScreen` 390 行；
   现在唯一的例外是 `ShellViewModel`（522 行，其中约 200 行为转发），其余文件均不超过 280 行。
3. **扩展成本由「改多处」变为「登记一处」**：设置项由描述层驱动、页面由导航枚举驱动、输入由路由接口驱动，
   新增能力时的改动点可枚举（第 5 节给出矩阵）。

---

## 1. 评估方法

| 项 | 内容 |
| --- | --- |
| 代码度量 | 文件行数分布、包行数分布、最大文件清单、测试文件与用例计数 |
| 依赖检查 | 按包统计 `import` 方向，验证纯逻辑层不反向依赖界面层 |
| 结构走查 | 逐包核对职责、真源数量、组合期副作用、错误处理与生命周期 |
| 扩展推演 | 对 7 类典型扩展逐项推演「要改哪些文件、有几处、风险在哪」 |
| 构建验证 | `:app_new:assembleDebug`、`:app_new:testDebugUnitTest`（164 项）、`:app_new:lintDebug`、`:app_new:assembleDebugAndroidTest` |

未执行项：设备侧运行（本机无 `adb`）、仪器化测试执行（无设备）、Compose 预览渲染。

---

## 2. 当前结构

### 2.1 分层

```
┌───────────────────────────────────────────────────────────────────┐
│ 界面层 ui/shell · ui/settings · ui/pane · ui/session · ui/tabs     │
│        ui/palette · ui/ProfileEditorDialog · ui/ShellActivity      │
│  只读快照 + 回调动作，不含业务规则                                 │
├───────────────────────────────────────────────────────────────────┤
│ 状态与接线层                                                        │
│  ui/ShellViewModel（门面）  ui/nav  ui/workspace  ui/settings       │
│  ui/profiles  ui/SessionBinder  ui/SettingsWriter  ui/ProfileWriter │
├───────────────────────────────────────────────────────────────────┤
│ 纯逻辑层（无 Android 依赖，可 JVM 测试）                            │
│  pane/(树与布局)  tabs/(标签与编码)  config/(属性、配置、布局存储)   │
│  input/(键位表与路由)  session/(会话契约与注册表)  commands/  render/│
├───────────────────────────────────────────────────────────────────┤
│ 运行时层 terminal-core（复制基线，只读）：VT 解析、PTY、终端视图    │
└───────────────────────────────────────────────────────────────────┘
```

### 2.2 包行数分布（主源）

| 包 | 文件 | 行数 | 职责 |
| --- | --- | --- | --- |
| `ui`（根） | 14 | 1523 | 门面、活动、主题、通知、持久化管道、命令目录、编辑对话框 |
| `ui/settings` | 8 | 1169 | 设置页、描述层、行组件、搜索、设置控制器、配置区块 |
| `ui/shell` | 3 | 526 | 主界面、按档位的内容布局、标题行与窗口菜单 |
| `config` | 13 | 898 | 属性读写、配置定义、配置文件与会话配置存储、原子写、布局存储 |
| `session` | 9 | 625 | 会话契约、本地宿主、注册表、配置与命令构造、环境 |
| `input` | 5 | 467 | 键位表、加载器、动作常量、输入路由 |
| `tabs` | 3 | 440 | 标签工作区、标签、布局编码 |
| `ui/tabs` | 2 | 409 | 标签栏与标签项（拖拽、菜单、自动滚动） |
| `ui/pane` | 3 | 383 | 窗格区、分割条、叶子视图 |
| `ui/palette` | 2 | 365 | 命令面板与命令行 |
| `pane` | 2 | 315 | 窗格树类型、窗格布局纯函数 |
| `ui/session` | 2 | 290 | 会话列表与会话行 |
| `ui/workspace` | 1 | 197 | 工作区控制器 |
| `ui/nav` | 2 | 192 | 导航状态与导航器 |
| `ui/profiles` | 1 | 83 | 会话配置控制器 |
| 其余（`commands`、`render`、`log`、入口） | 4 | 330 | 命令模型、帧调度占位、日志、应用入口 |

### 2.3 文件规模

| 阈值 | 文件数 | 备注 |
| --- | --- | --- |
| ≤ 150 行 | 52 | 多数为单一职责类型或单屏区块 |
| 151–250 行 | 23 | 编解码、行组件、列表、纯函数集合 |
| 251–300 行 | 3 | `TabStripItem`（279）、`ProfileEditorDialog`（274）、`CommandPaletteView`（259） |
| > 300 行 | 1 | `ShellViewModel`（522），门面类型，见 4.3 |

---

## 3. 架构复审发现

| 编号 | 级别 | 发现 | 说明与建议 |
| --- | --- | --- | --- |
| R-1 | 已修复 | 门面承担工作区、设置、导航、配置四类规则 | 拆为 `WorkspaceController`、`SettingsController`、`ShellNavigator`、`ProfileController`，门面只做转发与跨域联动 |
| R-2 | 已修复 | 页面层单文件承载多个屏幕/区块 | `ShellScreen` 拆为 `ui/shell` 三个文件，`SettingsScreen` 拆为 `ui/settings` 三个文件，窗格、会话、标签、命令面板各自成包 |
| R-3 | 已修复 | 纯逻辑层与界面层边界不可校验 | 现在纯逻辑层零 `ui` 依赖，可用第 4.1 节的命令作为回归检查 |
| R-4 | P2 | 接线对象位置不统一：`SessionBinder`、`SettingsWriter`、`ProfileWriter` 位于 `ui` 包，但本身不依赖 Compose | 建议把 `SessionBinder` 迁到 `session/`，把两个 Writer 迁到 `config/`；迁移只需改 3 处 import，但会改动测试替身引用，宜单独提交 |
| R-5 | P2 | `ShellViewModel` 作为门面仍有约 200 行纯转发 | 可选做法：界面直接依赖控制器（约 40 个调用点 + 测试同步调整）。收益是门面降到 300 行以内，代价是一次大范围改动，当前收益不足 |
| R-6 | P2 | 提示文案由枚举承载：新增提示需同时改枚举与 `stringRes` 映射 | 影响面 2 处，可接受；若提示类型继续增长，可改为携带资源的 sealed class |
| R-7 | P2 | `ui` 根包仍偏大（14 文件 1523 行） | 下一步可把 `ProfileEditorDialog`（274）拆为对话框与字段两文件，并把 `ShellCommandCatalog` 移入 `ui/palette` |
| R-8 | P3 | `ProfileEditorDialog` 与 `SettingsProfileSection` 都持有「配置摘要」逻辑（后者已有 `profileSummary`） | 把摘要下沉到 `SessionProfile` 的纯函数，两处共用并可单测 |
| R-9 | P3 | 布局文件为窗口级单文件，多窗口互不区分 | 已在 `FileLayoutStore` 的约束中声明「最后写入者生效」；若后续需要按窗口恢复，需引入窗口标识 |

---

## 4. 依赖与约束检查

### 4.1 依赖方向（可复现命令）

```bash
# 纯逻辑层不得引用界面层：无输出即通过
grep -rn "import com.termux.shell.ui" \
  app_new/src/main/kotlin/com/termux/shell/{pane,tabs,config,input,session,commands,render,log}
```

当前结果：无输出（通过）。

### 4.2 允许的引用方向

| 来源 | 允许引用 | 禁止引用 |
| --- | --- | --- |
| 纯逻辑层 | 同层、`terminal-core` 契约 | `ui.*`、`androidx.compose.*` |
| 接线层（`ui/nav`、`ui/workspace`、`ui/settings` 的控制器、`ui/profiles`） | 纯逻辑层、同层控制器 | 具体屏幕与行组件 |
| 界面层（`ui/shell`、`ui/pane` 等） | 门面与控制器、纯逻辑层 | 启动协程或直接落盘 |
| 应用装配（`ShellApplication`、`ShellViewModelFactory`） | 全部 | 被其他层反向引用 |

### 4.3 关于 `ShellViewModel` 的例外

`ShellViewModel` 保持 522 行是有意取舍：它承担「界面唯一入口」的稳定性职责，测试与界面都通过它访问状态；
若改为直接依赖控制器，需要同步修改约 40 个调用点与全部视图模型测试。当前把预算上限设为 **550 行**，
并要求门面内不得出现业务规则（只允许转发、跨控制器联动与窗口生命周期相关的状态）。

---

## 5. 可扩展性评估

### 5.1 扩展场景矩阵

| 扩展场景 | 需要改动的文件 | 处数 | 风险 | 备注 |
| --- | --- | --- | --- | --- |
| 新增一个布尔/选项设置项 | `SettingsSpec`（键与说明）、`ShellSettings`（快照）、`SettingsController`（写回方法）、`ShellSettingsCatalog`（条目）、`strings.xml`×2 | 4–5 | 低 | `ShellSettingsCatalogTest` 会自动校验「每个控件都写到已定义键与合法取值」 |
| 新增一条命令面板命令 | `ShellAction`（常量）、`ShellCommandCatalog`、`ShellViewModel.execute` 分派、（可选）默认键位表 | 3–4 | 低 | 命令自动出现在面板与分组中，无需改界面 |
| 新增一种会话类型（如容器） | `SessionProfile`（校验）、`ProfileCommandBuilder`（启动命令）、`ProfileEditorDialog`（字段）、`SettingsProfileSection`（摘要） | 4 | 中 | `ProfileCodec` 的版本 1 需要兼容旧文档：新增字段必须是可选的 |
| 新增一个页面（如快捷键编辑器） | `ShellLayer` + `ShellNavigation`（方法）、`ui/shell/ShellScreen`（分支）、新屏幕文件、入口按钮或命令 | 4–5 | 低 | 返回键、Emoji 与覆盖层顺序由导航状态统一处理，不需要新增 `BackHandler` |
| 新增输入源（按键捕获、指针模式） | `InputRouter` 实现、`ShellActivity.dispatchKeyEvent` 接线、（可选）设置项 | 2–3 | 中 | 界面层零改动；需要设备侧验证 |
| 替换主题/配色来源 | `ShellTheme` 单点、（可选）设置项 | 1–2 | 低 | 动态取色已按 API 级别分流 |
| 接入跨进程会话宿主（M16） | `SessionProvider` 实现、`ShellApplication` 装配 | 2 | 高 | 契约已定义线程与生命周期约束；界面层不需要改动 |
| 新增窗格交互（如键盘调整比例） | `WorkspaceController.setSplitRatio`、`PaneSplitHandle` 语义动作 | 2 | 低 | 比例截断在纯逻辑层完成并有测试 |

### 5.2 扩展点地图

| 扩展点 | 契约/入口 | 注册位置 |
| --- | --- | --- |
| 配置项 | `SettingsSpec.Entry` + `ShellSettings` + 描述层条目 | `config/SettingsSpec.kt`、`ui/settings/ShellSettingsCatalog.kt` |
| 界面动作 | `ShellAction` 常量 + `ShellCommandCatalog.Actions` | `input/ShellAction.kt`、`ui/ShellCommandCatalog.kt` |
| 键位 | `KeymapTable` 文本语法 | `~/.termux/keymap.properties`、`input/KeymapLoader.defaultLines` |
| 页面 | `ShellLayer` / `ShellOverlay` | `ui/nav/ShellNavigation.kt` |
| 会话启动方式 | `SessionProfile` + `ProfileCommandBuilder` | `session/` |
| 会话宿主 | `SessionHost` / `SessionProvider` | `ShellApplication` 装配 |
| 终端视图 | `SessionProvider.attachView` + `TerminalViewPool` | `ui/TerminalViewPool.kt` |
| 持久化 | `SettingsRepository`、`ProfileStore`、`LayoutStore` | `config/` |
| 提示 | `ShellNotice` | `ui/ShellNotice.kt` |

### 5.3 可扩展性短板

| 短板 | 影响 | 缓解 |
| --- | --- | --- |
| 界面层测试无法在本机执行 | 页面结构调整只能靠编译与人工走查 | 仪器化测试已就位，待 CI 或设备补跑；逻辑尽量下沉到可测层 |
| `compileSdk 36` 上限 | `activity-compose 1.13`、`lifecycle 2.11` 无法升级 | 随 M15-1 工具链升级统一处理 |
| 终端核心只读 | 键盘 resize 去抖、滚动位置持久化、脏行渲染无法在应用层完成 | 记录于 `docs/UI_REVIEW.md` 10.3；必要时向需求方申请豁免 |
| 门面仍偏大 | 新增状态需同时改控制器与门面 | 门面预算 550 行；超过时按 R-5 方案评估 |

---

## 6. 建议的护栏

1. **文件规模预算**：单文件不超过 300 行，门面不超过 550 行；超过时必须在文件头写明理由（当前唯一例外为 `ShellViewModel`）。
2. **包依赖校验**：把第 4.1 节的命令加入 CI 的静态检查步骤；后续可替换为 detekt/ktlint 的 `ImportOrder` 与自定义规则。
3. **设置项完整性测试**：`ShellSettingsCatalogTest` 已能捕获「控件未接线」与「非法取值」，新增设置项时不需要额外补测。
4. **构建验证矩阵**：`assembleDebug`、`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest` 四项为提交前必跑项；
   设备可用时追加 `connectedDebugAndroidTest`。
5. **文档同步**：新增能力先更新 `docs/UPGRADE.md` 与本文的扩展点地图，再改代码。

---

## 7. 结论

当前结构已满足「可长期演进」的三条判据：依赖方向单向且可校验、每类状态只有一个真源、常见扩展的改动点可枚举。
剩余主要风险不在结构，而在验证条件（无设备、`compileSdk` 上限、终端核心只读）。

建议的后续顺序：

1. 设备侧补跑仪器化测试与手感验证（拖拽、撤销、布局恢复、键盘重排）；
2. 按 R-4 迁移接线对象到 `session/` 与 `config/`，按 R-7 收敛 `ui` 根包；
3. 随 M15-1 提升工具链后升级依赖，并把静态检查（文件预算 + 包依赖）纳入 CI。
