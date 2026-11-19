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
| 当前进度 | 阶段一完成，18 项单元测试通过 | `UPGRADE.md` 第 10.4 节 |
| D1 | 采纳 A：复制基线 | 需求方于 2026-11-19 确认 |
| D2 | 采纳 C：改用 `com.termux` 包名顶替旧应用 | 需求方于 2026-11-19 确认 |

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
**建议**：布局文件写入应用私有目录，用户可配置项写入 `~/.termux/shell.properties` 并在文档中说明。

---

## 3. 里程碑总览

| 里程碑 | 目标 | 关键任务 | 预计工期 | 进入条件 | 退出条件 |
| --- | --- | --- | --- | --- | --- |
| **M12** | 外壳承接真实会话，帧管线可用 | M12-1…M12-6 | 2 周 | D2 已确认 | 真机会话可交互；流式输出无半帧；单元测试与 Lint 通过 |
| **M13** | 多窗口与窗格树接入终端视图 | M13-1…M13-6 | 3 周 | D1、D3 已确认 | 两窗口并行；四窗格 + 页签；布局冷启动恢复 |
| **M13.5** | 引导文件系统与真实负载 | M13.5-1…M13.5-3 | 2 周 | D2 的 B/C 已选定 | `apt update`、`proot-distro` 可用 |
| **M14** | 桌面级输入 | M14-1…M14-5 | 2.5 周 | M13 退出 | kitty 键序正确；拖放可用；横向滚轮可用 |
| **M15** | 工程与视觉现代化 | M15-1…M15-4 | 3 周 | 与 M13 之后的任一时间点并行 | AGP 9 构建通过；三档布局在折叠屏与桌面窗口下无回归 |
| **M16** | 运行时拆分（方案 B） | M16-1…M16-3 | 独立评估 | M14 退出 | UI 进程可切换 `targetSdk` 36 且功能等价 |

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

---

## 5. M13 — 多窗口与窗格树

| 编号 | 目标 | 改动文件 | 用法 | 验收 |
| --- | --- | --- | --- | --- |
| M13-1 | 窗口与会话组映射 | 新增 `ui/TaskSessionRegistry.java`；修改 `ui/ShellActivity.java` | — | 桌面窗口模式下两窗口各自独立会话；划掉一个窗口不影响另一窗口 |
| M13-2 | 窗格容器与页签栏 | 新增 `pane/PaneContainerView.java`、`ui/TabStripView.java` | — | 每叶子支持多页签；页签可切换、关闭、重排 |
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

## 10. 验收矩阵

| 类别 | 命令或清单 | 适用任务 |
| --- | --- | --- |
| 构建 | `./gradlew :app_new:assembleDebug` | 全部 |
| 单元测试 | `./gradlew :app_new:testDebugUnitTest` | 全部 |
| 静态检查 | `./gradlew :app_new:lintDebug` | 全部 |
| 帧率 | 设备侧记录 `Choreographer` 掉帧统计，`yes` 持续输出 60 s | M12-5、M12-6、M13-6 |
| 交互 | 设备清单：输入回显、`Ctrl+C`、窗格切换、页签切换、拖放、横向滚轮 | M12-2…M12-4、M13-2、M14-2…M14-4 |
| 持久化 | 冷启动后布局与滚动位置恢复；布局文件损坏时回退 | M13-3 |
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
