# UPGRADE — 版本更新与「桌面级外壳」改造方案

> 定位：本文件是**决策与排期文档**，回答两个问题：
> 1. 工具链/上游版本要升到什么、按什么顺序升；
> 2. **app 外壳（窗口 / 窗格 / 输入 / 帧 / 视觉）**要换成什么结构，才能给出真正的桌面级体验。
>
> 特性说明在 [`FORK.md`](../FORK.md)，构建/签名在 [`FORK_BUILD.md`](../FORK_BUILD.md)，
> 决策与踩坑在 [`DECISIONS.md`](../DECISIONS.md)。
> **`PLAN.md` 由 plan 工具维护，本文件不改它**；里程碑落地时把 M12–M15 喂给 plan 工具即可。

---

## 0. TL;DR

外壳的三根地基目前仍是「手机版」，功能层已经很桌面：

| 地基 | 现状 | 目标 |
| --- | --- | --- |
| **窗口模型** | `singleTask`、单窗格 + 最多 2 格分屏、会话只在抽屉列表里 | 多任务实例（桌面模式并排）+ Pane/Tab 树（N 格、可拖、布局落盘） |
| **输入协议** | xterm 级：无 kitty keyboard、无 modifyOtherKeys、无拖放、无横向滚轮 | 现代键序协议 + keymap + 拖放 + 鼠标语义补齐 |
| **帧管线** | 每次输出 `invalidate()` 整屏，无 dirty-region，无同步输出 | dirty-row 增量 + 行级缓存 + `DECSET 2026` 原子帧 |

结论：**不要再往 `TermuxActivity` 上叠功能**，先换窗口模型、输入协议、帧管线；其余（Material 3、Kotlin、AGP 9）是配套工程债。

---

## 1. 现状快照

### 1.1 版本矩阵

| 项 | 当前值 | 目标值 | 备注 |
| --- | --- | --- | --- |
| AGP | `8.13.2`（`build.gradle`） | **9.5.x** | 官方 stable 9.5.1（2026-11） |
| Gradle wrapper | `9.2.1` | **9.6** | AGP 9.4+ 的最低/默认 |
| compileSdk | `36` | **37** | AGP 9.4 起支持 API 37 |
| minSdk | `21` | **24** | 21 只剩兼容死分支（`tools:targetApi="m"` 等） |
| targetSdk | `28` | **28**（见 §6） | exec/W^X 硬约束，不可直接升 |
| Java | source/target `1.8` | **17 toolchain** | AGP 9 时代的基线 |
| desugar_jdk_libs | `1.1.5` | **2.1.x** | 现在靠 `lint.disable 'CoreLibDesugaringV1'` 掩盖 |
| Robolectric | `4.10` | 4.16+ | compileSdk 37 需要新版 |
| material | `1.12.0` | M3 1.13+ | 视觉层换代 |
| drawerlayout / preference | 1.2.0 / 1.2.1 | 随 M3 换代 | 抽屉/设置页 |
| Guava | `24.1-jre` | 33.x | 2017 年版本，靠 empty-listenablefuture 打补丁 |
| markwon | `4.6.2` | 保留或换 commonmark | 上游停更 |
| 上游基线 | 0.118.x 时代 | 季度合并 | 见 §7 |
| 打包的 bootstrap | `2026.02.12-r1` | 跟上游 | 已是最新 |

已对齐上游的关键修复：`hiddenapibypass 6.1`（Android 16 QPR1 崩溃）、`MANAGE_EXTERNAL_STORAGE` 声明、
`serialVersionUID`、bootstrap 更新。

### 1.2 相对上游仍缺的能力（会影响桌面体验，非仅版本号）

| 缺口 | 影响 |
| --- | --- |
| `winsize` 的 `.ws_xpixel` / `.ws_ypixel` 与 CSI 像素尺寸上报 | sixel / iTerm 图片按像素定位不准；`SGR-pixels`(1016) 鼠标无法实现 |
| `TerminalEmulator` 的 APC/DCS 状态机重构、未知 CSI 参数/中间字节消费 | 现代 TUI 探测序列（DECRQM、kitty 私有 CSI）可能吞字节或错位 |
| `WcWidth` Unicode 15 | emoji / 新码位宽度错 |
| AutoFill、Share selected text、PGUP/PGDN 长按、`Shift+PgUp` 语义 | 与上游交互不一致，合并时冲突 |

### 1.3 已经具备的桌面级素材（别重写）

OSC 8 / 52 / 99 / 777 / 133、sixel + iTerm 图片、`DECSET 1004` 焦点、`DECSET 1003` hover、
光标自适应配色、分屏（2 格）、状态栏、通知卡片、命令标记、回滚不拽底、真斜体。
这些是**协议层**的资本，缺的是壳层编排。

---

## 2. 目标架构（分层与责任边界）

```
┌──────────────────────────────────────────────────────────────┐
│ UI 进程（未来可为 targetSdk 37 的独立 APK，见 §6-B）           │
│                                                              │
│  WindowShell   ← documentLaunchMode 多实例；窗口→任务→会话组    │
│    └─ LayoutHost（WindowSizeClass 三档：compact/medium/expanded）│
│         ├─ PaneSplitter（N 格二叉分割树，可拖拽，落盘恢复）      │
│         │    └─ TabStrip（每 pane 多 tab）                     │
│         ├─ SessionRail（宽屏左栏）/ Drawer（窄屏）              │
│         └─ StatusBar / NotificationStack / SearchOverlay      │
│                                                              │
│  InputRouter  ← 硬件键盘/鼠标/触摸/拖放 → 统一 CellInput 事件    │
│  FrameScheduler ← dirty rows + DECSET 2026 原子帧 → 单次 vsync  │
└───────────────────────────┬──────────────────────────────────┘
                            │ 现有 TermuxService / TerminalSession（保持）
┌───────────────────────────┴──────────────────────────────────┐
│ Runtime（必须留在 targetSdk 28 侧）：PTY spawn、exec、bootstrap │
└──────────────────────────────────────────────────────────────┘
```

设计约束（来自现有代码的真实状态）：

* `TermuxActivity` **2447 行**、`TermuxTerminalViewClient` **1046 行**、
  `TermuxTerminalSessionActivityClient` **774 行** —— 这就是为什么 `WindowShell`/`PaneTree`
  必须先拆边界再加：**在动手 P0-2 之前先做 §4 的 W0 抽取**。
* 会话生命周期在 `TermuxService`（`mShellManager.mTermuxSessions`），UI 必须能随时重建
  → 所有 pane/tab/滚动状态放 `ViewModel` 或由 Service 持久化，**不能放 Activity 字段**。
* `android:configChanges` 已覆盖 `orientation|screenSize|smallestScreenSize|density|screenLayout|keyboard|keyboardHidden|navigation`，
  但仍有多处 `recreate()`（`updateTermuxActivityStyling(ctx, true)`）→ 多窗口下会丢焦点，需改为局部重建。

---

## 3. 工作项清单

优先级：**P0 = 桌面级地基**，P1 = 工程现代化，P2 = 增强/长期。
每项给出「动机 / 改动点 / 验收」。

### P0-1 窗口模型：多实例 + Pane 树

#### W0（前置）拆分巨型类
* 动机：不加这步，Pane/Tab 会全部堆进 `TermuxActivity`。
* 改动点：
  * `TermuxActivity` → 只负责窗口/insets/生命周期；会话与 UI 状态迁到 `TerminalUiViewModel`
    （`androidx.lifecycle:lifecycle-viewmodel`，现阶段可用 ViewModel + 手动 factory，不必上 Hilt）。
  * `TermuxTerminalSessionActivityClient` 的「当前会话 / 会话切换 / 分屏」下沉为
    `SessionSelectionController` + `PaneLayoutController`。
  * `TerminalSplitScreenController` 保留为 `PaneLayoutController` 的 2 格退化实现（先别删，保证回滚）。
* 验收：`TermuxActivity` < 800 行；现有分屏/会话切换/搜索/状态栏行为零回归（真机清单见 §9）。

#### W1 多任务实例（桌面模式并排）
* 动机：Android 16/17 的桌面窗口（desktop windowing）与平板分屏下，
  `singleTask` 会把所有启动合并进同一个任务，无法「两个窗口各跑一个会话」。
* 改动点（`app/src/main/AndroidManifest.xml:56-59`）：
  ```xml
  <activity android:name=".app.TermuxActivity"
      android:launchMode="standard"
      android:documentLaunchMode="always"
      android:resizeableActivity="true"
      android:configChanges="..." />
  ```
  * 每个任务记录自己的 `sessionGroupId`（`Activity.getTaskId()` → 持久化映射），
    窗口关闭后组内会话是否保留由属性 `multi-window-session-policy=detach|kill`（默认 `detach`）决定。
  * 「新建窗口」入口：长按菜单 + `Ctrl+Shift+N`。
  * 注意：`documentLaunchMode` 与 `singleTask` 互斥；`documentLaunchMode` 需要
    `resizeableActivity="true"`（已满足）。
  * `taskAffinity` 留空，配合 `android:excludeFromRecents="false"`。
* 验收：桌面模式/分屏下能同时开 2–3 个 Termux 窗口，各自独立会话；最近任务里能分别恢复；
  从最近任务划掉一个窗口不影响另一个窗口的会话。

#### W2 Pane/Tab 树（替换写死的 2 格分屏）
* 动机：2 格不够用，且无法持久化。
* 改动点：新增 `app/.../terminal/pane/`：
  ```java
  abstract class PaneNode { RectF bounds; }
  final class PaneLeaf  extends PaneNode { String sessionId; int tabIndex; List<String> tabs; int scrollRow; }
  final class PaneSplit extends PaneNode { boolean vertical; float ratio; PaneNode first, second; }
  ```
  * `PaneLayoutController`：命中测试、分隔条拖拽（带最小尺寸约束 `MIN_PANE_COLS/ROWS`）、
    `split(leaf, dir)` / `close(leaf)` / `swap(a,b)` / `zoom(leaf)`（tmux 风格）。
  * 布局落盘：`~/.termux/pane-layout.json`（版本化 `version: 1`），App 冷启动按窗口恢复；
    会话已被杀时退化为「新会话占位」。
  * 分隔条 / tab 栏样式跟随 `colors.properties`（复用现有 `TerminalColorScheme` 派生逻辑）。
  * 终端视图复用：每 leaf 一个 `TerminalView`（不要在 pane 间搬 View，避免 renderer 状态污染）。
* 验收：能开 4 格 + 每格 2 tab；拖拽分隔条无闪烁；旋转/换主题/被系统杀后布局恢复；
  关闭一个 pane 时焦点按 tmux 规则迁移。

#### W3 自适应布局（WindowSizeClass）
* 动机：现在宽屏就是「手机 UI 被拉大」。
* 改动点：加 `androidx.window:window`，用 `WindowMetricsCalculator` 算宽度档位（View 体系即可，
  无需引 Compose）：
  `compact(<600dp)` → 抽屉 + 单 pane；`medium(600–840)` → rail + 双 pane；
  `expanded(≥840)` → rail + tab 栏 + 可 N 分。抽屉把手（`DrawerHandleView`）只在 compact 生效。
* 验收：折叠屏展开/折叠、自由窗口拖拽、桌面模式下布局切换无重建、无状态丢失。

### P0-2 输入层：把「能当桌面用」补齐

#### I1 kitty keyboard protocol + xterm modifyOtherKeys
* 动机：没有它，Neovim/Helix/Emacs 无法区分 `Ctrl+Shift+A` 与 `Ctrl+A`，Alt 只能发 ESC 前缀。
* 改动点：
  * `TerminalEmulator`：`CSI > flags u`（push）/ `CSI > u`（pop）/ `CSI < u`（pop n）/
    `CSI = flags ; mode u`（set）；`CSI ? u` 回报当前 flags。
  * `TerminalView` → `KeyHandler`：在 flags 非 0 时改用 CSI-u 编码
    （`CSI unicode-key-code:shifted:base ; modifiers:event-type ; text u`），
    至少实现 flag `1`（disambiguate）与 `4`（report alternate keys）；事件类型 `8` 可选。
  * `modifyOtherKeys`（xterm）：`CSI > 4 ; n m`，作为老程序兜底。
  * 需要 `TerminalEmulator` 暴露 `getKittyKeyboardFlags()`，`TerminalViewClient` 增接口（默认 0，保证插件不炸）。
* 验收：`nvim --headless` 不行，用真机跑 `nvim` + `:map <C-S-a>` 与 `hx`（Helix）确认；
  kitty 的 `showkey`/`kitten` 不可用则以 `printf %s | xxd` 方式核对字节。

#### I2 keymap 表化（壳层快捷键）
* 改动点：新增 `~/.termux/keymap.properties`（缺省内置一份），语法 `ctrl+shift+t = new-tab`；
  `TermuxTerminalViewClient.onKeyDown` 先查 keymap 再落到 `KeyHandler`。
  默认绑定：`Ctrl+Shift+T/W` 新/关 tab、`Ctrl+Tab`/`Ctrl+Shift+Tab` 切 tab、`Alt+方向` 切 pane、
  `Alt+1..9` 跳到第 n 个 pane、`Ctrl+Shift+N` 新窗口、`Ctrl+Shift+P` 命令面板（可选）。
* 注意：与 `disable-hardware-keyboard-shortcuts` 的语义要对齐（该属性为 true 时 keymap 也禁用）。

#### I3 拖放
* 动机：桌面级必需，现在 `TerminalView` 完全没有 `onDragEvent`/`OnDragListener`。
* 改动点：
  * 接收 `text/plain` → 按 bracketed paste 规则插入。
  * 接收 `text/uri-list` → 复制到 `$HOME`（`TermuxFileUtils` 已有基于 `ContentResolver` 的实现），
    或属性 `terminal-drop-action=copy|insert-path`（默认 `copy`）。
  * 拖出：选中文本/文件 → `ACTION_SEND`（长按菜单已有复制，加「拖出」目标）。
* 验收：文件管理器拖入 → `$HOME` 出现文件且权限正确；从浏览器拖入文本不触发 IME 弹跳。

#### I4 鼠标语义补齐
* 横向滚轮 `MotionEvent.AXIS_HSCROLL`（映射到 `MOUSE_WHEEL_LEFT/RIGHT` 的 SGR 64/65 变体）；
* 中键 → 主选择区粘贴；右键 → 长按菜单（替代现在的长按）；
* 双击选词 / 三击选行（`TerminalView` 已有选择系统，加 `GestureDetector` 分支）；
* `OSC 22` 光标形状（`CSI Ps SP q` DECSCUSR 已有部分）→ 映射到 `TerminalView.setCursorStyle`；
* SGR-pixels (`1016`) 依赖 §1.2 的像素尺寸上报，先补 `ws_xpixel`。

### P0-3 帧管线：从「整屏重画」到「原子帧」

#### R1 dirty-row 增量失效
* 现状：`TerminalView` 在输出后直接 `invalidate()` 整屏；`TerminalRenderer` 逐行全量绘制。
* 改动点：
  * `TerminalBuffer`/`TerminalRow` 维护 `dirtyRowMin/Max`，
    `TerminalEmulator` 暴露 `getAndClearDirtyRows()`；`TerminalView.onDraw` 只画该区间。
  * 行级缓存：每行内容（不含光标/搜索/命令标记）渲染进 `RenderNode` 或行 `Bitmap`，
    行内容未变时直接复用；光标/高亮/标记画在独立叠加层。
  * `TerminalRenderer` 的 `drawTextRun` 路径不变（保留现有的 BiDi 修复）。
* 验收：`yes | pv -l -L 5000` 类高吞吐输出时 `Choreographer` 掉帧数 < 5%/s；
  5000 行回滚拖动无卡顿；内存增量 < 8MB（行缓存上限按可见行 + 预取行）。

#### R2 `DECSET 2026` 同步输出
* 动机：nvim/helix/pi 这类差分渲染 TUI 会声明原子更新，缺失时会出现「半帧」「闪」，
  并且和你已实现的「回滚不被拽底」互相干扰。
* 改动点：`mapDecSetBitToInternalBit` 加 `2026`；置位期间**照常解析输出**但抑制
  `TerminalSessionClient.onTextChanged` → view 重绘；复位时发一次合并通知。
  必须加 **300ms 超时保险**：程序崩溃在半帧状态时强制刷新，否则画面冻结。
* 验收：`nvim` 快速滚动/`:Telescope` 打开无半帧；`synchronized output` 测试脚本
  （`printf '\e[?2026h ... \e[?2026l'`）无撕裂。

#### R3 像素尺寸上报（§1.2 缺口）
* `TerminalEmulator.resize(cols, rows, cellWidthPx, cellHeightPx)` 已有字段，补齐
  `winsize.ws_xpixel/ws_ypixel`（JNI 侧 `JNI.java` + `terminal-emulator/src/main/jni`）与 CSI 上报。
* 验收：`stty size`/`ioctl(TIOCGWINSZ)` 在 termux 内返回正确像素；sixel 图不再按字符网格缩放。

### P1 视觉层与工程债

#### V1 Material 3 + 自适应组件
* material 1.13+，`Theme.Material3.*`；`NavigationRail` 用于 medium/expanded；
* 状态栏（`statusbar/StatusBarView` 337 行 + `StatusBarMetrics` 530 行）与通知卡片可以用 Compose 重写，
  但 **TerminalView 一律 `AndroidView` 包一层**，不要进 Compose 合成（会毁掉 §R1 的脏区收益）。
* 预测性返回需要 targetSdk 33+，**当前不可用**（§6），先在 B 方案里排期。

#### C1 配置层收敛
* 三套通路（`termux.properties` 文件 / `PreferenceDataStore` / `SharedPreferences`）→ 一个
  `SettingsRepository`（内存快照 + 变更监听 + 原子写入）。保留 `termux-reload-settings` 兼容。
* 现值：`TermuxPropertyPreferenceDataStore` 的「初始化回写默认值」守卫已是补丁，
  收敛后这类坑可以根除。

#### K1 Kotlin 化（app 模块）
* 只在 `:app` 引入 Kotlin（`terminal-emulator`/`terminal-view` 保持 Java，VT 核心不动）；
* AGP 9 自带 Kotlin 支持，无需单独插件。新增代码一律 Kotlin，旧代码不强制重写。

#### T1 工具链升级（独立 PR，不进功能分支）
* 顺序：`minSdk 24` → `desugar 2.1.x` → `compileSdk 37` → `Gradle 9.6` → `AGP 9.x` → `Java 17`。
* AGP 9 迁移检查表（逐条验证官方 release notes）：
  * `packagingOptions` → `packaging`；`splits` / `variant.outputs` API 变化；
  * `buildConfig` 已显式 `true`（保持）；`nonTransitiveRClass` 默认 true（与 `DECISIONS` 里那个 R 坑一致）；
  * 内置 Kotlin：与 `kotlin-android` 插件二选一，避免重复；
  * `coreLibraryDesugaring` 与 `lint` 的版本校验重新打开（删掉 `disable 'CoreLibDesugaringV1'`）。
* 验收：`Fork Build` CI 三套产物（app + 6 插件）全部编译通过、签名摘要一致（见 `FORK_BUILD.md`）、
  单元测试绿。

### P2 增强

* **字体**：Typeface 回退链（CJK/emoji/符号）+ `MeasuredText` 或 HarfBuzz shaping（组合字符、连字、复杂文种），
  可选 `font-ligatures=true`。现状逐 run `drawTextRun`，混排不可靠。
* **`SGR-pixels`(1016)** 鼠标、**kitty graphics protocol**（图片复用现有 `TerminalBitmap`/`ITermImage` 抽象）。
* **命令面板**（`Ctrl+Shift+P`）：搜索/切换会话/切主题/开分屏，桌面级常见入口。
* **会话持久化**：进程被杀后按 `pane-layout.json` 恢复 + `detach` 策略下用 `tmux`/`dtach` 兜底（可选依赖）。

---

## 4. 里程碑（建议排期）

| 里程碑 | 内容 | 工作项 | 预估 | Gate（不满足不进下一个） |
| --- | --- | --- | --- | --- |
| **M12** | 帧管线先行 | R1, R2, R3 | 1.5–2 周 | 掉帧 <5%/s；`nvim` 无半帧；回滚不拽底仍生效 |
| **M13** | 窗口与窗格 | W0 → W1 → W2 → W3 | 3–4 周 | 两窗口独立会话；4 格 + tab；布局冷启动恢复；宽屏不重建 |
| **M14** | 输入层 | I1, I2, I3, I4 | 2–3 周 | Neovim/Helix 键序正确；拖入/拖出可用；横向滚轮可用 |
| **M15** | 工程与视觉 | T1, V1, C1, K1 | 3 周（可并行） | AGP 9 CI 全绿；设置页与状态栏在新壳下零回归 |
| — | targetSdk 战略 | §6-B 的 POC | 独立 | 见 §6 |

顺序理由：R1/R2 改的是 `TerminalView`/`TerminalEmulator`，与 M13 的壳层重构**文件冲突最小**，
先做能立刻开箱验收；W0 是 M13/M14 的前置；T1 是纯构建改动，适合任意时刻插队。

> 本节为方案级排期。可执行的任务分解、文件清单与验收标准见
> [`DELIVERY_PLAN.md`](DELIVERY_PLAN.md)；两者不一致时以交付计划为准，并同步修订本节。

---

## 5. 上游合并节奏

* 每季度一次 `upstream/master` merge（当前上游已到 `0.119.0-beta`，含 `TermuxConstants` 命名、
  插件 API、`serialVersionUID`、APC/DCS 重构、`ws_xpixel`、`WcWidth` 15 等）。
* 冲突最小化原则（继续沿用 §25 的包划分）：
  1. 新能力优先用**新文件**实现，对上游文件只加 hook（当前 `statusbar/`、`terminal/menu/`、`notification/` 已符合）；
  2. properties 解析集中在一处，升级时整块替换；
  3. `TermuxActivity` 尽量回到上游结构（W0 拆分的另一个收益）。
* 合并前先跑：`Unit tests`、`Fork Build`、真机清单（§9）。

---

## 6. targetSdk 战略（必须先决策）

背景：`targetSdk=28` 是为了让 App 能 `exec()` 自己可写目录里的二进制（Android 10+ 的 W^X 限制）。
AOSP 的 `PLATFORM_MIN_SUPPORTED_TARGET_SDK_VERSION` 已抬到 28，并按「落后 4 个大版本就抬」的节奏继续上升
→ 长期风险不是「体验差」，而是**装不上/起不来**。上游拿到 NLnet 资助在做 APKLF
（把可执行文件放进只读 APK lib 目录以摆脱该限制）。

| 方案 | 做法 | 代价 | 收益 |
| --- | --- | --- | --- |
| **A. 永远是 28** | 现状 | 0 | Play 上架不可能；预测性返回/per-app language/FGS 类型/edge-to-edge 强制适配全部拿不到；未来可能被系统拒装 |
| **B. 双 APK 拆分（推荐）** | 主 UI 用新包名 `targetSdk 37`，通过 AIDL/`Messenger`/本机 socket 连 `com.termux`（保持 28）的 Runtime 进程 | 中高：装机流程多一步、签名/`sharedUserId` 约束要重算、两条 CI 产物 | 现代壳 + 现代 API 全拿到，且不破坏 exec |
| **C. 跟上游 APKLF** | 二进制以 APK 库形式分发，`targetSdk` 可升 | 极高，且与用户自装包模型冲突 | 官方终局 |

**建议**：短期走 **B**，同时观察 C。B 与 M13 的 W0 拆方向一致 ——
拆进程的边界就是拆 ViewModel/Service 的边界，做完 W0 再拆 APK 成本最低。

B 的关键设计（POC 范围）：
* Runtime APK（`com.termux`，28）：保留 `TermuxService`、`TermuxSession`、`ExecutionCommand`、
  bootstrap、`$PREFIX`；对外暴露一个 `IRuntimeService`（AIDL：`createSession(cols, rows, cwd, cmd)`、
  `resize`、`write`、`close`、`onOutput(byte[])`、`onExit(int)`）。
* UI APK（`com.termux.shell`，37）：`TerminalView` + Pane 树 + M3 壳；无 PTY 逻辑。
* 传输：AIDL 直连（同 `sharedUserId` 可省权限），跨进程输出走 `ParcelFileDescriptor` 管道以避免
  Binder 事务上限（当前 `TerminalSession` 已经用文件描述符读写，这条最省事）。
* 约束：插件（api/boot/float/styling/tasker/widget）仍依赖 `com.termux` 的 `sharedUserId` 与
  signature 权限 → Runtime APK 保留 `com.termux` 包名，UI 换包名，即可**不破坏插件生态**
  （详见 `DECISIONS.md` 的签名与 `sharedUserId` 条目）。

---

## 7. 风险登记

| # | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| R-1 | 系统抬高 `MIN_SUPPORTED_TARGET_SDK` | 无法安装 | §6-B 排期；每次 Android 大版本发布后检查该常量 |
| R-2 | 上游 0.119+ 大重构，fork 改得越深越难合并 | 长期不可维护 | 季度合并；新能力走新文件；W0 让 `TermuxActivity` 回归上游结构 |
| R-3 | 多实例后会话/文件锁竞争（同一 `$PREFIX` 并发 `apt`） | 数据损坏 | `apt` 级别不解决，但 UI 层给提示；会话组策略默认 `detach` 不 `kill` |
| R-4 | dirty-row 缓存与终端的「行内容不变但背景/反显变化」边界 | 视觉错乱 | 缓存 key 包含行内 `TextStyle` 哈希；全屏样式变化（`DECSET 5`、`OSC 10/11`）时整屏失效 |
| R-5 | `DECSET 2026` 半帧冻结 | 画面卡死 | 300ms 超时保险 + 复位时强制刷新 |
| R-6 | AGP 9 迁移破坏 `testkey` 签名与插件一致性 | 装机失败 | §T1 验收要求 CI 校验签名摘要（已有 `FORK_BUILD.md` 流程） |
| R-7 | 本机无 JDK/SDK，只能靠 CI 验证 | 反馈慢（6–8 分钟/轮） | 沿用 `DECISIONS.md` 里的静态检查脚本（`javabal2` / `xmlcheck` / `strcheck`）；逻辑密集改动补单测 |

---

## 8. 属性/接口命名约定（不破坏上游）

新增属性一律「默认关闭或等同上游行为」，与 `FORK.md` 的既有约定一致：

```properties
# 窗口/窗格
multi-window-session-policy=detach      # detach(默认) | kill
pane-layout-restore=true                # 默认 true（有落盘才恢复）
pane-min-columns=20
# 输入
terminal-kitty-keyboard=true            # 默认 true？建议默认 true，仅当程序请求时生效
terminal-drop-action=copy               # copy(默认) | insert-path
terminal-ligatures=false
# 帧
terminal-synchronized-output=true       # 默认 true（仅当程序请求）
terminal-render-cache=true              # 默认 true
```

> 注：`kitty`/`2026` 这类「由程序发起」的协议**不需要开关**——程序不请求就不改变行为，
> 所以可以默认开启；只有会改变用户可见行为（拖放、连字）的才需要默认关。

---

## 9. 验证方法

本地环境已具备 JDK 21、Android SDK（`platforms/android-36`、`build-tools/35.0.0`、`ndk/29.0.14206865`）
与 Gradle 9.2.1，可构建 `app_new` 并运行其单元测试；无 `adb`，故设备侧验证仍需人工执行。
`DECISIONS.md` 中「本机无法编译」的旧记录仅适用于当时的 Termux 内环境。

每条工作项的验收都要落到两类证据：

1. **CI**：`Unit tests` + `Fork Build`（app + 6 插件、签名摘要一致）+ 必要时新增的截图/基准任务；
2. **真机清单**（每项写进 PR 描述，逐条勾）：
   * 帧：`yes | head -c 200M` 流式输出不掉帧；5000 行回滚拖动；`nvim` 快速重绘；
   * 窗口：桌面模式开 2 窗口；折叠屏展开/折叠；系统杀进程后布局恢复；
   * 输入：`nvim` 的 `Ctrl+Shift+*` 映射、Helix、`tmux` 内嵌 pane 战争（双重键绑定不冲突）；
   * 回归：`FORK.md` §1–§32 里涉及 `TerminalView`/`TermuxActivity` 的条目逐项抽查
     （重点：Gboard CJK、extra keys 面板、搜索、命令标记、状态栏、通知卡片、回滚不拽底）。

---

## 10. app_new 骨架（阶段一成果）

阶段顺序固定为**骨架 → 规划 → 动工**，当前处于阶段一结束、阶段二开始之前。
阶段一只交付目录结构、包边界、公开契约与最小可运行实现，**不实现任何 P0 特性逻辑**。

### 10.1 模块边界

| 项目 | 取值 | 依据 |
| --- | --- | --- |
| 模块路径 | `app_new/` | `AGENTS.md` 第 3 节：新代码只允许位于该目录 |
| `applicationId` | `com.termux.shell` | 与旧应用 `com.termux` 并存安装，便于同机对比 |
| `namespace` | `com.termux.shell` | 同上 |
| `minSdk` | 24 | 旧模块的 21 仅保留兼容分支，无实际收益 |
| `targetSdk` | 默认 28，可经 `-PappNewTargetSdk=36` 覆盖 | 第 6 节 exec 与 W^X 约束；现代外壳形态须在运行时拆分完成后启用 |
| `compileSdk` | 36（继承根项目） | 与旧模块保持一致，避免同一构建内出现两套平台版本 |
| 语言与工具链 | Java 17 源码级别 | 骨架阶段不引入 Kotlin 插件，避免未经本地验证的工具链变更 |
| 依赖 | `androidx.annotation`、`androidx.core`、`androidx.appcompat`、`androidx.window`、`material` | 版本与旧模块保持一致 |
| 旧模块依赖 | 无 | 不引用 `app/`、`terminal-view/`、`terminal-emulator/`、`termux-shared/` 的源码 |

旧模块唯一改动为 `settings.gradle` 新增 `include ':app_new'`，属构建装配，单独提交。

### 10.2 包结构与职责

| 包 | 类型 | 职责 | 阶段一状态 |
| --- | --- | --- | --- |
| `com.termux.shell` | `ShellApplication` | 进程入口与单例装配 | 已实现（内存配置） |
| `com.termux.shell.log` | `ShellLog` | 统一日志标签 `Shell/<子标签>` | 已实现 |
| `com.termux.shell.config` | `SettingsRepository`、`MemorySettingsRepository` | 配置访问契约 | 契约与内存实现；持久化实现待阶段三 |
| `com.termux.shell.ui` | `ShellActivity`、`LayoutHost`、`WindowWidthClass`、`WindowSizeResolver` | 窗口宿主、档位判定、布局装配、系统栏内边距 | 已实现（三档布局与多窗口声明） |
| `com.termux.shell.pane` | `PaneNode`、`PaneLeaf`、`PaneSplit`、`PaneLayoutController`、`PaneLayoutCodec`、`PaneHostView` | 窗格树结构、结构操作、序列化、绘制与分割条拖拽 | 结构层已实现；终端视图挂载待阶段三 |
| `com.termux.shell.session` | `SessionHost`、`SessionDescriptor`、`SessionId` | 会话宿主契约（预留跨进程边界） | 仅契约，无实现 |
| `com.termux.shell.input` | `InputRouter`、`KeymapTable` | 输入路由契约、键位表解析 | 键位表已实现；路由待接入 |
| `com.termux.shell.render` | `FrameScheduler`、`DirtyRows` | 帧调度契约、脏行区间 | 契约与值对象已实现；调度器待阶段三 |

### 10.3 阶段一明确未实现的内容

下列内容属阶段三（动工）范围，骨架中仅保留契约或空位，阶段二不得提前实现：

1. 会话创建、伪终端读写、bootstrap 安装与 `exec`；
2. 终端视图挂载与渲染（含脏行增量、同步输出、行缓存）；
3. 键序编码（kitty keyboard 协议、`modifyOtherKeys`）与拖放；
4. 布局持久化写入磁盘与冷启动恢复（编解码器已就绪，读写位置待定）；
5. 页签栏、会话侧栏内容、状态栏与通知区域；
6. 设置页与 `termux.properties` 兼容层。

### 10.4 阶段一验证结果

本地环境：JDK 21、Android SDK（`platforms/android-36`、`build-tools/35.0.0`、`ndk/29.0.14206865`）、Gradle 9.2.1。

| 命令 | 结果 |
| --- | --- |
| `./gradlew :app_new:assembleDebug` | 通过，产物为 `app_new/build/outputs/apk/debug/` 下的调试包 |
| `./gradlew :app_new:testDebugUnitTest` | 通过，18 项单元测试（档位判定 5 项、脏行区间 5 项、键位表解析 8 项） |
| `./gradlew :app_new:lintDebug` | 通过（`abortOnError` 已启用） |

说明：上述结果为本机实测，**未**在设备或模拟器上安装运行，界面行为尚未获得真机验证。

### 10.5 阶段二（规划）的产出要求（已由 `docs/DELIVERY_PLAN.md` 满足）

1. 以 `app_new` 的包边界为单位，把第 3 节的 W0–W3、I1–I4、R1–R3 拆为可独立提交的任务，逐项给出改动文件与验收命令；
2. 明确 W0 拆分结果与 `SessionHost` 跨进程边界的一致性检查项；
3. 明确第 6 节方案 B 的进程拆分时序，以及 `targetSdk` 切换的触发条件；
4. 给出 M12–M15 的进入条件与退出条件，并登记到 plan 工具（`PLAN.md` 由该工具维护）。

## 11. 附录：参考实现对照

| 能力 | 参考实现 | 说明 |
| --- | --- | --- |
| kitty keyboard | kitty `docs/keyboard-protocol.rst` | flags 语义、`CSI u` 编码格式 |
| 同步输出 | `DECSET 2026`（kitty/wezterm/foot 均已实现） | 「begin/end synchronized update」 |
| 窗格树 | tmux `layout-string`、wezterm `Pane` | 拖拽 + 布局序列化 |
| 行级渲染缓存 | wezterm / alacritty 的 damage 上报 | 只重绘受损行 |
| 拖放语义 | Windows Terminal（拖文件插入路径）、iTerm2 | 默认复制 vs 插路径的取舍 |
| 自适应布局 | `androidx.window` + Material 3 Adaptive | compact/medium/expanded 三档 |

补充说明（阶段二结论）：终端接入采用**先进程内接入、`targetSdk` 保持 28** 的路线，理由与代价见
[`DELIVERY_PLAN.md`](DELIVERY_PLAN.md) 第 0 节与第 2 节；第 6 节的方案 B 顺延为 M16，不作为前期阻塞项。
