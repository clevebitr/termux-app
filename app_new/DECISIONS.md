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
