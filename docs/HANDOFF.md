# HANDOFF — 交接说明（2026-11-23）

> 本文件是会话交接点。新会话从这里开始，不必重读历史对话。
> 当前目标：在真实应用 `app/` 内完成「代码与架构规范化 + 扩展化」，再继续功能开发。
> `app_new/` 已冻结为原型，不作为交付对象（见根 `DECISIONS.md` 与 `app/DECISIONS.md`）。

---

## 1. 一句话状态

`app/` 的扩展 API 骨架、第一个扩展（备份）、终端菜单规则下沉、命令面板（含四个入口）与终端右键转发均已完成，
并在 PR #1 上通过 CI（单元测试 + Lint + 双 ABI 构建 + 签名 APK）；扩展禁用语义已在 M-E1 改为「入口不可达 +
运行时闸门」，菜单收敛已完成（抽屉按钮与面板共用 `ShellCommandHost`）；**X1 控制器拆分与其余扩展迁移尚未开始**。

---

## 2. 基线与仓库状态

| 项 | 值 |
| --- | --- |
| 交付基线 | `app/`（`com.termux`，83 个 Java 文件 18451 行） |
| 只读模块 | `terminal-view/`、`terminal-emulator/`、`termux-shared/`（改动需另行书面豁免） |
| 原型（冻结） | `app_new/`（Kotlin/Compose 外壳，27 个提交，仅作参考） |
| 工作分支 | `refactor/app-extensions`（已推送，PR #1：`refactor: app 扩展 API 重构与命令面板`） |
| PR 状态 | `OPEN` / `MERGEABLE`；Unit tests、Build（apt-android-5/7）、Fork Build（testkey）、Wrapper 校验全部 success |
| 未提交 | `app_new/PLAN.md`（早于本会话的改动）；`app/PLAN.md`（`plan init` 按当前工作目录生成的空文件，需求方已明确不需要，未跟踪） |
| 进度来源 | `app/todo.md`（`app/` 的代码进度与近期计划） |

新会话的第一条命令：

```bash
git checkout refactor/app-extensions
git status --short          # 只应有 app_new/PLAN.md 与 app/PLAN.md
read app/todo.md            # 进度与下一步在这里
```

---

## 3. 已完成内容（按层次）

### 3.1 治理与评估

| 文件 | 内容 |
| --- | --- |
| `AGENTS.md` §3 | `app/` 可修改（2026-11-23 豁免）；交付目标为 `app/`；`app_new/` 降级原型 |
| `DECISIONS.md` \#1 | 基线纠正的来龙去脉（早期 27 个提交误落在 `app_new/`） |
| `app/DECISIONS.md` \#1–\#3 | 引用决策；扩展 API 方向；备份扩展与设置项可见性 |
| `docs/APP_ARCHITECTURE_REVIEW.md` | `app/` 架构评估（综合 2.8/5、A-1…A-10 发现、扩展矩阵） |
| `docs/EXTENSION_API.md` | 扩展化可行性与 API 设计；X0/X2 实施记录 |
| `docs/UI_REVIEW.md`、`docs/ARCHITECTURE_REVIEW.md` | **仅适用于 `app_new/` 原型**，不要作为 `app/` 的依据 |

### 3.2 扩展框架（`app/src/main/java/com/termux/app/ext/`）

```
ext/api/       Extension、ExtensionContext、ExtensionSettingsPage
ext/host/      ExtensionSpec、ExtensionState、ExtensionStatus、
               ExtensionGraph（依赖拓扑/缺依赖/依赖环/级联；纯 Java 可测）、
               ExtensionHost（进程级唯一状态源：statuses/stateOf/isRunning/disabledIds/setEnabled）、
               ExtensionPreferences（enabled.<id> 读写、设置项可见性）、
               ExtensionManager（启动/停止、异常隔离、Activity 生命周期内持有）
ext/ForkExtensions  本构建的扩展登记表（当前登记 backup）
ext/backup/   BackupExtension、TermuxBackupUtils、BackupPreferencesFragment（含运行时闸门）
```

- 状态存储：`SharedPreferences("termux-extensions")`，键 `enabled.<id>`；
- 状态读取统一走 `ExtensionHost`，宿主、扩展设置页、设置搜索与页面闸门共用同一判断；
- 设置页：`fragments/settings/termux/ExtensionsPreferencesFragment`（程序化构建），入口在 `termux_preferences.xml` 的「扩展」分类；
- 接线：`TermuxActivity.setupExtensions()`（`onCreate` 末尾）、`onResume` 重新应用、`onDestroy` 释放；
- 贡献点：`Extension.preferenceKeys()`（根设置页条目可见性）、`Extension.settingsPages()`（搜索索引页面表与页面归属）；
- 禁用语义（需求方确认 A）：入口不可达 + 运行时闸门，已安装到 `$PREFIX/bin` 的脚本保留不删。

### 3.3 命令面板（`app/src/main/java/com/termux/app/commands/`）

| 类 | 职责 |
| --- | --- |
| `ShellCommand` | 标题、分组、快捷键、可用性、执行闭包 |
| `ShellCommandCatalog` | 复用 `TerminalMenuItems` 规则生成命令；执行仍走 `TermuxActivity.onTerminalMenuActionSelected` |
| `ShellCommandPalette` | 可搜索对话框；`filter(...)` 为纯函数（4 项测试） |

入口（`FORK.md` §33 有用户文档）：

| 入口 | 位置 |
| --- | --- |
| `Ctrl+Shift+P` | `TermuxActivity.dispatchKeyEvent` |
| 会话抽屉按钮 | `activity_termux.xml` 的 `palette_button` + `setSettingsButtonView()` |
| 终端菜单项 `command-palette` | `TerminalMenuAction.COMMAND_PALETTE`；**默认菜单列表在只读模块内，需用户显式加入 `terminal-menu-items`** |
| 虚拟键 `F12` | `TermuxTerminalViewClient.onKeyDown`（无修饰时拦截） |

### 3.4 终端右键转发

- `app/terminal/TermuxTerminalFrame extends FrameLayout` 包装 `TerminalView`（该视图在只读模块中是 `final`，不能继承）；
- `dispatchTouchEvent` 中检测 `SOURCE_MOUSE + BUTTON_SECONDARY`：`mEmulator.isMouseTrackingActive()` 为真时用
  `getColumnAndRow()` + `sendMouseEvent(2, col, row, pressed)` 转发并消费；否则交回视图弹 `TerminalMenuDialog`；
- 布局 `activity_termux.xml`：`TerminalFrame` 包裹 `TerminalView`（id 不变），分屏布局参数仍作用在 pane 容器上。

### 3.5 规则下沉

- `app/terminal/menu/TerminalMenuItems`：`State` 快照 + `isVisible`/`isEnabled`/`build`，替代 `TermuxActivity` 里的
  `switch` 过滤；8 项 JVM 测试。命令面板与终端菜单共用同一份规则。

---

## 4. 关键踩坑与约束（新会话必读）

1. **只读模块不能再改**：终端菜单默认项（`DEFAULT_IVALUE_TERMINAL_MENU_ITEMS`）与 `TerminalView` 都在只读模块内，
   所以「面板菜单项默认可见」和「继承 `TerminalView`」都不可行，分别改为「属性显式加入」与「Frame 包装层」。
2. **VT 级功能无法拆包**：OSC 52/133、焦点 1004、悬停、滚动保持等实现在只读模块内部，只能做 `builtin.vt` 属性开关。
3. **本机构建限制**：`:app:assembleDebug` 在本机（Termux）失败于 NDK `unable to find library -lc++_shared`；
   可用的本机验证命令是 `:app:compileDebugJavaWithJavac`、`:app:testDebugUnitTest`；完整构建以 CI 为准。
4. **本机测试限制**：`FileReceiverActivityTest > testIsSharedTextAnUrl` 在 JDK 21 下因 Robolectric 4.10
   `ShadowActivityThread.reset` 失败；CI（JDK 17）不复现。
5. **本机仓库访问**：`repo.maven.apache.org` 返回 403，已在仓库外 `~/.gradle/init.d/mirror.gradle` 配置
   阿里云镜像与 Robolectric 下载源，不进入仓库。
6. **设置搜索索引只读 XML 页面**：扩展设置页是程序化构建，不在搜索结果中；新加 XML 页面需在
   `Extension.settingsPages()` 声明归属（自动进入索引并参与禁用过滤），核心页则加入
   `SettingsSearchIndex.CORE_PAGES`。
7. **守卫测试**：`PreferenceResetSourceTest` 校验每个设置页安装长按复位监听，页面清单是「类名 → 路径」映射，
   移动页面时要同步更新；`ExtensionRegistrySourceTest` 校验扩展声明的 key 存在于 `termux_preferences.xml`、
   声明的页面源码调用 `isRunning(`、页面声明不重复。

---

## 5. 未完成事项（按建议顺序）

扩展闸门层（M-E1）已完成：唯一状态源 `ExtensionHost`、设置页贡献点 `Extension.settingsPages()`、搜索与导航
过滤、页面运行时闸门、空分类收起与三项守卫测试，记录见 `app/DECISIONS.md` #4–#5。
菜单收敛已完成：抽屉按钮与会话新建按钮改走 `ShellCommandHost`，面板新增 `new-named-session`，并修正了
「切换键盘」命令的递归调用，记录见 `app/DECISIONS.md` #6。以下顺序相应前移一格。

| 序 | 任务 | 要点 | 验收 |
| --- | --- | --- | --- |
| 1 | X1 控制器 | `TerminalMenuAction` 分发改为「命令目录 + 动作映射」，与面板共用；再抽 `DrawerController` | `TermuxActivity` 方法数下降，动作实现唯一 |
| 2 | `ext.statusbar` | 6 文件 1961 行；需新增「视图装饰器」贡献点 + 页面闸门 | 可启用/禁用；禁用后无残留视图与定时器 |
| 3 | `ext.notifications` | 13 文件 2704 行，依赖事件源与状态栏（可选） | 禁用后卡片、后台通知、历史全部停止 |
| 4 | 其他扩展 | `ext.menu`/`ext.split`/`ext.drawer`/`ext.search`/`ext.volumekeys` | 逐项可禁用 |
| 5 | `builtin.vt` 开关页 | 仅属性开关（OSC52/133、1004、悬停、滚动保持、内边距） | 设置页明确标注「核心能力，仅可关闭行为」 |
| 6 | 设备验证 | 无 `adb`，需出包后按 `FORK.md` §33 与验收矩阵逐项确认 | 记录到 PR 或 `FORK.md` |

**设备验证重点**（未验证项）：禁用扩展后设置搜索不再出现其条目；禁用后直接进入功能页只显示停用说明；
命令面板四个入口；虚拟键 `F12` 在 `extra-keys` 中的配置；右键在 `vim`/`tmux` 中的表现与 shell 中的菜单回退；
分屏下右键坐标换算；抽屉按钮着色与暗色主题；扩展设置页开关后返回终端的即时生效。

---

## 6. 新会话的操作约定

- **提交**：`type: 一句话中文`（动词开头、无句号），一个提交一件事；`app/` 改动逐项单独提交。
- **进度**：每完成一项即在 `app/todo.md` 勾选并补提交号，与代码改动分开提交；计划变化时同步本文件第 5 节。
- **迁移一个功能为扩展**的标准步骤：
  1. 新建 `app/ext/<name>/`，实现 `Extension`（`id`、标题、摘要、`preferenceKeys`、`onEnable`/`onDisable`）；
  2. 迁移实现类到该包，更新 `termux_preferences.xml` 的 `app:fragment` 与 `SettingsSearchIndex`；
  3. 在 `ForkExtensions.installed()` 登记；
  4. 补 `ForkExtensionsTest` 类断言（唯一性/依赖/设置键）；
  5. 若新增贡献点，先加接口再实现，不要为空占位。
- **验证命令**（本机）：

```bash
./gradlew :app:compileDebugJavaWithJavac
./gradlew :app:testDebugUnitTest          # 期望：159 项，仅 FileReceiver 1 项失败
```

- **CI 验证**（完整构建，推荐每个增量一次）：

```bash
git push -u origin <branch>
gh pr create --base master --head <branch> --fill     # 触发 Unit tests + Build
gh workflow run fork_build.yml --ref <branch> -f build_mode=testkey
gh run list --branch <branch> --limit 6
```

---

## 7. 文档索引

| 文档 | 用途 | 是否适用于 `app/` |
| --- | --- | --- |
| `docs/APP_ARCHITECTURE_REVIEW.md` | `app/` 架构现状与发现 | 是 |
| `docs/EXTENSION_API.md` | 扩展化方案与实施记录 | 是 |
| `app/DECISIONS.md` | `app/` 模块决策（plan 工具维护） | 是 |
| `app/todo.md` | `app/` 模块的代码进度与近期计划（跨会话续接的入口） | 是 |
| `FORK.md` §33 | 命令面板与右键转发的用户文档 | 是 |
| `FORK.md` §34 | 扩展管理与禁用语义的用户文档 | 是 |
| `docs/DELIVERY_PLAN.md` | 旧里程碑计划（多为 `app_new` 时期） | 部分 |
| `docs/UI_REVIEW.md`、`docs/ARCHITECTURE_REVIEW.md`、`docs/UPGRADE.md` | 主体面向 `app_new` 原型 | 否（仅参考设计结论） |

---

## 8. 未验证声明

- 本交接文档只记录已完成与待办，不代表功能已在设备上验证；
- CI 通过的是单元测试、Lint、双 ABI 构建与签名 APK 产出，界面手感与交互正确性仍需真机确认；
- 本机 `assembleDebug` 未通过（NDK `libc++_shared` 缺失），该限制与本次改动无关。
