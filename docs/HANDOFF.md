# HANDOFF — 交接说明（2026-11-24）

> 本文件是会话交接点。新会话从这里开始，不必重读历史对话。
> 当前目标：在真实应用 `app/` 内完成「代码与架构规范化 + 扩展化」，再继续功能与界面打磨。
> `app_new/` 已冻结为原型，不作为交付对象（见根 `DECISIONS.md` \#1）。

---

## 1. 一句话状态

`app/` 的扩展化已完成主体：8 个功能扩展 + 1 个不可禁用的核心扩展、6 类贡献点、命令面板与菜单收敛、
三个控制器（抽屉/搜索/分屏）与通知体系全部迁出 `TermuxActivity`。**UI 层第一批统一已完成**：形状令牌、
共用对话框构建器、应用级对话框改 Material 3、抽屉/状态栏/命令面板/设置搜索的观感修正。
第二批交互与系统界面打磨也已提交：扩展可用性门禁（分屏/搜索/抽屉）、抽屉胶囊强调条、
全屏刘海适配；真机反馈的额外键闪烁已回退修复，命令面板改为跟随终端配色方案。
第三批「面板接管终端动作」已提交：旧菜单对话框删除，右键可配置，命令快捷键可改绑，
并完成 `docs/APP_ARCHITECTURE_REVIEW.md` §9 的分层复盘。目前仍处在真机验证与结构收尾阶段。

---

## 2. 基线与仓库状态

| 项 | 值 |
| --- | --- |
| 交付基线 | `app/`（`com.termux`） |
| 只读模块 | `terminal-view/`、`terminal-emulator/`、`termux-shared/`（改动需书面豁免） |
| 原型（冻结） | `app_new/`（Kotlin/Compose 外壳，仅作参考） |
| 工作分支 | `refactor/app-extensions`（尚未合并到 `master`；UI 层优化追加 9 个提交） |
| PR | #2 `fix: 修正迁移后资源文件中的失效类名`，OPEN |
| CI | Validation / testing(lint+单测) / build(apt-android-5) / Fork Build 通过；`build (apt-android-7)` 一次因 **Maven 依赖瞬时 404** 失败（`javawriter`/`jsr305`），已重跑 |
| 最新安装包 | `termux-app_fork-20261123-0521-testkey_arm64-v8a.apk`（UI 层改动尚未出包） |
| 未提交 | `app_new/PLAN.md`（历史改动）、`app/PLAN.md`（plan 工具按 cwd 生成的空文件，需求方已明确不需要） |
| UI 层优化提交 | `1f0d811` `c10a35c` `c43fe8b` `a2345f5` `e39607c` `70010a1` `3db4815` `12324f6` `6d1ee61` |
| 交互与系统界面提交 | `c917360` `32d7c52` `5b85fb5` `8db2e1b` `0a45190` `91f9f0b` |
| 真机反馈修复提交 | `55b3012`（闪烁）`f817e06`（面板配色）`3877f33`（抽屉间隙） |
| 面板接管提交 | `71708e0`（搜索框）`4d39d05`（通知胶囊）`7c0ed84`（接管/快捷键/右键）`272248d`（分层修正） |
| 键盘兼容提交 | `a1b589e`（快捷键匹配加固 + `keyboard-compat-log`）`8a1fc86`（只读模块钩子）`a5cba39`（`ime-input-type`） |

新会话的第一条命令：

```bash
git checkout refactor/app-extensions
git status --short          # 只应有 app_new/PLAN.md 与 app/PLAN.md
read app/todo.md            # 步骤级进度与下一步
read docs/API_INDEX.md      # 已有 API，新增前必须查
```

---

## 3. 架构现状（`app/src/main/java/com/termux/app/`）

### 3.1 扩展框架 `ext/`

```
ext/api/      Extension、ExtensionContext、ExtensionSettingsPage、ExtensionViewSlot、ExtensionLayoutHost
ext/host/     ExtensionSpec、ExtensionState、ExtensionStatus、ExtensionGraph（纯 Java）、
              ExtensionHost（唯一状态源 + find/findRunning 类型化查询 + slotOwner）、
              ExtensionPreferences（enabled.<id> 读写、设置项可见性、空分类收起）、
              ExtensionManager（生命周期 onEnable/onStart/onStop/onReload/onDisable，异常隔离）
ext/ForkExtensions  本构建的扩展登记表
```

贡献点共 6 类：`preferenceKeys()`、`settingsPages()`、`slots()`、`onStart()/onStop()/onReload()`、
`ExtensionContext.requestExtensionLayout()`、`ExtensionHost.find/findRunning`。

**禁用语义（需求方确认）**：入口不可达 + 运行时闸门；已安装到 `$PREFIX/bin` 的命令保留不删。

### 3.2 已迁移的扩展（9 个）

| 扩展 | 内容 | 宿主接口 |
| --- | --- | --- |
| `statusbar` | 状态栏 6 文件 2413 行 + 设置页 | `getConfig()`、`requestExtensionLayout()` |
| `menu` | 终端菜单 4 文件 590 行 + 设置页 + 2 个偏好控件 | `showMenu()`、`getConfig()` |
| `notifications` | 13 文件 2704 行 + 设置页 | 事件转发 + `getManager()` |
| `volumekeys` | 音量键动作派发 + 设置页 | `perform(context, host, action, up)` |
| `search` | 搜索控制器 449 行 | 显示/隐藏/刷新 |
| `drawer` | 抽屉控制器、会话列表适配器、配置、把手、颜色工具、会话详情 + 4 个测试 | `openDrawer/closeDrawer/updateColors/notifySessionsChanged/onSessionsAvailable` |
| `split` | 分屏控制器 214 行（改为只依赖 `Context`） | `setSplitScreen()` 编排仍在宿主 |
| `backup` | 备份恢复 + 设置页 | 无 |
| `builtin.vt` | **不可禁用的核心扩展**：OSC 52、悬停、滚动保持、4 个内边距收拢为独立页 | 无 |

### 3.3 命令与菜单 `commands/`

| 类 | 职责 |
| --- | --- |
| `ShellCommand` | 面板条目（id、标题、分组、快捷键、可用性、执行闭包） |
| `ShellCommandCatalog` | 由 `TerminalMenuItems` 规则生成命令；`SHORTCUT_*` 常量与视图客户端一致 |
| `ShellCommandPalette` | **跟随终端配色方案的自绘面板**（`TerminalDialogBuilder` + 程序化视图），面板内可用快捷键触发命令 |
| `ShellCommandShortcut` | `Ctrl+Alt+F` → 修饰键 + 主键，`matches`/`normalize`，无法解析永不匹配 |
| `ShellCommandHost` | 窗口级动作唯一入口（面板、抽屉按钮、会话新建共用） |
| `TerminalActionRunner` | 22 个 `TerminalMenuAction` → 宿主方法的映射表，`runTerminalActionRunnerTest` 3 项 |

### 3.4 规模

| 指标 | 会话初 | 现在 |
| --- | --- | --- |
| `TermuxActivity` | 2523 行 | **2099 行** |
| `ext/` | 14 文件 1130 行 | **63 文件 10613 行** |
| `terminal/` | 23 文件 5356 行 | 6 文件 |
| 单元测试 | 159 项 / 32 类 | **180 项 / 36 类** |

---

## 4. 关键踩坑（新会话必读）

1. **资源文件里的类名不会被编译器检查**。包迁移后忘记改 `res/**/*.xml` 的自定义控件标签或 `app:fragment`，
   编译与单测全绿，真机 inflate 时才 `ClassNotFoundException`。已补 `ResourceClassNameTest`；
   本机 `:app:lintDebug` 跑不起来（NDK 缺 `libc++_shared`），**只有 CI 的 `testing` job 会跑 lint**。
2. **迁移后测试必须加 `--rerun-tasks`**。增量执行时会漏跑迁移过的测试类（曾出现 121 vs 169 的假数字）。
3. **XML 中的类名 / 新字符串的中文翻译**都会由 lint 报错：`MissingClass`、`MissingTranslation`。
   新增用户可见字符串必须同时补 `values-zh/strings.xml`（`translatable="false"` 的除外）。
4. **Drawable 实例不能在多行间共享**：状态保存在实例上，共享会让一行的 activated/ripple 画到所有行。
   会话行背景现在每行 `getConstantState().newDrawable().mutate()`。
5. **扩展启动顺序**：`ExtensionManager.start()` 先登记 running 再调用 `onEnable`，否则扩展在 `onEnable`
   期间查询宿主会被判为「未运行」（状态栏内边距首帧算错）。
6. **配置重载只有一个入口**：`reloadProperties()` 里调用 `mExtensionManager.onReload()`；
   不要在别处再加按特性命名的重载钩子。
7. **设置搜索只索引 XML 页面**：扩展通过 `settingsPages()` 声明后自动进入索引；核心页在
   `SettingsSearchIndex.CORE_PAGES`。
8. **本机构建限制**：`:app:assembleDebug` / `:app:lintDebug` 因 NDK `libc++_shared` 失败；
   可用的是 `:app:compileDebugJavaWithJavac`、`:app:processDebugResources`、
   `:app:testDebugUnitTest --rerun-tasks`。完整构建与 lint 以 CI 为准。
9. **本机测试限制**：`FileReceiverActivityTest > testIsSharedTextAnUrl` 在 JDK 21 + Robolectric 4.10 下失败，
   CI（JDK 17）不复现，属既有问题。
10. **CI 偶发失败**：`build (apt-android-*)` 曾因 Maven 仓库瞬时 404 失败，用 `gh run rerun <id> --failed` 重跑。
11. **`shapeAppearanceOverlay` 改不动对话框圆角**：Material 3 对话框的形状来自 `alertDialogStyle`
    （`MaterialAlertDialog.Material3`）的 `shapeAppearance`，主题里的 `shapeAppearanceOverlay` 只被
    `MaterialCardView` 这类自身读该属性的控件使用。要改对话框圆角必须覆盖 `alertDialogStyle`，
    见 `ThemeOverlay.Termux.Dialog` 与 `TermuxAlertDialogStyle`。
12. **形状令牌有两处来源**：`dimens.xml`（运行时）与 `TerminalDialogStyles` 的 Java 常量（无 `Context` 时的
    配置默认值），`ShapeTokenTest` 守卫两者一致；只改一处会让终端菜单与 MD3 对话框圆角漂移。

---

## 5. 真机试机结论（已验证）

| 问题 | 状态 | 记录 |
| --- | --- | --- |
| 启动即崩溃（`DrawerHandleView` 类名失效） | 已修复 + 守卫测试 | `app/DECISIONS.md` #16 |
| 抽屉多个会话行一起高亮、长按多项一起亮 | 已修复（共享 Drawable） | #18 |
| 抽屉只有 3dp 侧条正常、切换会话后不刷新 | 已修复（`setActivated` + 主动刷新） | #17 #18 |
| 重命名对话框黑字 | 已修复（`TerminalTextInputDialog` 按配色方案取色） | #18 |
| 部分配置需重启 App 才生效 | 已修复（重载入口统一 + 启动顺序） | #17 |

---

## 6. 未验证（下次试机重点）

- 9 个扩展的启用/禁用是否都即时生效、禁用后是否无残留视图与定时器；
- 抽屉选中行底色是否只作用于当前会话、长按是否只亮一行；
- UI 层第一批的观感：终端菜单/命名对话框/通知历史的圆角与标题层级、抽屉会话行圆角涟漪与强调条、
  状态栏分段间距与按下态、命令面板在输入法弹出后的列表高度、设置搜索与色板、有序列表对话框；
- 系统界面：额外键闪烁是否消失（键盘弹起/收起、切换输入法、`fullscreen` 开关各一次）、
  手势小横条留白、全屏刘海是否真正填满、`fullscreen=false` 时状态栏与挖孔是否避让；
- 命令面板在深色/浅色与彩色强调色配色下的可读性、chip 与行按下态、抽屉首末行间隙；
- 面板接管：浮条「更多」、菜单键、右键三种模式（`auto`/`forward`/`palette`）是否都符合预期；
- 快捷键：`command-shortcuts` 改绑后面板标签与按键是否一致、额外键宏按键是否被识别、取消快捷键是否真的不再拦截；
- 输入法兼容：`ime-input-type=hardware` 时物理按键是否全部直达终端（lazyvim 插入模式 Tab、Ctrl/Alt 组合键、
  node TUI 输入顺序），软键盘中文提交是否仍可用；`text` 时英文 Gboard + 硬件键盘是否出现悬浮栏；
  `keyboard-compat-log=true` 的日志能否区分 Activity / 视图 / IME 三条路径；
- 扩展禁用门禁：分屏/搜索/抽屉动作从终端菜单与命令面板消失；
- 命令面板内快捷键（`Ctrl+Alt+F/K/C`）在真机上的触发；
- 分屏排列/拖拽/活动窗格边框/状态恢复；搜索条；右键转发在 `vim`/`tmux` 与分屏下的坐标；
- OCR/设备侧一切 UI 行为（本机无 `adb`）。

---

## 7. 待决策（会阻塞下一步）

1. ~~**风格边界**~~（2026-11-24 已决）：全部统一为 Material 3 圆角，终端内对话框也使用圆角；
   1dp 强调色描边保留，可用 `terminal-menu-border-width=0` 关闭。
2. **MD3 表面色与配色方案的深度映射**：目前只做到「按配色方案明暗切换 DayNight 变体」；抽屉、对话框、
   状态栏等自绘表面直接用配色方案颜色。若要 MD3 表面色也跟随 `colors.properties`，需要设计运行期
   theme overlay 方案（`applyStyle` 只能引用静态资源）。
3. **OSC 133 / 焦点上报 1004 开关**：目前没有对应属性，需修改只读模块才能提供，属需求方豁免范围。
4. **命令面板配色来源**：面板已改为跟随当前会话的配色方案（`f817e06`），若希望它跟随应用主题而非终端配色，
   需要恢复 `MaterialAlertDialogBuilder` 版本（`DECISIONS.md` #22 记录了原因与差异）。
5. **扩展禁用后的额外键**：`extra-keys` 属性里的 `SEARCH`/`DRAWER`/`SPLIT_*` 等特殊键在扩展禁用后仍显示但无动作，
   是否随扩展可用性过滤需需求方决定（涉及改写用户自定义布局）。
6. ~~`ime-input-type` 与只读模块钩子~~（已决）：需求方已书面豁免，`terminal-view` 新增
   `getInputType`/`getImeOptions` 钩子，app 侧提供 `ime-input-type`（默认 `auto`），见 `app/DECISIONS.md` #24。
7. **设置页顶栏配色**：设置/帮助/报告页的 Toolbar 取自 `?attr/colorPrimaryDark`（Material 3 下等于 `colorPrimary`），
   是否改为 `colorSurface` 需先确定标题与图标的取色。

---

## 8. 操作约定

- **提交**：`type: 一句话中文`（动词开头、无句号），一个提交一件事；`app/` 改动逐项单独提交。
- **进度**：每完成一项在 `app/todo.md` 勾选并补提交号，与代码改动分开提交。
- **新增代码前**：先查 `docs/API_INDEX.md`，已登记的能力一律复用，不得另写同义实现。
- **迁移包时**：`git mv` 后必须同时更新 `res/**/*.xml` 中的类名（`ResourceClassNameTest` 会兜住）。

### 验证命令（本机）

```bash
./gradlew :app:compileDebugJavaWithJavac
./gradlew :app:processDebugResources
./gradlew :app:testDebugUnitTest --rerun-tasks   # 期望：175 项，仅 FileReceiver 1 项既有失败
```

### CI 与出包

```bash
git push
gh pr checks 2                                     # 期望：Validation/testing/build x2 pass
gh workflow run fork_build.yml --ref refactor/app-extensions \
  -f build_mode=testkey -f build_plugins=false      # 约 5 分钟
gh release download fork-latest -p '*arm64-v8a.apk' -D ~/storage/downloads
```

`build (apt-android-*)` 若因依赖 404 失败：`gh run rerun <run-id> --failed`。

---

## 9. 文档索引

| 文档 | 用途 |
| --- | --- |
| `app/todo.md` | `app/` 代码进度与近期计划（步骤级，先读这个） |
| `docs/API_INDEX.md` | 已有 API 索引与「不要重复实现」清单 |
| `app/DECISIONS.md` | `app/` 的决策与踩坑（#1–#19，按时间追加） |
| `docs/EXTENSION_API.md` | 扩展化方案、实施记录（§9.1 M-E1）、迁移评估（§10） |
| `docs/APP_ARCHITECTURE_REVIEW.md` | `app/` 架构现状与发现（早期评估） |
| `FORK.md` §33/§34 | 命令面板、右键转发、扩展管理与禁用语义的用户文档 |
| `docs/KEYBOARD_COMPAT.md` | 硬件键盘与 IME 兼容层设计（含待批准的只读模块钩子） |
| `docs/UI_REVIEW.md`、`docs/ARCHITECTURE_REVIEW.md`、`docs/UPGRADE.md` | 面向 `app_new` 原型，不作 `app/` 依据 |
