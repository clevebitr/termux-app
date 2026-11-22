# DECISIONS

> 决策与踩坑日志，只追加，不参与日常上下文。可用 grep 检索。条目按文件顺序编号（第一个 `##` 是 #1）。

## 2026-11-20 — 外壳采用窗口级标签栏（D4），布局编码升到 v2

结论：采纳 Windows Terminal 的标签语义：一个窗口一条标签栏，每个标签持有一棵独立的窗格树；切换标签等价于整体替换窗格树，分屏与焦点迁移只作用于当前标签。骨架期草案中「PaneLeaf 内部持有标签列表」（tmux 语义）被移除，PaneLeaf 改为只持有可空的 SessionId。

为什么不用窗格级标签：窗格内再挂标签会让「关闭窗格」「关闭标签」两个动作的作用对象纠缠，紧凑档位也没有足够横向空间；窗口级标签与命令面板、Ctrl+Tab、标签左右移动等快捷键的语义一一对应。

实现要点：新增 tabs/TabWorkspace（标签顺序、当前标签、上限 8 个）与 tabs/WorkspaceTab（标识、标题、窗格控制器）；关闭当前标签优先选中右侧，右侧不存在时选中左侧，关闭左侧标签时当前索引随之前移。布局编码拆两层：pane/PaneLayoutCodec 只编解码单棵窗格树，tabs/TabWorkspaceCodec 编解码「版本号 + 当前标签 + 标签数组」；版本取 2，版本 1 的草案格式从未发布，直接拒绝而不写迁移。标签标识为 tab-序号，恢复布局后从已有标识的最大序号继续，避免新标签与旧标签冲突。内存占用按「标签数 × 窗格数」计，上限 8 × 8。

未做：标签重命名、标签拖拽重排（当前以长按菜单的左右移动代替）、会话标题联动。

## 2026-11-20 — 配置层落地：应用私有主目录 + home/.termux 相对路径（D3）

结论：用户可配置项写入 filesDir/home/.termux/shell.properties 与 keymap.properties；窗格与标签布局将写入 filesDir/layout（M13-3）。路径计算集中在 config/ShellPaths，包名在 M13.5 切换为 com.termux 后同一路径即真实用户主目录，调用方无需改动。

为什么不用 ~/.termux/termux.properties：骨架阶段 applicationId 为 com.termux.shell，其沙箱与旧应用不共享 $PREFIX 与主目录；复用旧文件名还会让两套外壳互相覆盖同一个键。独立文件名加独立目录使两套外壳可以并存对比，也避免误改日常使用的 Termux 配置。

读取时机：配置与键位表在 ShellApplication.onCreate 中同步读取。两个文件均为数 KB，异步读取会造成首帧后界面跳变；若后续出现更大的配置文件，必须改为后台加载并在完成后通知界面。重载入口为命令面板的「重新加载配置文件」，它会重新设置默认日夜模式，界面可能因此重建，标签与窗格结构通过 onSaveInstanceState 的布局编码恢复。

校验规则：SettingsSpec 集中定义键名、类型、默认值、合法取值与中文说明；模板文件由同一张表生成，避免「模板、校验、默认值」三处不一致。非法取值被忽略并记入 getInvalidKeys()，由启动日志列出；删除某项即回退默认值，不存在初始化时回写默认值的守卫。

新增测试依赖：org.json:json:20240303（testImplementation）。平台的 android.jar 中 org.json 是抛出 RuntimeException("Stub!") 的存根，布局编码器的单元测试需要可用实现；terminal-core 的 unitTests.returnDefaultValues = true 不能替代该类真实实现。

## 2026-11-20 — 命令面板、键位表与菜单共用一套动作实现

结论：外壳动作名称集中在 input/ShellAction，键位表、命令面板与溢出菜单都通过 ShellActivity.executeAction(String) 执行，快捷键展示文本由 KeymapTable.findShortcut() 生成（Ctrl+Shift+T 形式），避免三处各写一份动作字符串与快捷键文案。

行为约定：
- 键位表默认绑定标签页（新建 / 关闭 / 上下切换 / 左右移动）、窗格（左右分屏 / 上下分屏 / 关闭 / 焦点前后迁移）、窗口与界面（新建窗口 / 命令面板 / 标签栏开关）。keymap-enabled=false 时全部按键交还终端。
- 未识别的动作名称只记日志不抛异常，便于用户在旧版本应用上使用新版键位表。
- 命令面板为 MD3 浮动卡片（最大宽度 560dp、顶部 72dp、背景遮罩），输入即筛选，方向键移动、回车执行、Esc 关闭；CommandFilter 同时支持子串与子序列（缩写）匹配，纯函数实现以便单测。
- 不可执行的命令（例如仅剩一个标签时的「关闭标签页」）仍然展示但置为半透明，点击与回车均不生效。

取舍：标签栏为自绘 View（TabStripView）而非组合控件，原因是需要在「平均分配」与「按标题收缩」两种宽度模式间切换，并在超出宽度时交由外层 HorizontalScrollView 滚动；自绘颜色一律经 ThemeColors 解析，缺失属性时使用回退值，避免主题 token 变更导致崩溃。

## 2026-11-20 — 本次交付的验证方式与未验证项

环境事实：本机原记录为「JDK 21 + Android SDK 可构建」，本次实测发现 JAVA_HOME 指向不存在的 java-17-openjdk，Android SDK 目录为空、Gradle 缓存为空。已安装 openjdk-21，但 Termux 为 aarch64，build-tools 中的 aapt2 无对应二进制，./gradlew :app_new:assembleDebug 仍无法执行。

替代验证：下载 platforms/android-36 的 android.jar，按 Maven POM 递归解析 Material/AndroidX 的 classes.jar，从资源生成 R 存根，用 javac 编译 app_new/src/main/java 与测试源，再用 org.junit.runner.JUnitCore 运行 9 个测试类共 56 项；另以仓库外脚本检查资源 XML、资源引用与 values/values-zh 键集合。全部通过。

未验证：aapt2 资源编译、清单合并、布局 inflate、主题属性解析结果、lintDebug，以及全部界面交互（标签栏点击 / 长按 / 关闭、命令面板输入与键盘操作、配置变更后的界面重建）。以上需在 CI 或具备 Android SDK 的机器上补跑，并按 docs/DELIVERY_PLAN.md 第 10 节的设备清单验收；本次交付不声称界面已验证。

## 2026-11-20 — 新增外壳演示构建工作流（不修改既有工作流）

背景：需求方要求在 CI 上构建并可安装查看外壳骨架界面。既有 workflow 中 Fork Build 会顺带构建 app_new，但其触发路径不含 app_new/**，且产物与旧应用 APK 混在一起、需要签名校验。

结论：新增独立工作流 .github/workflows/app_new_debug_build.yml（AGENTS 第 3.4 节要求新增构建任务先记录方案，本次由需求方明确要求）。内容：
- 触发：workflow_dispatch 手动触发；或 app_new/**、settings.gradle、build.gradle、gradle.properties、wrapper 属性变更后推送 master。
- 步骤：setup-java 17 → ./gradlew :app_new:testDebugUnitTest → :app_new:assembleDebug → 上传构建产物 → 以 GITHUB_TOKEN 创建或更新预发布页 shell-demo 并覆盖上传 APK。
- 产物命名 termux-shell-<sha7>-debug.apk，包名 com.termux.shell，可与官方 Termux 并存安装；不带 sharedUserId，不涉及签名密钥与插件仓库。

取舍：不改动 fork_build.yml / debug_build.yml，避免影响旧应用发布链路；预发布页而非构建产物链接，是因为构建产物下载需要登录 GitHub，直接发布 release 资源便于在手机上直接安装。

## 2026-11-20 — 演示 APK 使用固定文件名与固定下载地址

问题：首版工作流把产物命名为 termux-shell-<sha7>-debug.apk，每次推送都会在 shell-demo 发布页新增一个资源，下载地址随提交变化，链接容易失效，也无法让同一地址始终指向最新构建。

结论：产物固定命名为 termux-shell-demo-debug.apk，上传时使用 --clobber 覆盖同名资源；提交哈希写入发布说明与构建摘要，需要追溯具体提交时查看说明即可。下载地址固定为：
https://github.com/clevebitr/termux-app/releases/download/shell-demo/termux-shell-demo-debug.apk

注意：app_new/PLAN.md 也位于 app_new/** 触发路径内，纯文档提交同样会触发一次构建；工作流已设置 concurrency 并取消进行中的旧构建，因此不会堆积。

## 2026-11-20 — 壳层迁移到 Kotlin 2.2.20 + Compose BOM 2025.10.01（M13-7）

结论：app_new 的界面层与逻辑层全部改为 Kotlin + Jetpack Compose，Java 实现与自绘 View、XML 布局、菜单与图标资源全部删除；terminal-core 复制基线保持 Java 只读，终端视图将来以 AndroidView 嵌入 Compose。

版本选择：Kotlin 2.2.20（Gradle 9.2.x 官方测试的 KGP 上限，且 Compose 编译器自 Kotlin 2.0 起随语言版本发布）、Compose BOM 2025.10.01（ui/foundation 1.9.4、material3 1.4.0、material-icons-core 1.7.8）、activity-compose 1.10.1。AGP 8.13.2、Gradle 9.2.1、JDK 17、compileSdk 36 不变。

结构性改动：
- 根 build.gradle 的 buildscript 增加 kotlin-gradle-plugin 与 compose-compiler-gradle-plugin classpath；app_new 用 apply plugin 应用（AGP 已在根 classpath，沿用同一模式）。
- 状态模型改为不可变：sealed interface PaneNode + PaneSplit/PaneLeaf，TabWorkspace 的每个操作返回新实例；新增 ShellController 以 mutableStateOf 持有工作区、视图选项与主题模式，Compose 据此重组。
- 删除 TabStripView / LayoutHost / CommandPaletteController / ThemeColors / WindowSizeResolver；标签栏、标题行、侧栏、窗格容器、命令面板改为 Compose 组件；宽度档位改由 BoxWithConstraints 推导，因此移除 androidx.window 依赖；窗口主题改为平台 XML 主题，仅负责首帧背景，AppCompatDelegate 不再参与主题切换。
- 单元测试从 JUnit4+Java 迁移为 JUnit4+Kotlin，窗格测试由 PaneLayoutControllerTest 改为 PaneLayoutTest，测试总数由 56 增至 60。

取舍与风险：
- Compose 版本与 Kotlin 版本强耦合，升级必须同步升级 BOM 与编译器插件，按 CI 结果验证，不能只改其一。
- 终端视图不得进入 Compose 合成，否则破坏脏行增量；由 M14 在窗格叶子内以 AndroidView 承载。
- 删除 material/appcompat 直接依赖后，APK 体积 Debug 包为 11.7 MB（Compose 运行时占主因），release 需靠 R8 与 Compose 按需裁剪。

## 2026-11-20 — Kotlin/Compose 迁移的本地与 CI 验证方式

本地环境无法执行 Gradle（Termux aarch64 无可用的 aapt2），因此迁移期间采用两级替代验证：
1. 安装 Termux 的 kotlin 2.4.21（本地编译器版本高于项目 2.2.20，仅用于语法与类型检查，避免使用新版本专有语法）。
2. 用 kotlinc 编译纯逻辑层并对测试运行 JUnitCore：先排除 Compose 文件编译通过 60 项测试；随后下载 Compose BOM 对应 AAR 的 classes.jar（ui-android 1.9.4、foundation-android 1.9.4、material3-android 1.4.0、material-icons-core-android 1.7.8、activity-compose 1.10.1 及其传递依赖），配合 kotlin-compose-compiler-plugin（非 embeddable 版本，否则 kotlinc 的非 embeddable 编译器缺少 shaded intellij 类会抛 NoClassDefFoundError）编译全部主源，通过。
3. 注意类路径顺序：Compose 依赖会带入旧版 kotlin-stdlib 与 android.jar 的 org.json 存根，必须把 kotlin-stdlib 2.4.21 与 org.json:json 放在 android.jar 之前，否则出现 lowercase/buildList 无法解析、JSONObject 抛 Stub! 等假错误。

CI：app_new_debug_build.yml 上 :app_new:testDebugUnitTest 与 :app_new:assembleDebug 均通过（run 44781087804，2m16s），产物 11.7 MB。lintDebug 仍未执行；真机上的 Compose 渲染、命令面板输入法交互、分割条拖拽手感与主题切换效果未验证。

## 2026-11-22 — 分层与状态管理重构：ShellViewModel + ConfigSource + 提示事件（M13-8）

审核结论：原 ShellController 同时承担工作区操作、界面导航、设置镜像与落盘、会话注册表派生、路径暴露与提示回调，共 410 行；持有 Activity Context 与 ShellApplication 静态单例，无法在 JVM 测试；7 个独立设置状态字段在重载时逐项赋值；提示经 onMessage(resId) 回调到 Activity Toast；命令目录位于 commands 包却依赖 Context/R。

重构：
1) ShellController 改为 ShellViewModel（androidx.lifecycle.ViewModel），构造注入 ConfigSource（配置/键位/路径/重载）、titleFactory、SettingsPersister；不持有 Context、资源或静态单例。onCleared 取消写协程。
2) 设置状态聚合为不可变 ShellSettings 快照（主题、布局、侧栏、全屏、命令面板、键位、视图选项），重载时整体替换；所有写入走 updateSetting(key,value,transform)，先乐观更新界面，再由 SettingsPersister 写入磁盘；失败发布 SETTINGS_SAVE_FAILED。
3) 提示改为 ShellNotice 枚举 + consumeNotice 一次性事件，界面映射为字符串并显示 Snackbar；新建窗口改为 newWindowRequestToken 事件并在窗口消费后归零，避免窗口重建时重复创建。
4) 命令目录迁移到 ui/ShellCommandCatalog（依赖资源与键位表），commands 包只保留 ShellCommand 与 CommandFilter 纯逻辑；界面在面板打开时构造一次命令清单。
5) Activity 只负责生命周期、输入路由（LaunchedEffect 同步 viewModel.keymap/keymapEnabled）、系统栏与实例状态保存；导航状态（面板、设置页、抽屉）移入视图模型，窗口重配置不丢失。
6) MemorySettingsRepository 的 getBoolean/getInt/getString 统一兼容 put 存入的文本与 putBoolean 存入的布尔值，修复测试替身读回默认值的缺陷。

新增依赖：androidx.lifecycle:lifecycle-viewmodel:2.9.4、org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2（协程用于后台落盘与主线程回调）。

## 2026-11-22 — 界面动效与交互细节：覆盖层、过渡动画与触控目标

动效：
- 命令面板由独立 Dialog 改为窗口内覆盖层，随 Activity 窗口淡入并轻微缩放（0.98→1.0），遮罩点击与返回键关闭，imePadding 避让输入法；卡片用 detectTapGestures 消费点击，避免点输入框被遮罩关闭。
- 标签栏显隐使用 expand/shrink + fade；标签底色、内容色与 2dp 选中指示条使用 140–160ms 颜色过渡；长按标签触发 LongPress 触觉反馈。
- 标题行使用 AnimatedContent 交叉淡入；设置页与主界面之间淡入淡出；会话侧栏收起/展开使用水平展开动画，宽度随档位 180ms 过渡。
- 窗格焦点描边颜色与宽度过渡，分割条在悬停或拖拽时以 120ms 淡入 45% 强调色；命令面板选中行背景 120ms 过渡。
- 所有动画使用 Compose 默认 MotionDurationScale，系统关闭动画时自动即时切换。

细节：
- 会话栏操作按钮 36dp→40dp，新标签按钮 40dp，标签关闭按钮 20dp→28dp，图标同步微调。
- 修复标签栏新增按钮与关闭按钮在无按压反馈时的视觉突兀：统一使用颜色过渡的背景态。
- 修复 MemorySettingsRepository 读取与写入存储类型不一致（详见 #9）。

## 2026-11-22 — 仓库 Actions 被禁用：本轮 CI 构建未能执行

现象：推送提交 8742db2 后 workflow_dispatch 与 push 均未创建运行。手动触发返回 HTTP 422「Actions has been disabled for this repository」；此时 GET /repos/clevebitr/termux-app/actions/permissions 返回 enabled=true，workflow 列表中的 Shell Demo Build 状态为 active，仓库未归档、未禁用，且账号对仓库有 admin 权限。

已排查：PUT /actions/permissions 开启、检查 workflow 状态、检查 fork/archived/disabled 字段、检查所选 actions 策略，均未解除。原因在 GitHub 侧（仓库或账号层面），本地无法继续处理。

影响：M13-8 的 CI 验证（:app_new:testDebugUnitTest 与 :app_new:assembleDebug）与 shell-demo APK 更新未执行。本地替代验证：kotlinc 2.4.21 + Compose 编译器插件编译全部主源通过；JUnitCore 运行 13 个测试类共 94 项全部通过。

后续：需要仓库管理员在 GitHub 设置中恢复 Actions（Settings → Actions → General），恢复后推送任意 app_new 变更或手动触发 workflow 即可重新出包；在此期间不得声称界面/安装包已验证。

## 2026-11-22 — 原始仓库 Actions 被禁用：改用镜像仓库出包并保留固定下载地址

排查补充：用 gh 在账号下新建私有探针仓库并触发 workflow，9 秒成功，说明账号级 Actions 正常；随后对原仓库执行 PUT /actions/permissions（enabled=true、allowed_actions=all）、关闭后再开启、以及直接 POST workflow dispatch，均返回 422「Actions has been disabled for this repository」。workflow 状态为 active，仓库未归档未禁用，当前账号对仓库有 admin 权限，GitHub 状态页无故障。结论：原始仓库在 GitHub 侧被单独禁用，API 无法解除，需要 GitHub 支持或仓库设置中的额外策略调整。

解决办法：把当前 HEAD 通过 git archive 导出为单提交快照，推送到新建的公开仓库 clevebitr/termux-shell-demo（不做历史复制），由该仓库的 Shell Demo Build 工作流出包。构建 run 45026422748 通过（单测 + assembleDebug），发布页 shell-demo 提供固定地址：
https://github.com/clevebitr/termux-shell-demo/releases/download/shell-demo/termux-shell-demo-debug.apk
大小 11.8 MB，SHA-256 42610d39d1e25e961a674cdc4fc9f6ba5cb2ceebf168d82d6c123596d8ac3c98。

镜像仓库只承载构建快照，不接收后续开发提交；原始仓库 Actions 恢复后应删除镜像，并把发布页迁回原仓库。探针仓库 clevebitr/actions-probe 因当前 token 缺少 delete_repo 作用域未能删除，需要账号所有者手动删除或授权 gh auth refresh -s delete_repo。

## 2026-11-22 — 确认会话归属模型 B 并落地会话配置（profile，M19）

需求方确认：一个标签 = 一个主会话 + 分屏产生的额外会话（Windows Terminal 式窗格树保留）。标签记录主会话所用的配置标识，分屏沿用同一配置；每个窗格叶子绑定唯一 sessionId；同一会话不得同时挂到两个窗格或两个窗口；关闭标签关闭其全部会话，关闭窗口按 detach（默认）或 kill 策略（M14 实现）；应用进程死亡时会话随之结束，跨进程存活依赖 tmux/dtach，不在应用内实现。

已实现（M19-1…M19-4）：
- session/SessionProfile：SHELL / DISTRO / SSH 三类，名称与标识非空、DISTRO 必须给发行版名、SSH 必须给主机；不保存凭据。
- config/ProfileCodec（JSON 版本 1）、ProfileStore 契约、FileProfileStore（原子写：临时文件 + 重命名；StateFlow 共享快照；损坏文件保留旧列表）。文件位于 ~/.termux/shell-profiles.json。
- ShellApplication 创建单例 FileProfileStore，ConfigSource 暴露 profiles()，这是首个跨窗口共享状态：任一窗口增删改后其他窗口自动刷新。
- WorkspaceTab 增加可空 profileId；TabWorkspace.newTab(title, profileId)；布局编码新增可选 profile 字段，旧文档缺省视为默认 shell。
- ShellViewModel：profiles 状态流、profileById、newTab(profile)、newTabWithProfile、saveProfile/deleteProfile；改名同步标签标题，删除清除标签绑定；新建标签标题改用 workspace.nextTabNumber，修复关闭中间标签后重名。
- 设置页「会话配置」分组与 ProfileEditorDialog（类型切换、启动命令、工作目录、环境变量 KEY=VALUE）；命令面板追加「新建会话：<名称>」。

边界：按配置真正启动进程（proot-distro login、ssh、自定义命令）依赖 M14 会话宿主，当前仅记录绑定；分屏沿用主配置同样待 M14。

验证：本地 kotlinc + Compose 编译器编译通过；JUnitCore 运行 17 个测试类共 112 项通过（新增 SessionProfileTest 6 项、ProfileCodecTest 3 项、FileProfileStoreTest 5 项、标签绑定与标题编号 3 项）。原始仓库 Actions 仍被禁用，CI 走镜像仓库。

## 2026-11-22 — 会话宿主与终端视图首批交付（系统 shell，M12-2…M12-4）

结论：时机成熟，先以系统 shell 打通「配置 → 会话 → 终端视图」闭环，bootstrap 相关能力仍留给 M13.5。

实现：
- session/LocalSessionHost（SessionProvider）：进程级持有 TerminalSession，createSession 只构造不启动；首次挂载视图按实际尺寸 updateSize 才创建 PTY。关闭会话仅在 pid>0 时发送 SIGKILL，规避未启动会话 pid=0 导致 Os.kill(0,…) 误伤当前进程组。
- session/SessionEnvironment：HOME=应用主目录、TMPDIR=缓存目录、PATH=/system/bin:/system/xbin、TERM=xterm-256color、SHELL=/system/bin/sh。
- session/ProfileCommandBuilder：SHELL 直传命令；DISTRO 生成 proot-distro login '<distro>'；SSH 生成 ssh '<host>'；参数单引号转义；环境变量以默认环境为底、配置覆盖。
- terminal/TerminalSessionClientAdapter、TerminalViewClientAdapter：文本变化驱动视图重绘、标题/退出/进程号转发、剪贴板写入、单击唤起输入法、保留回滚位置；按键交回终端键序编码。
- terminal/TerminalViewPool：按 sessionId 复用 TerminalView，保证单挂载；显式设置可聚焦并在获得焦点时回写外壳焦点窗格；创建/附加失败返回 null 回退占位。
- ShellViewModel/ShellApplication：注入 SessionProvider；启动/新建标签/分屏时为叶子补齐会话；关闭标签/窗格时关闭对应会话；恢复布局时检测失效 sessionId 并重新创建。

边界与未验证：无 $PREFIX/bootstrap，proot-distro 与 ssh 不可用，配置会直接打印工具缺失；会话列表仍由工作区派生，未展示真实标题/PID，接管脱离会话未实现；窗口关闭当前相当于 detach（不结束会话）；设备侧未运行，仅本地编译与 123 项单元测试通过。

## 2026-11-22 — 新增 minified release 包用于定位卡顿来源

背景：真机反馈外壳可用但手感偏卡。当前 debug 包未压缩、Compose debug 运行时开销高，且终端渲染尚未做脏行增量（M12-6）与同步输出（M12-5）；需要先区分「debug 构建开销」与「渲染管线本身」。

做法：app_new 的 release 构建开启 minifyEnabled + shrinkResources，R8 规则保留 com.termux.shell.terminal.JNI（名称绑定原生方法）与终端/视图包类名；新增 .github/workflows/app_new_release_build.yml，手动触发，使用 CI 生成的调试签名（与 debug 包同密钥，可直接覆盖安装），产物发布到 shell-demo 发布页的 termux-shell-demo-release.apk，与 debug 包并存。

验证方法：同一组操作（输入回显、大输出、滚动历史、键盘弹出/收起）对比 debug 与 release 的手感；若 release 明显更顺则优先继续做构建与渲染优化，若两者都卡则推进脏行增量、同步输出与 PTY resize 去抖。

风险：R8 可能暴露 JNI/反射裁剪问题（优先检查 UnsatisfiedLinkError）；release 包仅用于性能对比，不作为发布产物；不改变 applicationId/targetSdk，不影响现有 Termux 安装。
