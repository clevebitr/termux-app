# DELIVERY_PLAN — 交付计划

> 本文件是阶段二（规划）的唯一产出，回答「谁在什么时候改哪些文件、如何验收」。
> 方案与取舍见 [`UPGRADE.md`](UPGRADE.md)，协作约束见 [`../AGENTS.md`](../AGENTS.md)。
> `PLAN.md` 由 plan 工具维护，本文件不替代它；里程碑登记时以本文件的编号为准。

---

## 0. 计划输入

| 输入 | 取值 | 来源 |
| --- | --- | --- |
| 终端接入时序 | 先进程内接入，`targetSdk` 保持 28 | 需求方于 2026-11-19 确认 |
| 新代码位置 | 仅 `app_new/` | `AGENTS.md` 第 3 节 |
| 旧模块 | 只读 | 同上 |
| 阶段顺序 | 骨架 → 规划 → 动工 | `AGENTS.md` 第 5 节 |
| D1 | 采纳 A：复制基线 | 需求方于 2026-11-19 确认 |
| D2 | 采纳 C：改用 `com.termux` 包名顶替旧应用 | 需求方于 2026-11-19 确认 |
| D3 | 采纳：应用私有主目录 + `home/.termux` 配置目录 | 本文件第 2 节；实现见 `ShellPaths` |
| D4 | 采纳：窗口级标签栏（每标签一棵窗格树） | 需求方确认；见 `app_new/DECISIONS.md` #1 |
| 当前进度 | M12-0、M12-1、M13-2a…M13-2d、M13-7、M13-8 已完成 | 本文件第 4、5 节 |
| 技术栈 | Kotlin 2.2.20 + Compose BOM 2025.10.01（壳层）；终端核心保持 Java 复制基线 | `UPGRADE.md` 第 12 节；需求方于 2026-11-20 确认 |

---

## 1. 计划原则

旧模块只读的前提下，只有以下三种合法用法。任何任务在动工前必须声明自己属于哪一种。

| 用法 | 含义 | 允许 | 禁止 |
| --- | --- | --- | --- |
| 只读依赖 | `implementation project(":terminal-view")` 等构建依赖，不修改源码 | 是 | 修改其源码、资源、构建脚本 |
| 包装层 | 在 `app_new` 内包裹旧类，通过公开 API 或输出流拦截实现新行为 | 是 | 依赖旧类的私有成员或反射改写行为 |
| 复制基线 | 把旧模块源码复制进 `app_new` 并迁移包名，形成独立基线 | 仅当决策点 D1 批准 | 复制后仍与旧模块共享文件、或同时引用复制基线与原模块 |

---

## 2. 待确认决策点

以下决策直接影响任务顺序，须在动工前逐项确认。未经确认的任务不得启动。

### D1 — VT 核心的归属（阻塞 M12-6 与全部 I1）

**问题**：脏行增量（R1）、同步输出探测（R2）、kitty 键盘协议（I1）都需要 VT 解析器内部状态，
而 `terminal-emulator` 为只读模块。仅靠包装层可以覆盖 R2（扫描输出字节流），但 R1 与 I1 无法覆盖。

| 方案 | 做法 | 代价 | 风险 |
| --- | --- | --- | --- |
| **A（推荐）** | 在 `app_new/terminal-core/` 复制 `terminal-emulator` 源码并迁移包名，形成独立基线；此后所有 VT 改动只发生在新模块 | 一次性迁移约 13 k 行；上游 VT 修复需手工挑选 | 双份维护；包名迁移遗漏导致 JNI 符号不一致 |
| B | 对 `terminal-emulator` 做最小补丁（每处改动单独提交、逐条记录） | 违反「旧代码只读」约束，需需求方书面豁免 | 旧模块重新与技术债耦合，合并上游成本回升 |
| C | 只做包装层，放弃 R1 与 I1 | 无 | 桌面级体验的两项核心能力缺失，本计划目标无法达成 |

**结论：采纳 A**（2026-11-19 确认）。复制范围为 `terminal-emulator` 与 `terminal-view` 两者的源码，
理由：`terminal-view` 直接引用 `com.termux.terminal.*`，若只复制前者，构建中将同时存在两套 VT 类型与
两个同名原生库 `libtermux`，产生类冲突与符号冲突。复制后在 `app_new/terminal-core/` 内形成独立基线，
包名迁移为 `com.termux.shell.terminal` 与 `com.termux.shell.termview`，原生库保留模块名 `libtermux`。
M12 的 R1 与 M14 的 I1 顺延至复制基线完成之后；R2 不受影响，可按包装层先行实现。

### D2 — 引导文件系统（bootstrap 与 `$PREFIX`）的归属（阻塞 M12-2）

**问题**：进程内创建会话必须调用 `exec`，而 `exec` 的目标位于 `$PREFIX`。`app_new` 的
`applicationId` 为 `com.termux.shell`，其数据目录与旧应用隔离，进程无法访问旧应用的 `$PREFIX`。

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A（本计划默认）** | M12 仅接入系统 shell（`/system/bin/sh`）验证外壳与帧管线，不安装 bootstrap；bootstrap 安装作为独立里程碑 M13.5 | 无法在 M12 验证 `apt`、`proot` 等真实负载 |
| B | 在 `app_new` 内重新实现 bootstrap 安装器（下载、校验、解压、权限修复、`termux-exec`） | 约 1500 行，且与旧模块功能重复 |
| C | `app_new` 最终改用 `com.termux` 包名顶替旧应用 | 无法与旧应用并存对比；签名与 `sharedUserId` 约束回归 |

**结论：采纳 C**（2026-11-19 确认），即 `app_new` 最终以 `com.termux` 包名顶替旧应用，直接复用既有 `$PREFIX`。

采纳 C 带来下列必须同时满足的约束，缺一不可：

| 编号 | 约束 | 说明 |
| --- | --- | --- |
| C-1 | 必须声明 `android:sharedUserId="com.termux"` | 旧应用已声明该属性；更新安装时 `sharedUserId` 不允许新增或变更，否则报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |
| C-2 | 必须与旧应用同签名 | 否则报 `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`；签名流程见 `FORK_BUILD.md` |
| C-3 | 无法与旧应用并存 | 同 `applicationId` 的安装即覆盖旧应用；「并存对比」这一收益相应取消 |
| C-4 | 覆盖安装会破坏插件功能 | `Termux:API`、`Termux:Boot`、`Termux:Tasker`、`Termux:Widget`、`Termux:Float`、`Termux:Styling` 依赖旧应用的 `RUN_COMMAND` 服务与签名级权限；在移植对应服务之前，覆盖安装会使插件失效 |
| C-5 | 开发期覆盖安装会终止正在运行的会话 | 覆盖安装等同于升级，进程重启，正在执行的命令与未保存的输出丢失 |

**M12 期间的落地方式**：`app_new` 继续使用 `applicationId=com.termux.shell` 与独立数据目录，仅使用
`/system/bin/sh` 验证外壳与帧管线；`sharedUserId`、签名与包名切换作为 M13.5 的前置任务单独实施，
并在实施前完成 `$PREFIX` 备份（见 `FORK_BUILD.md` 第 4.4 节）。这样可在不触碰日常使用中的 Termux
的前提下推进 M12 与 M13。

### D3 — 布局与配置的落盘位置

候选：应用私有 `filesDir`（简单、随卸载清理）与 `~/.termux/`（与旧应用习惯一致、便于用户编辑）。

**结论：采纳组合方案。** 用户可配置项写入应用私有主目录下的 `home/.termux/shell.properties` 与
`home/.termux/keymap.properties`；窗格与标签布局写入应用私有目录的 `layout/`（M13-3 实现）。
骨架阶段 `applicationId` 为 `com.termux.shell`，其 `home` 位于应用私有目录内；M13.5 切换到 `com.termux`
包名后同一路径即真实用户主目录，路径计算集中在 `config/ShellPaths`，切换时不需要改调用方。

文件在首次启动时按模板生成（含中文说明），删除某项即回退默认值；取值非法时忽略并在日志中列出键名。
配置文件在进程启动时同步读取，重载通过命令面板的「重新加载配置文件」触发。

---

## 3. 里程碑总览

| 里程碑 | 目标 | 关键任务 | 预计工期 | 进入条件 | 退出条件 |
| --- | --- | --- | --- | --- | --- |
| **M12** | 外壳承接真实会话，帧管线可用 | M12-1…M12-6 | 2 周 | D2 已确认 | 真机会话可交互；流式输出无半帧；单元测试与 Lint 通过 |
| **M13** | 多窗口与窗格树接入终端视图 | M13-1…M13-6、M13-2a…M13-2d | 3 周 | D1、D3、D4 已确认 | 两窗口并行；四窗格 + 标签栏；布局冷启动恢复 |
| **M13.5** | 引导文件系统与真实负载 | M13.5-1…M13.5-3 | 2 周 | D2 的 B/C 已选定 | `apt update`、`proot-distro` 可用 |
| **M14** | 桌面级输入 | M14-1…M14-5 | 2.5 周 | M13 退出 | kitty 键序正确；拖放可用；横向滚轮可用 |
| **M15** | 工程与视觉现代化 | M15-1…M15-4 | 3 周 | 与 M13 之后的任一时间点并行 | AGP 9 构建通过；三档布局在折叠屏与桌面窗口下无回归 |
| **M16** | 运行时拆分（方案 B） | M16-1…M16-3 | 独立评估 | M14 退出 | UI 进程可切换 `targetSdk` 36 且功能等价 |
| **M19** | 会话配置与 profile | M19-1…M19-4 | 1 周 | 无（模型与存储可先行） | profile 可增删改、跨窗口同步、标签绑定；按配置启动待 M14 |

M12 与 M15-1（工具链）可并行；M13.5 与 M14 可并行；M16 不得早于 M14。

---

## 4. M12 — 外壳承接真实会话

| 编号 | 目标 | 改动文件（均在 `app_new/`，另有标注者除外） | 用法 | 验收 |
| --- | --- | --- | --- | --- |
| M12-0 | 终端核心复制基线（D1=A） | 新增 `app_new/terminal-core/`：复制 `terminal-emulator` 与 `terminal-view` 源码，包名迁移为 `com.termux.shell.terminal` 与 `com.termux.shell.termview`，原生库保留 `libtermux` | 复制基线 | 迁移后 `:terminal-core:testDebugUnitTest` 中原有 24 个测试文件全部通过；`:terminal-core:assembleDebug` 产出单一 `libtermux.so` |
| M12-1 | 接入终端库 | `build.gradle` 增加 `project(":terminal-core")`；根 `settings.gradle` 增加该模块 | 只读依赖（指向复制基线） | `./gradlew :app_new:assembleDebug` 通过；APK 内含 `libtermux.so` |
| M12-2 | 进程内会话宿主 | 新增 `session/LocalSessionHost.java`、`session/SessionThread.java` | 只读依赖 | 单元测试覆盖创建、写入、关闭的幂等性；设备上出现交互式 shell 提示符 |
| M12-3 | 会话视图挂载 | 新增 `pane/SessionViewFactory.java`；修改 `pane/PaneHostView.java` 为容器，改为按叶子定位子视图 | 只读依赖 | 四窗格各显示独立会话；切换窗格时视图不重建（以 `View` 实例标识验证） |
| M12-4 | 终端视图客户端适配 | 新增 `terminal/TerminalViewClientAdapter.java`、`terminal/TerminalSessionClientAdapter.java` | 只读依赖 | 输入可回显；`Ctrl+C` 可中断前台进程；未实现项以 `TODO(M14)` 标注 |
| M12-5 | 帧合流与同步输出 | 新增 `render/ChoreographerFrameScheduler.java`、`render/SynchronizedOutputFilter.java`；`SessionObserver.onOutput` 改为经筛选器投递 | 包装层 | `yes` 持续输出时 `Choreographer` 掉帧率低于 5%/s；`printf '\e[?2026h…\e[?2026l'` 无半帧；超时 300 ms 后强制刷新 |
| M12-6 | 脏行增量（**依赖 D1**） | 若 D1 采纳 A：`terminal-core/` 内 `TerminalBuffer`、`TerminalRow`、`TerminalEmulator` 增加脏行区间 | 复制基线 | 高吞吐输出时仅重绘脏行（以绘制计数断言）；5000 行回滚拖动无卡顿 |

M12 期间不得实现多窗口、页签、kitty 键序与拖放。

> 2026-11-22 状态：M12-2…M12-4 已按「系统 shell」范围交付（`LocalSessionHost`、`TerminalViewPool`、
> 会话/视图客户端适配器、标签与窗格会话绑定）；bootstrap 与 `$PREFIX` 仍被 M13.5 阻塞，
> 发行版/SSH 配置只会打印工具缺失错误；设备侧未验证。

---

## 5. M13 — 多窗口与窗格树

| 编号 | 目标 | 改动文件 | 用法 | 验收 |
| --- | --- | --- | --- | --- |
| M13-1 | 窗口与会话组映射 | 新增 `ui/TaskSessionRegistry.java`；修改 `ui/ShellActivity.java` | — | 桌面窗口模式下两窗口各自独立会话；划掉一个窗口不影响另一窗口 |
| M13-2 | 窗格容器与标签栏骨架 | 新增 `ui/TabStripView.java`、`tabs/TabWorkspace.java`、`tabs/WorkspaceTab.java`、`tabs/TabWorkspaceCodec.java`；修改 `pane/PaneLeaf.java`、`pane/PaneLayoutCodec.java`、`pane/PaneHostView.java` | — | 窗口级标签栏：新建、切换、关闭、左右移动、长按菜单；旋转与被系统回收后标签与窗格结构恢复；标签数量与窗格数量受上限约束 |
| M13-2a | 配置文件配置层 | 新增 `config/ShellPaths.java`、`config/SettingsSpec.java`、`config/PropertiesSettingsRepository.java`；修改 `ShellApplication`、`app_new/build.gradle` | — | 首次启动生成 `home/.termux/shell.properties` 与 `keymap.properties` 模板；合法取值生效、非法取值回退并记日志；`reload-config` 立即生效 |
| M13-2b | 键位表落到外壳动作 | 新增 `input/KeymapLoader.java`、`input/ShellAction.java`、`input/ShellInputRouter.java`；修改 `input/KeymapTable.java` | — | 默认键位覆盖标签页 / 窗格 / 窗口三组动作；快捷键展示文本用于命令面板；`keymap-enabled=false` 时全部按键交还终端 |
| M13-2c | 命令面板 | 新增 `commands/ShellCommand.java`、`commands/CommandCatalog.java`、`commands/CommandFilter.java`、`ui/CommandPaletteController.java`、`layout/dialog_command_palette.xml`、`layout/item_command_row.xml` | — | 顶部浮动卡片、输入即筛选、方向键选择、回车执行、Esc 关闭；不可执行命令置灰；与快捷键、溢出菜单共用同一动作实现 |
| M13-2d | MD3 视觉基线 | 修改 `ui/LayoutHost.java`、`ui/ShellActivity.java`、`res/layout/activity_shell.xml`、`res/values/themes.xml`、`res/values*/strings.xml`；新增 `ui/ThemeColors.java`、`ui/ShellViewOptions.java`、`res/drawable/ic_*.xml`、`res/menu/*.xml` | — | 标题行 + 标签栏 + 窗格区的三段结构；标签栏可置顶或置底；日夜模式切换后自绘颜色同步更新；侧栏在中等与展开档位常驻 |

| M13-7 | 技术栈迁移（Kotlin + Compose） | 根 `build.gradle` 增加 Kotlin 与 Compose 编译器插件 classpath；`app_new/build.gradle` 应用插件并声明 Compose 依赖；`src/main/java` 与 `src/test/java` 整体迁移到 `src/main/kotlin`、`src/test/kotlin` | — | `:app_new:testDebugUnitTest` 与 `:app_new:assembleDebug` 在 CI 通过；单元测试数量不低于迁移前的 56 项；壳层界面全部由 Compose 绘制 |

| M13-8 | 分层与状态管理重构 | 新增 `ui/ShellViewModel.kt`、`ui/ShellSettings.kt`、`ui/ShellNotice.kt`、`config/ConfigSource.kt`、`config/ApplicationConfigSource.kt`；`commands/CommandCatalog` 迁移为 `ui/ShellCommandCatalog`；重写 `ShellActivity`、`ShellScreen`、`CommandPaletteView`、`PaneArea`、`TabStripBar` | — | 视图模型不依赖 `Context` 与资源；设置状态聚合为单快照；提示改为 Snackbar；新增 `ShellViewModelTest`；界面动效覆盖标签栏、标题、侧栏、窗格与命令面板 |

> 说明：M13-2 原计划的「每个窗格内多页签」按 D4 改为「窗口级标签栏，每个标签一棵窗格树」，
> 与 Windows Terminal 的语义一致。窗格内容在 M12-2…M12-4 完成前保持占位。
> M13-7 把壳层实现语言与界面框架替换为 Kotlin + Compose，终端视图仍以 `AndroidView` 承载。
> M13-8 收敛了分层与状态管理，并补齐界面动效；CI 因仓库禁用 Actions 暂未补跑。
| M13-3 | 布局落盘与恢复 | 新增 `pane/PaneLayoutStore.java`（路径见 D3） | — | 冷启动恢复分割结构与各叶子滚动位置；布局文件损坏时回退单窗格 |
| M13-4 | 档位驱动布局 | 修改 `ui/LayoutHost.java`、`ui/WindowSizeResolver.java` | — | 折叠展开、自由窗口拖拽、桌面窗口下布局切换不重建 Activity |
| M13-5 | 焦点与导航 | 新增 `pane/PaneNavigator.java` | — | 方向键与 `Alt+方向` 迁移焦点；关闭窗格后焦点按规则落到兄弟叶子 |
| M13-6 | 帧管线适配多窗格 | 修改 `render/ChoreographerFrameScheduler.java` | — | 四窗格同时输出时掉帧率仍低于 5%/s |

---

## 6. M13.5 — 引导文件系统（依赖 D2 结论）

| 编号 | 目标 | 改动文件 | 验收 |
| --- | --- | --- | --- |
| M13.5-1 | 包名与共享用户切换（C-1、C-2） | `app_new/build.gradle`、`app_new/src/main/AndroidManifest.xml` | 覆盖安装成功；`/data/data/com.termux/files/usr` 可读写可执行 |
| M13.5-2 | 既有 `$PREFIX` 兼容验证（C-3、C-5） | 新增 `runtime/PrefixProbe.java` | `bash`、`apt`、`proot-distro` 在既有前缀上可用；备份与恢复流程见 `FORK_BUILD.md` 第 4.4 节 |
| M13.5-3 | 插件服务前置（C-4） | 新增 `plugin/RunCommandService.java` 等，或明确降级说明 | `Termux:API` 至少一条命令可用；未移植项在 `FORK.md` 中列为已知限制 |

本里程碑直接采纳 D2 的 C 方案，不再实施自建 bootstrap 安装器；M13.5-3 的工作量较大，
可拆为独立里程碑，但不得晚于首次对外发布。

---

## 7. M14 — 桌面级输入

| 编号 | 目标 | 改动文件 | 用法 | 验收 |
| --- | --- | --- | --- | --- |
| M14-1 | kitty 键盘协议（**依赖 D1**） | `terminal-core/` 内新增 `KittyKeyboardState.java`，修改 `TerminalEmulator.java`、`KeyHandler.java` | 复制基线 | 设备上 `nvim` 中 `Ctrl+Shift+A` 与 `Ctrl+A` 可区分；`hx` 中组合键正常 |
| M14-2 | keymap 生效 | 新增 `input/KeymapLoader.java`，修改 `input/InputRouter` 的实现类 | — | `Ctrl+Shift+T` 新建页签；`disable-hardware-keyboard-shortcuts` 等价开关生效 |
| M14-3 | 拖放 | 新增 `input/DropHandler.java` | 包装层 | 文件管理器拖入后文件出现在 `$HOME`；拖入文本按 bracketed paste 插入 |
| M14-4 | 鼠标语义 | 新增 `input/PointerRouter.java` | 包装层 | 横向滚轮、中键粘贴、右键菜单、双击选词、三击选行可用 |
| M14-5 | 光标形状 | 修改 `terminal-core/` 内光标样式解析 | 复制基线 | `OSC 22` 与 `DECSCUSR` 均可切换光标形状 |

---

## 8. M15 — 工程与视觉现代化

| 编号 | 目标 | 改动文件 | 验收 |
| --- | --- | --- | --- |
| M15-1 | 工具链升级（仅 `app_new` 先行） | `app_new/build.gradle`：`compileSdk` 37、AGP 9、Gradle 9.6、Java 17 toolchain | `:app_new:assembleDebug` 与 `:app_new:testDebugUnitTest` 通过；根构建不受影响 |
| M15-2 | 旧模块工具链升级 | 根 `build.gradle`、`gradle.properties`（须单独提交并经确认） | CI 三套产物通过，签名摘要一致 |
| M15-3 | Material 3 自适应改造 | `ui/`、`res/values/themes.xml`、`res/layout/` | 三档布局在折叠屏与桌面窗口下无布局溢出 |
| M15-4 | 配置层收敛 | `config/` 内新增 `PropertiesSettingsRepository.java` | 属性文件与界面配置一致；不存在初始化回写默认值的问题 |

Kotlin 引入不在 M15 范围内，单独评估后再排期。

---

## 9. M16 — 运行时拆分（方案 B）

| 编号 | 目标 | 验收 |
| --- | --- | --- |
| M16-1 | 定义 `IRuntimeService`（AIDL）与进程间输出通道 | 通过 `ParcelFileDescriptor` 传输，单次输出 1 MB 不触发事务上限 |
| M16-2 | 运行时侧实现（旧应用包名，`targetSdk` 28） | 拆分后 M12–M14 的功能等价 |
| M16-3 | UI 侧切换 `targetSdk` 36 | 预测性返回、按应用设置语言、FGS 类型声明可用 |

---

## 9.1 M19 — 会话配置

| 编号 | 目标 | 改动文件 | 用法 | 验收 |
| --- | --- | --- | --- | --- |
| M19-1 | 配置模型与校验 | 新增 `session/SessionProfile.kt` | — | SHELL/DISTRO/SSH 校验；单元测试覆盖非法输入 |
| M19-2 | 编码与存储 | 新增 `config/ProfileCodec.kt`、`config/ProfileStore.kt`、`config/FileProfileStore.kt`；修改 `ShellPaths`、`ShellApplication`、`ConfigSource` | — | JSON 版本 1；原子写；`StateFlow` 跨窗口共享；文件损坏保留旧列表 |
| M19-3 | 标签绑定 | 修改 `tabs/WorkspaceTab.kt`、`tabs/TabWorkspace.kt`、`tabs/TabWorkspaceCodec.kt`、`ui/ShellViewModel.kt` | — | 标签记录 `profileId`；编码可选字段；标签标题使用配置名；关闭后新建不重名 |
| M19-4 | 设置页与命令面板 | 新增 `ui/ProfileEditorDialog.kt`；修改 `ui/SettingsScreen.kt`、`ui/ShellScreen.kt`、`res/values*/strings.xml` | — | 设置页可增删改配置；命令面板出现「新建会话：<名称>」 |

> M19 的启动部分依赖 M14 会话宿主：在 M14 完成前只记录绑定，不创建进程。

---

## 10. 验收矩阵

| 类别 | 命令或清单 | 适用任务 |
| --- | --- | --- |
| 构建 | `./gradlew :app_new:assembleDebug` | 全部 |
| 单元测试 | `./gradlew :app_new:testDebugUnitTest` | 全部 |
| 静态检查 | `./gradlew :app_new:lintDebug` | 全部 |
| 帧率 | 设备侧记录 `Choreographer` 掉帧统计，`yes` 持续输出 60 s | M12-5、M12-6、M13-6 |
| 交互 | 设备清单：输入回显、`Ctrl+C`、窗格切换、页签切换、拖放、横向滚轮 | M12-2…M12-4、M13-2、M14-2…M14-4 |
| 持久化 | 冷启动后布局与滚动位置恢复；布局文件损坏时回退 | M13-3 |
| 无 Android SDK 时的替代验证 | `javac` 编译主源与测试源（`platforms/android-36` 的 `android.jar` + Maven AAR 的 `classes.jar` + `R` 存根），`org.junit.runner.JUnitCore` 运行单元测试；资源引用与语言文件对齐用仓库外脚本检查 | M12 之后全部 |
| 外壳演示构建 | `.github/workflows/app_new_debug_build.yml`：运行 `:app_new:testDebugUnitTest` 与 `:app_new:assembleDebug`，产物上传为构建产物并发布到预发布页 `shell-demo` | M13 之后全部（按需手动触发） |
| 兼容性 | 桌面窗口模式、分屏、折叠屏展开与折叠 | M13-1、M13-4、M15-3 |

设备侧验证须逐条记录于提交说明，未执行项须标注「未验证」。

---

## 11. 风险登记（承接 `UPGRADE.md` 第 7 节）

| 编号 | 风险 | 影响任务 | 缓解 |
| --- | --- | --- | --- |
| P-1 | D1 选择复制基线后，包名迁移遗漏导致 JNI 符号不一致 | M12-6、M14-1 | 迁移后先跑通 `terminal-emulator` 既有单元测试，再接 UI |
| P-2 | 无 bootstrap 时无法验证真实负载，M12 的验收强度不足 | M12 全部 | 以 `/system/bin/sh` 与本地高频输出脚本覆盖帧管线；真实负载由 M13.5 补验 |
| P-3 | 多窗格共享 `$PREFIX` 时并发写风险 | M13、M13.5 | 界面层提示；`multi-window-session-policy` 默认 `detach` |
| P-4 | 旧模块长期只读导致与上游 0.119 系列的能力差距扩大 | 全部 | 每季度对照上游 VT 修复清单，按需 cherry-pick 至 `terminal-core` |
| P-5 | 长按菜单、状态栏、通知等既有 fork 能力在新外壳中缺失 | M13 之后 | 单独里程碑迁移，不并入 M12–M14 |

---

## 12. 变更记录

| 日期 | 变更 | 依据 |
| --- | --- | --- |
| 2026-11-19 | 建立本计划；确认先进程内接入；登记决策点 D1–D3 | 需求方确认与阶段一交付结果 |
| 2026-11-19 | D1 采纳 A（复制基线，范围含 `terminal-view`）；D2 采纳 C（改用 `com.termux` 包名），登记约束 C-1…C-5；新增任务 M12-0；重写 M13.5 | 需求方确认 |
| 2026-11-19 | M12-0 完成：`app_new/terminal-core/` 复制 64 个文件（含 23 个测试类），包名迁移为 `com.termux.shell.terminal` 与 `com.termux.shell.termview`，JNI 符号同步迁移；239 项单元测试通过，Lint 通过 | 本机实测 |
| 2026-11-19 | M12-1 完成：`app_new` 以只读依赖方式接入 `:terminal-core`；APK 含 4 个 ABI 的 `libtermux.so` | 本机实测 |
| 2026-11-19 | 确认 D3（组合方案）与 D4（窗口级标签栏）；新增任务 M13-2a…M13-2d，修订 M13-2 的验收口径；补充无 Android SDK 时的替代验证方式 | 需求方确认与本次交付 |
| 2026-11-19 | M13-2、M13-2a…M13-2d 完成：标签栏、配置层、键位表、命令面板与 MD3 视觉基线；56 项单元测试通过；Gradle 构建未执行（本机缺 Android SDK build-tools） | 本机 `javac` + `JUnitCore` 实测 |
| 2026-11-19 | 新增外壳演示构建工作流 `app_new_debug_build.yml`（需求方要求通过 CI 出包查看界面）；单独成文件，不修改既有工作流 | 需求方要求 |
| 2026-11-20 | M13-7 完成：壳层迁移到 Kotlin 2.2.20 + Compose BOM 2025.10.01，删除 Java 实现与自绘视图、XML 布局；60 项单元测试通过，CI 构建与单测通过；APK 11.7 MB | 需求方确认与 CI 实测 |
| 2026-11-22 | M13-8 完成：分层与状态管理重构（ShellViewModel、ConfigSource、设置快照、提示事件）与界面动效；94 项单元测试通过；CI 在镜像仓库 `clevebitr/termux-shell-demo` 上通过（原仓库 Actions 被禁用） | 本地 kotlinc + JUnitCore 与镜像仓库 CI 实测 |
| 2026-11-22 | 确认会话归属模型 B（1 标签 = 1 主会话 + 分屏额外会话）；M19-1…M19-4 完成：profile 模型/存储/跨窗口共享、标签绑定、设置页与命令面板入口；112 项单元测试通过 | 需求方确认与本机实测 |
| 2026-11-22 | M12-2…M12-4 首批交付：进程内会话宿主、终端视图池、会话/视图客户端适配器、标签与分屏会话绑定；系统 shell 可交互为目标；123 项单元测试通过；设备侧未验证 | 本机实测 |
| 2026-11-22 | 新增 minified release 性能验证包：release 开启 R8 与资源裁剪；演示工作流统一使用仓库公开测试密钥，debug/release 同证书可互相覆盖安装；release 包 1.65 MB，debug 包 11.9 MB | 性能对比需求与 CI 实测 |
| 2026-11-21 | 追加第 13 节：交互层（系统按键捕获、指针接管、虚拟键鼠）、自适应界面与会话注册表、设置页；登记决策点 D5–D7 与里程碑 M15–M18 | 需求方提出，可行性结论见 `UPGRADE.md` 第 13 节 |
| 2026-11-21 | `M17`、`M18` 部分完成：配置写回与两个新配置项、会话注册表、三种密度的会话列表、三档自适应界面、设置页与写回接线、标签与分割条的指针交互。未完成项：会话脱离与移动、指针与按键捕获开关（依赖 M14–M16）。本机 `assembleDebug`、74 项单元测试与 `lintDebug` 通过 | 本机实测 |
| 2026-11-21 | 修复需求方报告的四个界面问题：宽屏分隔条挤占主区域、深色模式内容色缺失、系统栏图标不跟随应用主题、缺少全屏模式；并补齐会话列表显隐入口（标题栏按钮与菜单项）。本机 `assembleDebug`、74 项单元测试与 `lintDebug` 通过 | 需求方反馈与本机实测 |
| 2026-11-21 | 窗格分隔条改为不可见热区（仅悬停显示细线），新建/设置/窗口菜单移入会话栏，新增 Ctrl+Alt+方向键的方向聚焦（含命令面板条目与默认键位），打磨标签栏指示条、会话列表信息层级、设置页排版与窗格样式。本机 `assembleDebug`、79 项单元测试与 `lintDebug` 通过 | 需求方反馈与本机实测 |


---

## 13. 交互层与自适应界面（2026-11-21 追加）

本节编号沿用外壳计划 `app_new/PLAN.md` 的里程碑序列。它细化并部分取代第 7 节 `M14` 的
`M14-3`…`M14-5` 与第 8 节 `M15` 的 `M15-3`、`M15-4`：鼠标语义、自适应布局与配置收敛在此拆成
可单独验收的任务。可行性依据见 `docs/UPGRADE.md` 第 13 节。

### 13.1 待确认决策点

#### D5 — 输入捕获的默认层级

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A（推荐）** | 默认窗口级捕获；无障碍过滤为可选开关，默认关闭 | 系统保留键（`HOME`、助理键、语音助手）在默认配置下仍由系统处理 |
| B | 首次启动引导开启无障碍服务 | 可拦截保留键，但授权成本高，且商店分发政策对该权限用途敏感 |
| C | 只做窗口级，不提供无障碍过滤 | 实现最简，但无法满足「屏蔽语音助手」的要求 |

**结论：待确认。**

#### D6 — 会话与标签的关系

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A（推荐）** | 三层模型：会话（进程与 pty）／窗格（视口）／标签（窗格树容器）；关闭标签默认结束会话，`session-detach-on-tab-close` 可改为脱离 | 需要会话注册表与「重新附着」流程，实现量最大 |
| B | 保持现状：标签即会话，删除会话侧栏 | 无法保留「关闭标签后进程仍在」的桌面习惯 |
| C | 标签即会话 + 独立会话列表，不引入窗格 | 与已实现的多窗格能力冲突，需回退 M13 的窗格树 |

**结论：待确认。**

#### D7 — 指针模式默认值与 `minSdk`

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A（推荐）** | 默认触屏直通；指针模式为手动切换；`minSdk` 保持 24，指针捕获在 API 26 及以上启用，低版本隐藏该选项 | 低版本设备缺少指针模式 |
| B | 默认指针模式，`minSdk` 提升到 26 | 放弃 API 24–25 设备，但实现与测试路径单一 |

**结论：待确认。**

### 13.2 M15 — 输入兼容层

| 编号 | 目标 | 改动文件 | 用法 | 验收 |
| --- | --- | --- | --- | --- |
| M15-1 | 捕获策略与生命周期联动 | 新增 `input/capture/KeyCapturePolicy.kt`；修改 `ui/ShellActivity.kt` | — | 单测覆盖三种策略与 `RESUMED`/`PAUSED` 迁移；后台恢复系统按键 |
| M15-2 | 窗口级按键捕获 | 新增 `input/capture/WindowKeyCapture.kt`；修改 `ui/ShellActivity.dispatchKeyEvent` | 只读依赖（键位表复用） | 屏蔽集合内的按键被消费并写入诊断日志；集合外按键照常进入终端 |
| M15-3 | 可选无障碍过滤（**依赖 D5**） | 新增 `input/capture/AccessibilityKeyFilter.kt` 与清单声明 | — | 仅前台消费；切后台后系统按键立即恢复；成对消费 down/up 有单测 |
| M15-4 | 按键诊断 | 新增 `input/capture/KeyDiagnostics.kt`；设置页展示 | — | 记录键码、来源设备与是否被消费，可导出到配置文件同目录 |

### 13.3 M16 — 虚拟键鼠与触控板

| 编号 | 目标 | 改动文件 | 验收 |
| --- | --- | --- | --- |
| M16-1 | 指针捕获桥接 | 新增 `input/pointer/PointerCaptureBridge.kt`；`ui/PaneArea` 的 `AndroidView` 挂载 | 系统光标隐藏，相对位移换算正确；`Esc` 与失焦可退出捕获 |
| M16-2 | 终端鼠标编码复用 | 新增 `input/pointer/TerminalMouseEncoder.kt`（复用 `terminal-core` 的 SGR 与 1000/1002/1003 状态） | 按下、拖动、释放、滚轮事件在 `vim` 与 `tmux` 中位置正确 |
| M16-3 | 触控板式虚拟指针 | 新增 `input/pointer/VirtualPointer.kt`、`ui/VirtualPointerOverlay.kt` | 单指拖动移动、轻点左键、双指轻点右键、双击拖动；与触屏直通可切换且互斥 |
| M16-4 | 虚拟按键行 | 新增 `input/VirtualKeyPad.kt`、`ui/KeyPadBar.kt`；修改 `ui/ShellScreen.kt` | `CTRL`/`ALT`/`SHIFT` 修饰可组合；按键直接写入焦点会话；随输入法弹出与收起调整位置 |
| M16-5 | 配置与设置联动（**依赖 D7**） | 修改 `config/SettingsSpec.kt`、新增 `ui/SettingsScreen.kt` 的输入分组 | `pointer-mode`、`virtual-keypad`、`pointer-speed` 立即生效且写入配置文件 |

### 13.4 M17 — 自适应界面与会话管理

| 编号 | 目标 | 改动文件 | 验收 |
| --- | --- | --- | --- |
| M17-1 | 三档布局 | 修改 `ui/ShellScreen.kt`、`ui/WindowWidthClass.kt` | 手机竖屏单窗格且纵向无浪费；平板展开档显示常驻侧栏与标签栏；折叠屏切换无溢出 |
| M17-2 | 桌面级细节 | 修改 `ui/TabStripBar.kt`、`ui/PaneArea.kt` | 悬停高亮、指针形状（`Modifier.pointerHoverIcon`）、分割条加宽命中区、右键菜单 |
| M17-3 | 会话注册表（**依赖 D6**） | 新增 `session/SessionRegistry.kt`、`ui/SessionList.kt`；重写 `SessionRail` | 列出全部会话与附着位置；聚焦、移动、脱离、结束可用；标签关闭语义符合配置 |
| M17-4 | 手机端会话抽屉 | 新增 `ui/SessionSheet.kt` | 抽屉只承载会话，不再与标签栏信息重复；返回键与手势可关闭 |

### 13.5 M18 — 设置页与配置一致性

| 编号 | 目标 | 改动文件 | 验收 |
| --- | --- | --- | --- |
| M18-1 | 设置页骨架 | 新增 `ui/SettingsScreen.kt`、`ui/settings/*`；修改 `ui/ShellScreen.kt` 入口 | 分节展示外观、会话、输入、终端、关于；窄屏为整页，宽屏为左右分栏 |
| M18-2 | 双向同步 | 修改 `config/PropertiesSettingsRepository.kt` | 界面修改写回文件；外部修改后「重新加载配置文件」生效；非法取值回退默认并记录键名 |
| M18-3 | 权限与状态提示 | 修改 `ui/settings/*` | 无障碍授权状态、指针捕获可用性（API 级别）如实显示，不可用时说明原因 |

### 13.6 里程碑关系

`M15`、`M16` 依赖 `app_new/PLAN.md` 的 `M14`（会话宿主与终端视图挂载）：没有真实视图与焦点，
指针捕获与按键捕获都无法验收。`M17`、`M18` 可与 `M15` 并行，但 `M18-3` 需要 `M15-3` 的授权状态接口。

