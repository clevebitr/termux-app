# DECISIONS

> 决策与踩坑日志，只追加，不参与日常上下文。可用 grep 检索。条目按文件顺序编号（第一个 `##` 是 #1）。

## 2026-10-18 — 签名与插件兼容（sharedUserId + signature 权限）

**结论：不能混用，必须同一签名。** 1) 主 App 和 6 个插件（api/boot/float/styling/tasker/widget）都声明 `android:sharedUserId="com.termux"`（app/src/main/AndroidManifest.xml:5），Android 安装时强制要求同 UID 组签名一致，否则 `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`（中文 ROM 显示"与其他用户签名冲突"）。2) termux-api 还声明 `com.termux.sharedfiles.READ_WRITE`（protectionLevel=signature）。3) 运行时 `TermuxUtils.isTermuxAppAccessible()` / `getTermuxAppAPKClassField()` 依赖 shared UID + 跨 APK 反射读 BuildConfig。4) APK 发行版靠签名摘要判定（TermuxConstants.java:482 起）。\n\n**已核实事实**：termux-app 与 6 个插件仓库里的 `testkey_untrusted.jks` 是同一个文件（git blob `174cc95acd605bf557f9e63983f55fadc5ae44bd`，2097 字节），其证书 SHA-256 = `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1` = TermuxConstants 里的 `APK_RELEASE_GITHUB` 摘要 → 官方 GitHub 分发版用的就是这把**公开**密钥，所以 testkey 编译的 debug 包能和官方 GitHub 版插件共存（F-Droid 版 228fb2cf… / Play 版 738f0a30… 不行）。\n\n**操作要点**：保留包名 com.termux；换签名必须先卸载主 App + 所有插件（ColorOS 的"卸载保留数据"能保住 $PREFIX）；备份见 `FORK.md §4.4`；`ownkey` 模式用 `-Pandroid.injected.signing.*` 给插件注入签名，无需改插件 build.gradle。

## 2026-10-18 — CI 结构：fork_build.yml / testkey-ownkey / fork-latest

工作流：`.github/workflows/fork_build.yml`（Fork Build (signed APKs)）。触发：push master（仅当改动命中 app/**、terminal-*/**、termux-shared/**、*.gradle、gradle.properties、wrapper、该 workflow 本身）+ workflow_dispatch。任务：app（主 App）→ plugins（矩阵克隆 `termux/<repo>@master` 编译 6 个插件）→ release（合并产物、sha256、发布 `fork-latest` 预发布 Release）。\n\n模式：无 secrets → `testkey`（全部 assembleDebug，签名天然一致）；配了 `TERMUX_KEYSTORE_BASE64/…PASSWORD/…ALIAS` 4 个 secret → `ownkey`（assembleRelease + `-Pandroid.injected.signing.*`）。每个插件都会用 apksigner 校验证书摘要与主 App 一致，不一致直接 fail。\n\n坑与约定：1) fork 里 Actions 可能未注册（`gh api repos/<fork>/actions/workflows` 返回 total_count 0）→ 用 `PUT /repos/<o>/<r>/actions/permissions -F enabled=true` 打开；默认 workflow 权限要设 write 才能建 Release。2) release 任务没有 checkout，`gh release create/delete` 必须加 `-R "$GITHUB_REPOSITORY"`，否则报 "not a git repository"。3) 构建约 6–8 分钟；`gh run list/view --log-failed` 看失败。4) 上游 `Build`/`Unit tests` 也会跑（等于额外回归验证）；`Automatic Dependency Submission`、`Trigger Termux Libraries on Jitpack` 在 fork 里无意义已 `gh workflow disable`。

## 2026-10-18 — Gboard CJK 兼容实现（gboard-cjk-workaround）

现象：Gboard 在终端视图里只会显示 ASCII QWERTY 并隐藏语言切换键，无法直接输中文（上游 #1539/#202，维护者确认是 Gboard 不遵守 `InputType.TYPE_NULL`）。`enforce-char-based-input=true` 也无效（VISIBLE_PASSWORD 同样被强制 QWERTY）。\n\n实现（`terminal-view/.../TerminalView.java` + 属性 `gboard-cjk-workaround`，默认 false）：\n- `onCreateInputConnection()`：开关打开且终端视图选中时，用 `InputType.TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS | (CJK ? TYPE_TEXT_VARIATION_NORMAL : TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)`；语言未知则回退原来的 TYPE_NULL。\n- 语言识别：`InputMethodManager.getCurrentInputMethodSubtype().getLanguageTag()`（API 24+），zh/ja/ko 视为 CJK。\n- 切语言：Gboard 切语言会回调 `InputConnection.requestCursorUpdates()`，在此覆写里比对语言标签变化 → `restartInput()`（延迟 40ms 复查一次）。\n- CJK 下每次 `commitText` 后 `invalidateInput()`（API 33+）否则 `restartInput()` 压掉候选条。\n- 副作用：非 CJK 语言退化为 visible-password 风格（无联想），与 termux-x11 PR #620 的做法一致。\n\n新增接口方法 `TerminalViewClient.isGboardCjkWorkaroundEnabled()` 需要同时改 `TermuxTerminalViewClientBase`（默认 false）与 app 的 `TermuxTerminalViewClient`；属性加进 `TERMUX_APP_PROPERTIES_LIST` + `TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST`。相关：点击不弹键盘时用 extra-keys 的 KEYBOARD 键 / Ctrl+Alt+K。

## 2026-10-18 — extra keys 配色联动链路与三个背景色坑

目标：虚拟键盘（extra keys 行）配色跟 Termux:Styling 主题联动。\n\n链路：`colors.properties`（Styling 写）→ `TermuxTerminalSessionActivityClient.checkForFontAndColors()` → `TerminalColors.COLOR_SCHEME.updateWith()` → `TermuxActivity.setExtraKeysViewColors()`（termux.properties 覆盖优先）→ `ExtraKeysView.setButtonColors()`。\n\n派生规则（`TerminalColorScheme`）：`extra-keys-foreground`→默认 foreground、`extra-keys-background`→默认 background、`extra-keys-active-foreground`→默认 cursor、`extra-keys-active-background`→默认 `blendColors(bg, fg, 0.25)`；`updateWith()` 对未知键仍抛 IllegalArgumentException，所以新键必须显式分支处理。\n\n覆盖项：`termux.properties` 里同名 4 个键（优先级高、不会被 Styling 覆盖），用 `android.graphics.Color.parseColor()` 解析（支持 alpha）。\n\n坑（都已修）：1) 承载 extra keys 的 `terminal_toolbar_view_pager` 在布局里写死 `@color/black` → 会让按键"主题色 + 黑框"，现在 `extra-keys-background` 同时应用到 pager 与 ExtraKeysView 自身背景；2) 文本输入页 EditText 文字色写死白色 → 现在跟随 `extra-keys-foreground`（`TermuxActivity.setTerminalToolbarTextInputViewColors()`，在 pager 建页时调用一次）；3) `ExtraKeysView.reload()` 原本不设置初始背景（只有触摸才设），现在建按钮时就设，并且用 `getButtonIdleBackgroundColor()` 以保留激活态背景。\n\n注意：`termux.properties` 会被 Styling 主题覆盖，所以长期自定义要写 termux.properties；`ExtraKeysView` 属 termux-shared，改动会影响 Termux:Float（Float 未接主题联动）。

## 2026-10-18 — night-mode=color-scheme 与状态栏/导航栏图标

`night-mode` 新增取值 `color-scheme`：读 `colors.properties` 的 `background` 亮度（阈值 **130**，与光标色自适应同阈值）决定浅/深色。\n\n实现：`NightMode` 加 `COLOR_SCHEME`（+ `MAP_NIGHT_MODE` 加 `IVALUE_NIGHT_MODE_COLOR_SCHEME`，否则属性解析会报无效）；`TermuxThemeUtils.resolveNightMode(name)` / `isLightBackground(color)`；`TermuxActivity.setActivityTheme()` 自动走解析后的值，`updateNightMode()` 供 `checkForFontAndColors()` 在主题变化后重挂（AppCompat 会自动重建 Activity）。\n\n状态栏/导航栏：原仓库**完全没有**处理（浅色背景上白图标看不见）。新增 `TermuxActivity.updateWindowBackgroundColor(color)`：设 decor 背景 + 用 `WindowInsetsControllerCompat.setAppearanceLightStatusBars/NavigationBars` 切图标明暗；`TermuxTerminalSessionActivityClient.updateBackgroundColor()` 改为委托它，并在 `onResume()` 里补一次调用（原来只在 checkForFontAndColors/onColorsChanged/切会话时调用，且要求 isVisible，启动瞬间可能没生效）。\n\n测试注意：`TerminalColors.getPerceivedBrightnessOfColor()` 用 `android.graphics.Color`，在纯 JVM 单测里返回 0（`unitTests.returnDefaultValues = true`），所以断言不要写死由亮度派生的颜色（我第一次就因此让 `testExtraKeysColors` 挂了）。

## 2026-10-18 — 设置页 ↔ termux.properties 桥（PreferenceDataStore）与非传递 R 坑

应用设置页（androidx.preference）原本只绑 SharedPreferences，本次加了"属性版"通路：`TermuxSharedProperties.getTermuxPropertyValue(Context,key)` / `setTermuxPropertyValue(Context,key,value)`（用 `FileUtils.readTextFromFile/writeTextToFile`，按行正则 `^\s*#?\s*([^=\s]+)\s*=` 替换同名键、保留注释与其他行、重复键注释掉；value=null 表示注释掉恢复默认）。\n\n设置页：`TerminalKeyboardPreferencesFragment` + `TerminalKeyboardPreferencesDataStore extends PreferenceDataStore`（get/put Boolean/String 落到 properties 文件，改完 `loadTermuxPropertiesFromDisk()` + `TermuxActivity.updateTermuxActivityStyling(ctx,false)` 立即生效），XML `res/xml/termux_keyboard_preferences.xml`，入口加在 `res/xml/termux_preferences.xml`。\n\n自定义颜色控件 `ColorPreference`：22 色板（含半透明）+ 自定义 hex（`Color.parseColor` 校验）+ "Follow color scheme"（清除）。\n\n**坑（已踩，务必记住）**：AGP 8 默认非传递 R class，`app` 模块**没有** `R.color.*`（连它自己声明的都没有任何颜色资源），引用 termux-shared 的颜色 `R.color.grey_500` 会编译失败 → 要在 `app/src/main/res/values/colors.xml` 里自定义，或用 `ThemeUtils.getSystemAttrColor()`。同理，XML 里的 `@color/x` 是可以跨模块解析的，Java 里不行。

## 2026-10-18 — UI i18n：values-zh + locales_config + 两项机器校验

方案：三个模块各加 `values-zh`（app 126 条、termux-shared 55 条、terminal-view 3 条：复制/粘贴/更多），VT 内部无面向用户文案故不动。多语言基础设施：`app/src/main/res/xml/locales_config.xml`（en、zh）+ manifest `android:localeConfig` → Android 13+ 可按应用选语言；12 及以下跟随系统。`supportsRtl` 仍是上游的 false（要加阿语/希语需改 true 并检查布局）。\n\n**校验方法（必须做，否则 aapt/运行时才炸）**：\n1) 键名完整性：`grep -oE 'name=\"[a-z0-9_]+\"'` 后 `comm` 比对 en 与 zh，缺失/多余都要为 0；\n2) 占位符一致性：逐条比对 `%1$s`/`%2$d`/`%d` 序列（脚本 `~/strcheck.js` 曾用于此，注意 $HOME 里的临时脚本在重装/数据重映射后会丢，最好放到 /sdcard 或 repo 的 tools/）；\n3) 英文原文里的 `\n`、`\"`、`&ENTITY;`、markdown 标记必须原样保留。

## 2026-10-18 — 激活态高亮：SpecialButtonState 只改文字色

问题：按下 CTRL/ALT 只改文字色，而"激活文字色"默认取配色方案的 `cursor` 色（常与普通文字色相同）→ 视觉上零变化；且触摸监听在抬手/取消时把背景复位为普通色，`reload()` 也会覆盖。\n\n修法（termux-shared）：`ExtraKeysView.updateSpecialButtonStateColors(button, isActive)` 同时设文字色与激活背景色；`getButtonIdleBackgroundColor(buttonInfo)` 让抬手/取消/popup 收起时回到"激活背景色（若激活）"；`createSpecialButton()` 与 `reload()` 都用它；`SpecialButtonState.setIsActive()` 改为调用该方法（同一类键的多个实例一起更新，长按锁定同样保持）。\n\n可调：`extra-keys-active-background`（默认 `blend(bg,fg,0.25)`，黑色主题约 #3f3f3f），设置页也暴露为"激活时背景颜色"。

## 2026-10-18 — 环境限制：本机无法编译，验证只能靠 CI

本机（Termux）**没有 JDK / Android SDK / NDK**（`java`、`javac`、`gradle` 均不存在，`$ANDROID_HOME` 为空），所以本地无法编译或跑 gradle；唯一可行的验证路径是 **push 到 master → GitHub Actions**（`Fork Build` 编译+签名+证书一致性校验、`Unit tests` 跑 gradle test、上游 `Build` 再编两套 variant）。约 6–8 分钟一轮。\n\n本地只能做静态检查，三个自研脚本（放在 `/storage/emulated/0/Download/AppProjects/tools/`，**不要放 $HOME**，App 重装/数据重映射会丢）：\n- `javabal2.js`：Java 词法级括号/引号平衡（跑全仓库 201 个 .java，应 0 失败）。\n- `xmlcheck.js`：XML 良构性（标签配对/注释/CDATA）。\n- `strcheck.js`：i18n 键名与占位符一致性。\n另外：`/tmp` 不可写（要用 `$HOME` 或 /sdcard）；仓库在 /sdcard（App 卸载不影响）；`gh` 已登录 clevebitr，`git push` 需要 `-c credential.helper='!gh auth git-credential'`，网络偶发 SSL/超时，要重试循环（实测最多第 4 次成功）。

## 2026-10-18 — M3 范围与页面组织（外观页 + 按键行为 + 鼠标点按唤键盘）

范围由用户确认：1) 「鼠标点按唤键盘」做成**新属性** `mouse-tap-shows-keyboard`（默认 false，保持上游行为），不直接改默认；2) 「工具栏」这组只做 `extra-keys-style` + `extra-keys-text-all-caps` + `terminal-toolbar-height`（不做 SharedPreferences 里的 showTerminalToolbar）；3) 页面组织：新建「外观」页（主题/全屏/工具栏，`AppearancePreferencesFragment`），按键行为加到现有「终端键盘」页的新分类（`back-key` / `volume-keys` / `soft-keyboard-toggle-behaviour` / `hide-soft-keyboard-on-startup` / `mouse-tap-shows-keyboard` / `disable-hardware-keyboard-shortcuts`）。

新属性的判定逻辑在 `TermuxTerminalViewClient.onSingleTapUp()`：原来条件是 `!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)`，现在改成 `boolean isMouseTap = e.isFromSource(SOURCE_MOUSE) || term.isMouseTrackingActive(); if (!isMouseTap || properties.shouldShowSoftKeyboardOnMouseTap())`。所以它同时覆盖「鼠标点击」和「鼠标跟踪生效时的点击（含手指）」，这也符合 FORK.md §6 里描述的「TUI 开了鼠标跟踪点击不弹键盘」的历史行为修补。

「外观」页的属性都是创建 Activity 时读取的（`fullscreen` 在 `onCreate` 加 FLAG_FULLSCREEN，`terminal-toolbar-height` 在 `setTerminalToolbarHeight()` 里算高度），所以 `AppearancePreferencesDataStore` 用 `updateTermuxActivityStyling(ctx, true)` 让 Activity 重建一次；`TerminalKeyboardPreferencesDataStore` 仍用 false。会话/transcript 在 TermuxService 里，重建不掉。

顺带修了 `TermuxActivity.reloadActivityStyling(false)` 的 night-mode 通路：原来只调 `TermuxThemeUtils.setAppNightMode()` 设静态值（改了 `night-mode` 属性不会重建 Activity，主题不生效），现在 recreate=false 时改调 `setActivityTheme()`（`setLocalNightMode` 只在真的变了才重建）；recreate=true 时保持原来的静态赋值以免重复重建。

i18n：新增 `values/arrays.xml` + `values-zh/arrays.xml`（5 组 entries/entryValues），文案 en/zh 各 30 余条。注意「工具栏高度（%）」标题里的裸 `%` 不要写成 `% i` 这种形态，会被工具误判成占位符。

## 2026-10-18 — 坑：PreferenceDataStore 初始化会回写默认值 → 抽 TermuxPropertyPreferenceDataStore 加 no-op 守卫

`androidx.preference` 的 `ListPreference.setValue()`（`onSetInitialValue` → `setValue(getPersistedString(default))`）和 `SwitchPreferenceCompat.setChecked()`（值为 true 时）、`SeekBarPreference.setValue()`（值 != 内部初值 0 时）都会**在初始化阶段就调 `persistString/persistBoolean/persistInt`**，也就是「刚读到的值再写回去」。原来的 `TerminalKeyboardPreferencesDataStore` 直接落盘 + `updateTermuxActivityStyling()`，所以：1) 光是打开设置页就会往 `termux.properties` 里插入 `back-key=back`、`night-mode=system` 这类用户没设过的键；2) 对「外观」页更糟——每次打开都会走 `updateTermuxActivityStyling(ctx, true)` 重建一次 Activity。

修法：抽出 `TermuxPropertyPreferenceDataStore`（base，`app/.../fragments/settings/termux/`，两个 DataStore 都继承它，键盘页那份改为 3 行），用 `mLastReadValues` 记住每个键「上次 getter 返回的值」，`putXxx` 的值与它相等就跳过（并 remove）。要点：
- 比较对象必须是**从文件读到的值**，不能拿被写入的值当默认值比较——属性没定义时 `getBoolean(key, newValue)` 会返回 newValue，那样判断相等就永远不写了（用户改了也不生效）。
- 属性没定义 + 用户选了跟 XML 默认值相同的项 → 也跳过，语义正确（生效值没变）。
- 键名必须与属性名一致（两个页都满足），base 里 `putBoolean/putString` 直接按 key 落盘。
- `SeekBarPreference` 只能存 int，而我们用的是 float 倍率：约定 int = 百分数（40–300），`putInt` 写 `value/100f`（`String.valueOf` → `"1.0"`/`"1.25"`），`getInt` 用 `DataUtils.getFloatFromString` 再 `Math.round(*100)`；被越界属性（如 `5`）会被 seekbar clamp 后写回合法值，属于预期。

不要用 `Preference.setSummaryProvider()` 来显示滑条数值：其签名（`SummaryProvider<Preference>` 还是 raw）没能确认，改用 `app:showSeekBarValue="true"` + 标题写「工具栏高度（%）」+ 静态 summary 说明取值范围，零 API 风险。确认签名前不要重构成 summaryProvider。

另外 `tools/strcheck.js` 的占位符正则升级为支持 flags/width/precision（`%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z%]`，故意不含空格 flag，否则 `100% is` 会被误判），以后写 `%1$.2f` 这类也能校验。

## 2026-10-18 — 坑：设置页改动不生效——ACTION_RELOAD_STYLE 广播在 TermuxActivity 停止时被丢

真机验证发现：在设置页改完属性返回 Termux **不生效**，要手动 `termux-reload-settings` 或在终端里重启 App。M1 的键盘页其实一直有这个问题，FORK.md §9「改完立即生效」的说法是错的。

根因：`TermuxActivity` 的广播接收器原本在 `onStart()` 注册、`onStop()` 注销，而 `SettingsActivity`（`Theme.TermuxApp.DayNight.NoActionBar`，不透明全屏）打开时 TermuxActivity 会 stop → `TermuxPropertyPreferenceDataStore` 发的 `ACTION_RELOAD_STYLE` **根本没有接收者**；接收器里那句 `if (mIsVisible)` 因此形同虚设。在终端里跑 `termux-reload-settings` 时 TermuxActivity 在前台，所以那条路径正常。上游设置全是 SharedPreferences（同进程共享实例，写读同一对象，不需要重载），只有写 `termux.properties` 的这几个页面会踩到。

修法（`TermuxActivity`）：
1. 接收器改 `onCreate()` 注册 / `onDestroy()` 注销（`mIsInvalidState` 早退发生在注册之前，所以早退路径本来就没注册，onDestroy 的早退跳过 unregister 仍然配对）。
2. `onReceive` 里若 `!mIsVisible`：先跑 `fixTermuxActivityBroadcastReceiverIntent()`（把 `EXTRA_RELOAD_STYLE="storage"` 那种转成权限请求，避免被误当成样式重载），然后只对 `ACTION_RELOAD_STYLE` 记 `mReloadActivityStylingOnStart = true` 与 `mRecreateActivityOnStart |= intent.getBooleanExtra(EXTRA_RECREATE_ACTIVITY, true)`，其余 action 直接 return（等价于改动前「停止期间收不到」）。
3. `onStart()` 末尾消费这两个标志，调用 `reloadActivityStyling(recreate)`（位置在 clients 的 `onStart()` 之后，和「广播刚好此时到达」等价）。标志是实例字段，`recreate()` 后新实例为 false，不会循环重建。

为什么不直接在停止期间重载：`reloadActivityStyling` 会动 extra keys view、改窗口 insets、调 `setSoftKeyboardState`，stop 期间做这些不安全；`recreate()` 放在 `onStart()` 里是安全的（`ActivityThread.handleRelaunchActivityInner` 会自己处理 paused/stopped 状态），而且 `onResume` 随后会再调一次 `setSoftKeyboardState(true, isActivityRecreated)` 兜底。

`recreate` 位必须由广播携带而不能统一用 true：键盘页/`TermuxService` 传 false（这些属性用到时现读，或者本来每次可见都重读），只有外观页的 `fullscreen` / `use-fullscreen-workaround` / `extra-keys-style`（`setExtraKeys()` 只在构造时跑）必须重建。外观页 false→true 会让每次返回都闪一下重建。

副作用（可接受）：`TermuxService` 在停止期间新建会话时发的那个 false 广播，现在会留下一个待重载标志，下次 `onStart()` 多跑一次 `reloadActivityStyling(false)`——不做 UI 破坏，只是多读一次文件。

## 2026-10-18 — M4 实现：真斜体缩放对齐 + OSC 8 按格存储与渲染分组

范围由用户确认：斜体自动探测 `~/.termux/font-italic.ttf`、无则回退现有合成斜切；OSC 8 点击共用 `terminal-onclick-url-open`（默认关）；链接按格存储（reflow/resize 都正确）；超链接自动加下划线。实现位置与用法已写进 FORK.md §13。

**真斜体**：`Typeface.create(自定义Typeface, ITALIC)` 拿不到真斜体，必须有第二个字体文件，所以加 `TermuxConstants.TERMUX_FONT_ITALIC_FILE`（`~/.termux/font-italic.ttf`，Termux:Styling 不写它）。`TerminalRenderer` 多一个 `mItalicTypeface`；度量（行高/基线/列宽/asciiMeasures）一律取正体，只在实际画 italic run 时切 typeface，并用 `mItalicTextScaleX = 正体'X'宽 / 斜体'X'宽` 缩放，保证网格不被撑歪（度量一致的字体缩放=1，无失真）。保留 `TerminalRenderer(int,Typeface)` 与 `TerminalView.setTypeface(Typeface)` 旧重载，Termux:Float 等调用方不受影响。斜体文件加载失败只记日志，不影响正体/配色。

**OSC 8 存储**：样式 long 已经满了（bit 15 = TERMINAL_BITMAP，位 16-63 是颜色），所以不能像 bitmap 那样塞进 style；改为 `TerminalRow` 里**惰性**分配 `int[] mHyperlinks`（0=无链接，N=本行 URI 表下标+1），URI 表是每行一个 `List<String>`（行内链接通常 1-2 个，`indexOf` 去重足够）。惰性分配很关键：transcript 可以有一万行，无条件加 int[] 会让行内存 (long[]+int[]) 涨 50%。宽度 2 的字符要把链接写到**两格**（`getHyperlinkAtLocation` 拿到的列可能是宽字符后半）。`copyInterval` 必须带链接；`this == line` 的自拷贝要对 `mHyperlinks` 做快照（上游 mStyle 有这个重叠 bug，链接这份先做对）。`TerminalBuffer` 加 `setChar(...,String hyperlinkUri)` 重载（旧 4 参重载保留，擦除路径 blockSet/EL/ED/ECH 走旧重载 → 自然清链接）与 `getHyperlinkAtLocation(x,y)`（坐标约定同 `getWordAtLocation`，负数=transcript，超界返回 null，避免 `externalToInternalRow` 抛异常）。reflow（resize 改列数）里除了 `styleAtCol` 还要记住 `hyperlinkAtCol`（零宽字符沿用上一个）。

**渲染下划线**：`TerminalRenderer.render()` 的 run 分组条件要加 `hasHyperlink != lastRunHasHyperlink`，否则同一样式的链接文字无法单独加下划线；bitmap 分支要一起复位该标志。`drawTextRun` 多带一个 `hasHyperlink` 参数，`underline = 原下划线 || hasHyperlink`。

**OSC 8 解析**：`doOsc` 的 `textParameter` 是第一个 `;` 之后的内容，所以 `OSC 8;params;URI` 要再按第一个 `;` 切一次。`mCurrentHyperlinkUri` 作为「当前属性」跨行保持（规范如此，只有空 URI 才结束），`reset()`(RIS) 里清掉。params 里的 `id=` 忽略。URI 里带 `:` 不会破坏 OSC 快速路径（`setOscTypeVariables()` 只影响 `mTerminalControlArgs` 容量，mOscType 非 52/1337 时照常累积）。

**测试**：`./gradlew test` 会在 CI 跑。加了 OperatingSystemControlTest 7 个（含 BEL/ST 两种结尾、params、同行多链接、宽字符两格、跨行保持、ECH 擦除、RIS 清零）、ResizeTest 1 个（reflow 前后链接跟着文字）、TerminalRowTest 2 个（含自拷贝快照）。本地无 JDK/gradle，只能靠 CI 验证。

## 2026-10-18 — M5 实现：IME 收放 extra keys 行 / 手指拖拽上报鼠标拖拽 / DECSET 1004 焦点上报

范围由用户确认（三项各自对应上游 issue）：1) 「键盘收起收行」= 上游 #140/#3263，新增属性 `hide-extra-keys-when-soft-keyboard-hidden`（默认关）+ 设置页开关，IME 隐藏时收起 extra keys 行、弹出时恢复；2) 「拖拽滚轮」= 上游 #1384，新增属性 `touch-drag-sends-mouse-drag`（默认关）+ 设置页开关，鼠标跟踪生效时手指拖拽按鼠标拖拽上报而不是滚轮事件；3) 1004 = 上游 #3650/#3644，用窗口焦点（`TermuxActivity.onWindowFocusChanged`）发 `CSI I`/`CSI O`。用法与限制写进 FORK.md §14。

**1) extra keys 行随 IME 收放**：IME 可见性用已有的 `KeyboardUtils.isSoftKeyboardVisible()`（`WindowInsetsCompat.Type.ime()`，**只有 API 23+ 可用**，helper 注释里就写着 "Does not work on android 7"），所以属性在低版本不生效而不是崩溃。判定时机必须在布局变化时（`TermuxActivityRootView.onGlobalLayout()` 里回调 activity），而全局布局监听器原来只在 `terminal_margin_adjustment` 打开时才注册 → 注册条件改成 `isTerminalMarginAdjustmentEnabled() || shouldHideExtraKeysWhenSoftKeyboardIsHidden()`；另外 `reloadActivityStyling()` 里也重算一次，否则「属性从开改成关」时行不会回来（关掉属性后不再查询 IME，只用 `shouldShowTerminalToolbar()` 恢复显示，也就不需要 layout 事件）。**手动优先**：`toggleTerminalToolbar()` 会置 `mIsTerminalToolbarVisibilitySetManually`，IME 状态变化时清掉——否则属性打开时按工具栏开关会「显示后立刻被收起」，看起来像坏了。用 `Boolean mIsSoftKeyboardVisibleForToolbar`（null=未知）做状态比较，null 与 false 的区别很重要：未知时不判定为「IME 隐藏」，避免启动瞬间把行收掉。

**2) 拖拽上报**：老代码注释里写得很清楚——对手指输入不能只发「移动」事件，因为协议上没有前置的按下事件。所以要发完整的 press(e1) → motion(e2) → release，e1 必须从 `GestureAndScaleRecognizer.onScroll(e1, e2, ...)` 传出来（原来只传 e2），否则按下位置会变成越过 touch slop 之后的位置。**默认关**是刻意的：打开后使用鼠标跟踪的程序就收不到拖拽产生的滚轮事件了（滚动变拖拽），这是个能力替换而不是纯增强，所以留给用户切。长按后（`isAfterLongPress`，手势已归 View/文本选择）不启动上报，`ACTION_CANCEL` 里补发 release；否则程序里鼠标会一直处于按下状态。开启该属性时 `onFling` 不再补发滚轮事件（拖拽结束又滚一下很怪）。状态机放在 `TerminalView`（`mIsMouseDragFromTouchInProgress`），顺手在 release 分支里复位 `scrolledWithFinger`。

**3) 1004**：`sendFocusEvent(boolean)` 不够用——程序可能在 App 启动后才开启 1004，那时若没人再给它发事件，它永远以为自己是聚焦的。所以改成 emulator 里存 `mIsWindowFocused`（默认 true，因为会话都在前台创建）+ `setWindowFocused()`（状态真变化才发，避免重复），并在 `doDecSetOrReset` 的 `case 1004`（DECSET 置位时）立刻上报当前状态，行为对齐 xterm.js #2333。`TermuxActivity.isWindowFocused()` 直接用 `hasWindowFocus()` 而不是自己记字段（onStart 时可能还没收到焦点事件）。会话切换在 `setCurrentSession()` 里给旧会话发 out、给新会话同步状态（只有显示中的会话能有焦点）。

测试：`DecSetTest#testSendFocusEvent`（未开 1004 不发、开启时上报当前状态、状态变化才发、关闭/`reset()` 后不发）。本地无 JDK，靠 CI 的 `./gradlew test`。

## 2026-10-19 — M6 实现：extra-keys-panels 多面板 / 左右滑切会话 / 会话列表 PID·进程·cwd

方案由用户确认：多面板用**新属性** `extra-keys-panels`（三层 JSON：面板数组→行→键），未设置时回退 `extra-keys` 单面板；会话手势用**单指左右滑、默认开**；会话列表显示 **PID/前台命令名 + cwd**；多面板**不加页数指示器**。用法与限制写进 FORK.md §15。

**1) 多面板**：面板就是工具栏 ViewPager 的页，文本输入框排最后（页数 = 面板数 + 1）。实现要点/踩坑：
- `extra-keys` 原本**只在 `TermuxTerminalExtraKeys` 构造时解析**（`setExtraKeys()` 只被构造函数调用），所以 `termux-reload-settings` 改 `extra-keys`/`extra-keys-style` 根本不生效、必须重进 App。多面板必须修这个，所以加了 `reloadExtraKeys()`（比较 `extra-keys-panels`/`extra-keys`/`extra-keys-style` 三个属性拼成的签名字符串，变了才重新解析并返回 true），`TermuxActivity.reloadProperties()` 改成返回该布尔，`reloadActivityStyling()` 里：变了就 `recreateTerminalToolbarViewPagerPages()`（`notifyDataSetChanged()` + 夹紧 currentItem），没变才走原来的「就地 reload 每个面板」。反过来如果不变还去就地 reload，用的会是 `ExtraKeysView.getExtraKeysInfo()`（旧面板）→ 面板内容改了但显示不变。
- `ExtraKeysView.reload()` 现在把 `ExtraKeysInfo` 存下来并暴露 `getExtraKeysInfo()`，这样「只换配色要重建按钮」的就地 reload 不需要额外维护「视图→面板」映射。
- `getItemPosition()` 必须返回 `POSITION_NONE`，否则面板数变化时 PagerAdapter 会保留旧页；为此 `destroyItem()` 里在文本输入页销毁前把已输入文字存回 `mSavedTextInput`（`instantiateItem` 会恢复），不然改属性会把文本框内容吞掉。
- `FullScreenWorkAround.apply()` **不是幂等的**（每次调用都 addOnGlobalLayoutListener），所以只在第 0 个面板调用一次（原来 position==0 只有一页，天然只调一次）。
- CTRL/ALT 等 `SpecialButton` 的激活状态是**每个面板各自一份**，所以 `readExtraKeysSpecialButton()` 改成遍历所有面板取「或」（单个面板时行为不变）。
- 空面板属性（`[]`）当成无效，回退 `extra-keys`；`extra-keys` 也无效才回退内置默认。

**2) 左右滑切会话**：`TerminalView.onFling()` 里判断（此时 `mScroller` 可能还在滚，所以判断放在 `mScroller.isFinished()` 之前）：`|vx| > 2|vy|` 且横移 > 宽度/10 才算，且 `shouldSwitchSessionOnSwipe()` 打开、`!isSelectingText()`、`!isMouseTrackingActive()`。`GestureAndScaleRecognizer.Listener.onFling` 增加 `e1` 参数用于算横移距离。属性 `switch-session-on-swipe` **默认 true**（放在 `TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST`），设置页在「终端视图」；那一页原来是纯 SharedPreferences 的 DataStore，现在 `TerminalViewPreferencesDataStore extends TermuxPropertyPreferenceDataStore` 并按 key 分流（`terminal_margin_adjustment` 继续走 SharedPreferences）。

**3) 会话列表信息**：`/proc/<pid>/stat` 的第 2 个字段（comm）可能含空格且带括号，所以从最后一个 `)` 之后开始按空格切分，此时 `tpgid` 是第 6 个元素（字段 8 = tpgid 的偏移容易算错）。`tpgid <= 0`（进程没接终端）时用 shell 自己。cmdline 用 NUL 分隔、取第一个参数 basename；内核线程 cmdline 为空 → 回退 comm。cwd 用 `/proc/<pid>/cwd` 的 canonical path，`$HOME` 缩成 `~`。任何一项读不到就不显示该项，整行读不到就 GONE 第二行（避免空行留白）。刷新：抽屉打开时 `notifyDataSetChanged()`（`title 变化` 时本来就会刷新，但 cwd 变了标题可能不变）。选中背景（state_activated 选择器）从标题 TextView 移到根布局，否则第二行没有选中高亮。

本地无 JDK，编译与单测靠 CI（`./gradlew test`、Fork Build 的 debug+release）。

## 2026-10-19 — FORK.md 重新定位为特性文档 + 拆出 FORK_BUILD.md（含旧编号映射）

FORK.md 按用户要求重新定位为「**只讲本分支新增/改变的功能与行为**」，非功能内容搬走：

- 构建 / 签名 / 安装 / CI（原 §1-§5）→ 新文件 `FORK_BUILD.md`（**编号不变**，所以该文件内部的 §4.2/§4.4 引用仍有效；`.github/workflows/fork_build.yml`、`.gitignore`、`app/build.gradle` 里的 "See FORK.md" 已改为指向它）。
- 实现细节与「坑」小节（原 §12.4/§12.5）→ 不再重复写在 FORK.md，指向本文档的 #11/#12；各处「实测（真机）」小节合并为 FORK.md 末尾的「验证情况（真机）」一节。
- 新 FORK.md 结构：开头说明 + 「特性总览」表格（10 项，含配置项与默认值）+ §1-§10 逐特性说明 + 验证情况。章节重新编号为 §1-§10（对应关系：旧 §6→§1、§7→§2、§8→§3、§9→§4、§10→§5、§11→§6、§12→§7、§13→§8、§14→§9、§15→§10）。
- **历史条目里的旧编号引用**（本日志 #1-#9 的正文）按下表理解：`FORK.md §4.4` → 现在在 `FORK_BUILD.md §4.4`；`FORK.md §6` → 现 `FORK.md §1`；`FORK.md §9` → 现 `FORK.md §4`；`FORK.md §13` → 现 `FORK.md §8`；`FORK.md §14` → 现 `FORK.md §9`；`FORK.md §15` → 现 `FORK.md §10`。
- `README.md` 的 **Vibe Coding Warning** 里补了一句：本分支新增的功能列在 FORK.md，构建/签名/安装说明在 FORK_BUILD.md（用户要求「提一嘴」）。

另：`DECISIONS.md` 现在有「只追加、禁止直接编辑」的保护（sed/write 被拦），所以本次没有直接改历史条目的编号引用，而是用这条映射说明代替。

## 2026-10-19 — M7 设计：音量键自定义动作（下拉式，单击/长按，复用 extra-keys macro）

范围由用户确认（三项各自对应上游 issue）：原先音量键只有 `volume-keys=virtual`（音量减=Ctrl、音量加=Fn）与 `volume`（完全不接管）两种命运。本次新增**逐键、按手势**指定动作：设置页下拉形态（不是「可增删规则列表」），动作集合覆盖 Ctrl/Fn+音量条、软键盘/extra keys 行、翻页/切会话、发送序列/新会话/粘贴，手势为单击+长按两档，默认保持上游行为并提供「一键恢复默认」。

**属性设计**：4 个动作 key（`volume-up-action` / `volume-up-long-action` / `volume-down-action` / `volume-down-long-action`）+ 2 个序列 key（`volume-up-send-keys` / `volume-down-send-keys`）。动作值走 `MAP_VOLUME_KEY_ACTION` 白名单（复用 `SharedProperties.getDefaultIfNotInMap` 模式：非法值回退默认并 `logErrorOnInvalidValue`），4 个 key 各有独立的 `*InternalPropertyValueFromValue`，为的是日志里 key 名准确。单击/长按两个默认值不同（`DEFAULT_IVALUE_VOLUME_KEY_ACTION`=`default`、`DEFAULT_IVALUE_VOLUME_KEY_LONG_ACTION`=`none`）。

**`default` 的语义冲突（最重要的取舍）**：`default` = 上游修饰键，而修饰键**必须**在 keyDown 立刻生效（`mVirtualControlKeyDown`/`mVirtualFnKeyDown` 要和之后按下的键组合，`readControlKey()` 是在 `TerminalView.onKeyDown/inputCodePoint` 里 OR 进去的）。这跟「单击动作等抬手才执行」天然冲突，所以规则是：`default` 在 keyDown 生效、keyUp 清除，并且长按计时器照常跑；若用户又给它配了非 `none` 的长按动作，按住超阈值会**额外**触发一次长按动作。文档（FORK.md §11）明确写成「想把音量键当稳定 Ctrl 用就把长按留 `none`」，而不是去禁用长按下拉（那会砍掉「Fn + 长按调音量」这类合理组合）。

**长按判定不用计时器**：用 `KeyEvent.getRepeatCount() > 0` 的**首个重复事件**当长按触发点，不自己维护 Handler/延时任务。代价是阈值跟随系统长按超时（约 500ms）且长按只触发一次；收益是没有新增生命周期、旋转/切 App 不会漏掉清理。

**长按位 `none` 不抵消单击**：如果长按位是 `none` 就把单击动作也吃掉，用户配了单击动作、只是按久一点就「毫无反应」，像坏了。所以 `none` 在两个位置的语义不同：长按位 = 长按不额外做事（仍走单击），单击位 = 整键无动作（`mVolumeKeyLongPressTriggered` 只在长按动作非 `none` 时才置位）。

**与 `volume-keys` 的关系**：保留 upstream 的 gate —— `volume-keys=volume` 时 `handleVirtualKeys()` 第一句就 `return false`，自定义动作全部不生效（没有为了「只接管一个键」去改这个语义，那会让「不接管」这个明确选择变得含糊）。这个前提写在设置页「恢复音量键默认行为」的 summary 和 FORK.md 里。

**发送序列复用 extra-keys 的 macro**：给 `ExtraKeyButton` 加一个公开构造器 `(String key, boolean macro, String display)`，然后在 ViewClient 里 `new TerminalExtraKeys(mActivity.getTerminalView()).onExtraKeyButtonClick(null, new ExtraKeyButton(seq, true, seq), null)`。白送 F1~F12/方向键/PGUP/ESC 等键名映射和 `CTRL c` 这类前缀修饰语法，不重新实现一遍键名表；限制（空格分隔、发不了字面空格、修饰键只作用紧随的一个键）写进对话框文案与文档。注意 `onExtraKeyButtonClick` 第一个参数 `view` 与第三个 `button` 在实现里根本没用到，传 `null` 安全。

**设置页序列输入框条件显示**：`OnPreferenceChangeListener` 是在**持久化之前**回调的，所以监听器里不能用 `getValue()` 合成新状态（那时还是旧值）——用「哪个 key 触发的回调」来决定 newValue 属于 `action` 还是 `longAction`，另一项取 `getValue()`。可见性条件是「单击或长按任一为 `send-keys`」。

**「恢复默认」必须落在文件上**：这一页的 DataStore 直接读写 `~/.termux/termux.properties`，所以恢复 = 对 6 个 key 调 `setTermuxPropertyValue(key, null)`（实现是把该行写成 `#key=`，保留注释与其它配置，见 §4/#10），再 `setPreferencesFromResource(...)` 重建界面。重建时 ListPreference/EditTextPreference 会把读到的默认值再 `persistString()` 一次，靠 DataStore 的 `mLastReadValues` 判定成 no-op —— 否则「恢复默认」会立刻把默认值写回文件，失去「未设置」状态。

**未做与待验证**：本地无 JDK/SDK（仓库既有约定：编译/单测/lint 靠 CI），所以本次改动**未经编译**，FORK.md 的「验证情况」一节**故意没有加这一项的实测条目**，等真机跑通再补。也没写单测：状态机落在 `TermuxTerminalViewClient`（要 Activity + TermuxService 的 Robolectric 环境才算得出边界），只测解析层则要给 termux-shared 新加 JVM test 源集，收益不匹配。验证清单：CI `./gradlew test` + `lintDebug`（重点看 MissingTranslation 与资源引用）；真机逐项试「唤醒软键盘 / 调音量 / PageUp / 切会话 / send-keys `CTRL c`」+ 长按 + 「恢复默认」后回到官方 Ctrl/Fn，另需确认 `volume-keys=volume` 下自定义动作确实不生效。

## 2026-10-27 — 终端内边距（terminal-padding-*）+ 本机终于能真编译

**需求**：类似 Windows Terminal 的上下左右自定义 padding。**范围由用户确认**：只缩终端内容区（工具栏/会话抽屉保持通栏）、dp 单位 0–100、入口放「终端视图」页 4 个滑条。

**为什么不改 TerminalView**：它是自绘 View，`setPadding()` 本身不生效；若在 TerminalView 里处理 padding，必须同时改 `updateSize()`（行列）、`onDraw()`（canvas 平移）、`getCursorX/Y`、`getPointX/Y`、`TextSelectionHandleView` 的裁剪、IME 光标位置……漏一个就是「点到的字符和看到的差一格」。所以改成**在 `activity_termux.xml` 里给 TerminalView 套一层 `FrameLayout#terminal_view_container`**，只对容器 `setPadding()`：终端视图坐标体系完全不变（父容器直接缩小它的 bounds），行列数由 `onSizeChanged → updateSize()` 自动减少并给程序发 SIGWINCH；DrawerLayout 的内容子 View 只要 gravity=NO_GRAVITY（FrameLayout 满足），抽屉仍是直接子 View，边缘滑动不受影响。

**背景不用额外处理**：`TerminalRenderer.render()` 并不填背景（只有 reverse video 时 `drawColor`），终端背景一直是 decor view 的颜色（`updateWindowBackgroundColor()`），所以 padding 区域天然同色；**不要**给容器再设一次背景色，否则半透明配色下 padding 会比终端区多叠一层、更亮。

**坑 1**：`res/xml/termux_terminal_view_preferences.xml` 原来没有 `xmlns:android`（此前只用 `app:` 属性），加 `android:min/android:max` 后 aapt2 报 `AttributePrefixUnbound?SeekBarPreference&android:max`。

**坑 2**：`TermuxPropertyPreferenceDataStore` 只实现了 boolean/string，而 `SeekBarPreference` 走 `putInt/getInt`——`PreferenceDataStore` 的默认实现是空操作，值会被静默丢弃。现在 int 支持上移进基类（带 `mLastReadValues` 守卫），`AppearancePreferencesDataStore` 只保留 `terminal-toolbar-height` 的百分数换算特例。

**本机现在可以真编译了（推翻 #13「本机无法编译」，FORK_BUILD.md §6 有完整命令）**：

```sh
export ANDROID_HOME=$PREFIX/opt/android-sdk
./gradlew :app:compileDebugJavaWithJavac -x downloadBootstraps \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2
```

- 本机已有 openjdk-21 + android-sdk（platforms 34/35，构建中自动装了 android-36）+ gradle wrapper 9.2.1。
- `-x downloadBootstraps`：该任务挂在 `javaCompile` 上，会从 GitHub 下 4 个 bootstrap zip；编译/单测不需要，网络差时（实测 `SocketException: Unexpected end of file`）会让整个 graph 失败。
- `-Pandroid.aapt2FromMavenOverride=...`：Maven 的 aapt2 只有 linux-x86_64，arm64 Termux 上 `AAPT2 Daemon startup failed`；用 build-tools 里的本机 aapt2 即可。
- **已知失败**：`app:testDebugUnitTest` 的 `FileReceiverActivityTest` 在本机因 Robolectric teardown 的 `ShadowActivityThread.reset: ActivityThread not set` NPE 失败，单独跑该测试同样失败，与本 fork 改动无关（CI 正常）；`terminal-emulator` 等其他测试全绿。

**未验证**：真机上的 padding 视觉、点击定位、长按选择手柄、软键盘弹出时光标位置。验证清单：4 个方向分别设 8/16/32，看 `tput cols/lines`、点击定位、选择把手、键盘弹出时光标可见。

## 2026-10-27 — 终端内搜索 + OSC 52 读取/开关

**搜索（§15）**：需求是“在回滚历史里查找”。范围由用户确认（终端内搜索 + OSC 52）。实现分三层：

- **匹配**：`TerminalBuffer.findAllMatches(query)` 逐**逻辑行**扫描（用 `getLineWrap(row)` 把自动换行的多行拼成一条），忽略大小写，返回按行/列排序的 `SearchMatch{row,startColumn,endColumn}`；跨行匹配拆成每行一段。
- **坑 1（重要）**：`TerminalRow.clear()` 里 `mSpaceUsed = mColumns`，所以**每一行的 `mText` 都带一整行尾随空格**（测试里 `assertLineIs(1,"ef")` 实际拿到 `"ef  "`）。不裁剪的话查 `"b "` 会命中行尾填充。解决：匹配前把逻辑行尾部的空格裁掉。
- **坑 2**：大小写折叠不能用 `String.toLowerCase()`（某些字符会改变长度，字符下标→列号的映射会错位），改逐字符 `Character.toLowerCase(char)`，长度恒定。
- **坑 3**：宽字符（CJK/emoji）要按 `WcWidth.width(codePoint)` 累加列号；surrogate pair 的两个 char 映射到同一列同一宽度，匹配结束时用 `max(column+width)` 求右边界。
- **渲染**：`TerminalRenderer.setSearchHighlights(rows,startCols,endCols,currentIndex)`；匹配数组已按行排序，render 里用一个前进指针 `while (rows[i] < row) i++` 避免每行全表扫描；高亮矩形画在该行**文字之前**（背景层），几何与单元格背景一致（`y - mFontLineSpacing .. y`）；当前匹配用更深色。颜色是固定半透明橙（`0x66FFC107` / `0xAAFF9800`），深浅主题下都能看清。
- **交互**：初始选中**最后一个**匹配（手机上通常是屏幕上看到的那个）；↑/↓ 循环；`TerminalView.updateSize()`（软键盘弹出、旋转、改列数都会走 reflow）里会重新匹配并尽量把选中项保持在原来的行列附近，否则键盘一弹视图就跳走。
- **UI**：`terminal_search_bar` 作为根 `RelativeLayout` 的最后一个子 View **盖在终端顶部**（不改变终端尺寸 → 不触发 SIGWINCH，也不会打乱行列）；`Ctrl+Alt+F` 或长按菜单 → 搜索打开；返回键先关搜索栏再考虑退出。**坑 4**：`TermuxTerminalViewClient` 的 TerminalView 焦点监听会在失焦时收软键盘，焦点移到搜索框时会立刻把 IME 关掉——监听里补了 `mActivity.isTerminalSearchBarVisible()`，把“搜索栏打开”也当作文本输入视图处理。
- 搜索栏是 `EditText`，与工具栏文本输入页互不影响；切换会话时 `attachSession()` 清空匹配，`setCurrentSession()` 末尾调 `refreshTerminalSearch()` 在新会话重跑一次。

**OSC 52（§16）**：先确认现状——上游**已经支持写入**（`TerminalEmulator.doOsc` 的 `case 52` 调 `onCopyTextToClipboard`），所以这次实际补的是**读取方向 + 开关**，不要重复实现写入：

- 新增属性 `terminal-osc52-clipboard`（`write` 默认 / `read-write` / `off`），设置 → 终端视图 → 剪贴板。`off` 在 app 侧拦 `onCopyTextToClipboard`（emulator 仍会解码，代价可忽略）；`read-write` 才允许读。**默认不允许读取**是刻意的：读取无提示，SSH 到不可信主机时对方能读走剪贴板内容。
- emulator 里 `OSC 52 ; Pc ; ?` → `mSession.getClipboardText()`（null 表示不允许/没有 → 不响应）；有内容则回 `OSC 52 ; Pc ; <base64> ST`（ST 结尾，避免剪贴板里的控制字符破坏序列），选择目标原样回显。响应在 app 侧限制 100KB（与写入上限一致，避免大内容写 pty 卡住）。
- **抽象层次要注意**：emulator 只认识 `TerminalOutput`（测试里是 `MockTerminalOutput`），`TerminalSessionClient` 是 `TerminalSession` 的上层。所以读取入口加在 `TerminalOutput.getClipboardText()`（默认 null，测试可直接覆写），`TerminalSession` 覆写后转给 `mClient`；`TerminalSessionClient` 里用 **default 方法**返回 null，这样 Termux:Float 等其他仓库的 client 即使不重新编译也不会被新抽象方法破坏。
- **坑 5（测试）**：`android.util.Base64` 在 JVM 单测里是空壳（`unitTests.returnDefaultValues` → 返回 null），原仓库的 `disabledTestSetClipboard` 就是因为这个被禁用的。这次写了 `TerminalBase64`（纯 Java，标准字母表、编码不换行、解码忽略空白/容忍缺 padding、非法字符抛 `IllegalArgumentException`），OSC 52 改用它，那个禁用测试也换成了真正跑的 `testSetClipboard`，并新增读取/多目标/非法输入/未授权等用例。

**测试**：`TerminalBufferSearchTest`（9 个：单/多/大小写/不重叠/transcript/换行跨行/CJK/排序/尾部空格）、`TerminalBase64Test`（6 个）、`OperatingSystemControlTest` 新增 5 个 OSC 52 用例；本机 `./gradlew :terminal-emulator:test` 全绿。

## 2026-10-27 — OSC 133 命令标记（跳跃/刻度/失败提示）+ 分屏

**OSC 133 实现取舍**：

- **标记存在 `TerminalRow` 里，不存 buffer 的平行数组**。行对象在 `blockCopyLinesDown()` 里被搬来搬去（环形缓冲区），存在行上就自然跟着滚动走；`TerminalRow.clear()`（行被复用）清零，`TerminalBuffer.clearTranscript()` 丢掉引用即可。行内还存 `mCommandMarkSequence`：`C` 时分配序列号，`D` 时序列号不匹配就说明这一行已经被复用（命令输出比整个缓冲区还长），**不要**把退出码写到别人的行上。
- **锚点是提示符行（`A`），不是输出首行（`C`）**。真实 shell 里回车后光标已经到下一行，`C` 落在输出首行；如果用它做锚点，跳转/刻度会落在 `$ ls` 的**下面一行**。所以 `C` 只是把「当前命令」指向最后一次 `A` 的行（若该行还活着），刻度/跳转都用 `A|C` 位；只有 shell 不发 `A` 时才回退到 `C` 所在行。同一行同时有 A 和 C 时**不给 C 行加位**，否则一条命令会出现两个刻度。
- **不处理 `B`**：它标记提示符文本结束，我们的功能用不到，也就不需要改用户的 `PS1`。
- `D` 不带参数或退出码非法时按 0 处理；没有 `C` 的 `D`（比如空回车）直接忽略。
- reflow（改变列数）在 `TerminalBuffer.resize()` 的逐字符重排里把旧行的 mark 复制到「旧行第一个字符落到的新行」；不改列数的快速 resize 天然保留。已知小缺陷：reflow 时**进行中**命令的行引用会失效（新行对象序列号不同），这一条命令的退出码会丢，可接受。
- 渲染：`TerminalRenderer.render()` 在每行文字画完之后画右边缘竖条（在文字上层），宽度 `min(4px, max(2px, fontWidth/4))`；成功=前景色 40% alpha、失败=`#CCFF5252`、运行中=前景色 20%。触摸区固定 16dp（与绘制宽度解耦），点中附近 ±4 行内的刻度才拦截事件，没有刻度时完全不拦截（不影响右侧选字）。
- **测试**：`CommandMarksTest` 12 个，覆盖 A/C/D、成功/失败、锚点行、滚动后行号变化、D 无 C 忽略、行复用后丢弃、reflow 保留、`reset()` 清空。**坑**：`getTerminalTranscriptRows()` 会把 `< 100` 的 transcript 行数替换成默认值（2000），所以测试行复用必须显式构造 `TerminalEmulator(..., TERMINAL_TRANSCRIPT_ROWS_MIN, ...)` 再写 120 行；一开始按 4 行 buffer 写，标记根本没被回收，D 正确地写到了还在 transcript 里的行上。
- 失败提示会误报 `grep`/`diff`/`test` 这类「非 0 是正常结果」的命令，文档里写明了；没有加开关（只有装了 shell 集成才会有提示）。

**shell 集成为什么这么做**：

- 用 **`PS0`** 发 `C` 而不是 `DEBUG` trap：`PS0` 的展开时机正好是「命令行读入后、执行前」，而且**不会覆盖用户已有的 DEBUG trap**（覆盖 trap 是破坏性的）；代价是 bash 4.4+ 才有，老 bash 只是没有命令开始标记。`PROMPT_COMMAND` 里**前置** `__termux_osc133_prompt` 并在函数末尾 `return $?`，这样用户后面的 PROMPT_COMMAND 项仍然能看到真实退出码；bash 5.1+ 的数组形式 PROMPT_COMMAND 也做了处理（`declare -p` 判断）。
- zsh 用 `add-zsh-hook precmd/preexec`，与用户已有钩子共存，同样 `return $?`。
- 安装器把脚本写到 `~/.termux/shell-integration.{bash,zsh}`，往 rc 文件追加**带标记的块**（幂等、卸载只删块），`~/.zshrc` 只在已存在时写（不给不用 zsh 的人创建配置文件）。不改 `PS1`。
- 本地用 `script -qec "bash --rcfile rc -i"` 跑真实 pty 验证了序列顺序与退出码；zsh 本机没装，只能靠语法审查。

**分屏实现取舍**：

- **每个窗格一个 `TermuxTerminalViewClient` 实例**（构造函数新增 `TerminalView` 参数，39 处 `mActivity.getTerminalView()` 机械替换成自己的 `mTerminalView`）。原因：`TerminalViewClient` 的回调必须作用于「收到事件的那个视图」，共用一个实例在点非活动窗格时会操作错视图。`TermuxTerminalSessionActivityClient` 保持**单例**：它的 `mActivity.getTerminalView()` 语义等于活动窗格，正好是抽屉选中会话、切会话、新建会话想要的目标。
- **活动窗格语义**：`mTerminalView` 字段始终指向活动窗格，`getTerminalView()` 不变，于是搜索、上下文菜单、extra keys（点击时 `TerminalExtraKeys.setTerminalView()` 改投）、音量键、搜索栏、`getCurrentSession()` 等既有代码全部自动跟随活动窗格。
- **点窗格切活动**用 `TerminalView.setOnTouchListener` 在 `ACTION_DOWN` 里切（返回 false 不消费），这样后续的手势回调、长按菜单、IME 都已经在正确的窗格上；没有用 `onSingleTapUp`，因为长按/滑动也要切。
- **焦点监听必须移到每个 client 的 `onCreate()`**：原来它注册在 `setSoftKeyboardState()` 里，而该函数现在会为「非活动窗格」提前 return，导致第二个窗格永远没有焦点监听（点它反而会把键盘关掉）。同时判断里加了 `isAnyTerminalViewFocused()`：焦点从一个窗格移到另一个窗格时，旧窗格的 `onFocusChange(false)` 不能把新窗格刚弹出的键盘关掉。
- **一个会话不能挂在两个视图上**（两边网格尺寸不同会互相 resize）。所以从抽屉里选中「另一个窗格正在显示的会话」时**互换两个窗格的会话**（`swapTerminalPaneSessions()`），而不是让两边显示同一个。
- 布局：`activity_termux.xml` 里 `terminal_panes_container`(LinearLayout) + `terminal_pane_0/1`(FrameLayout) + 6dp 分隔条 + 第二个 `TerminalView`；横竖方向与权重都在 `updateSplitScreenLayout()` 里用 `LinearLayout.LayoutParams` 重建（XML 里的 `height=0dp weight=1` 只适用于竖屏初始状态）。`terminal-padding-*` 现在作用于 `terminal_panes_container`，两个窗格一起内缩。
- 分隔条拖动 40ms 节流（拖动会 resize 两个会话、各发一次 SIGWINCH，不节流会很卡）；比例夹在 20%–80%，横竖切换保留比例。
- 开分屏时第二个窗格优先显示「另一个运行中会话」，只有一个会话才调 `TermuxService.createTermuxSession()` 新建（**不要**用 `addNewSession()`，它会把新会话挂到当前活动窗格，把第一个窗格顶掉）；8 个会话上限沿用 `TermuxTerminalSessionActivityClient.MAX_SESSIONS`（改成 public）。
- 活动窗格边框用 `FrameLayout#setForeground()` + 1dp stroke drawable（`terminal_pane_active_border.xml`），只在分屏时显示，不额外加 View。
- **未持久化**：分屏状态在 Activity 重建（换主题等）后回到单窗格；会话本身都在 `TermuxService` 里不会丢。旋转不受影响（manifest 已声明 `configChanges`，不会重建 Activity）。
- **未验证**：真机上的点窗格切键盘、分隔条拖动、抽屉互换、刻度点击与失败 toast —— 本机只能编译。

## 2026-10-27 — 在本机出 APK：bootstrap 嵌入 + NDK 工具链坑

要给别人/自己装就得 `assembleDebug`，而它比编译多两步坑：

- **bootstrap zip 是嵌入 native 库的**：`app/src/main/cpp/termux-bootstrap-zip.S` 用 `.incbin` 把
  `bootstrap-<abi>.zip` 编进 `libtermux-bootstrap.so`（APK 里 30MB 的那个 .so 就是它）。
  `downloadBootstraps` 一次下 4 个 ABI，失败会让整个 graph 挂；只给自己手机装可以手动下
  `bootstrap-aarch64.zip`（sha256 `ea2aeba8…f4f3`）再 `-x downloadBootstraps`。
- **NDK 必须是 aarch64 能跑的**：Gradle 下的 r29 只有 `toolchains/llvm/prebuilt/linux-x86_64`，
  `ndk-build` 在 `build/tools/ndk_bin_common.sh` 里直接 `ERROR: Unknown host CPU architecture: aarch64`。
  `$PREFIX/opt/android-ndk` 是 r27b 且带 `linux-aarch64`，`ln -sfn` 成
  `$PREFIX/opt/android-sdk/ndk/27.1.12297006`，再用 `-PndkVersion=27.1.12297006` 覆盖
  （`build.gradle` 读的是 `project.properties.ndkVersion`，`-P` 能覆盖，不用改仓库里的 gradle.properties）。
- **只出一个 ABI**：`-Pandroid.injected.build.abi=arm64-v8a`，否则另外 3 个 ABI 缺 zip 会失败。
  这个属性会让产物落到 `app/build/intermediates/apk/debug/` 而不是 `app/build/outputs/apk/`。
- 产物签名是 `testkey_untrusted.jks`（与官方 GitHub/CI testkey 一致），`aapt2 dump badging` 可确认
  `com.termux 118 0.118.0 native-code arm64-v8a`。装机会杀掉当前 Termux 进程（别在会话中途装）。


## 2026-10-27 — 安装器报「该安装包是测试版本，请联系开发者」

用了 `-Pandroid.injected.build.abi=arm64-v8a` 出的 debug 包**带 `android:testOnly="true"`**（AGP 把任何
`android.injected.*` 当成 IDE 部署，给包打上 testOnly），系统 PackageInstaller 会直接拒绝安装。
修法：构建时再加 `-Pandroid.injected.testOnly=false`（实测生效，`aapt2 dump xmltree` 里 testOnly 消失，
`debuggable=true` 保留，签名仍是 testkey `b6da0148…e5e1`）。
验证命令：`aapt2 dump xmltree --file AndroidManifest.xml <apk> | grep -E 'testOnly|debuggable'`。
如果安装报的是签名不一致（而不是“测试版本”），说明已装的是 F-Droid/Play/自己 key 的包，只能卸载重装或换签名。

## 2026-10-27 — 分屏时两个窗格都实时重绘

**问题**：分屏的进程层早就是真并行（每个窗格一个 `TerminalSession`，各自的 reader 线程把输出
append 进各自的 emulator），但 **画面只有活动窗格在刷**：`TermuxTerminalSessionActivityClient.onTextChanged()`
里的守卫是 `if (mActivity.getCurrentSession() == changedSession) getTerminalView().onScreenUpdated()`。
非活动窗格要等你点它（`TerminalSession` 的下一条输出）才刷新，看起来像「后台挂起」。

**为什么不能直接把守卫换成遍历两个窗格调 `onScreenUpdated()`**：那个方法有三个副作用，对非活动窗格都是错的

1. `!skipScrolling && mTopRow != 0` → `setTopRow(0)`：把用户往上翻看的位置**拽回底部**。桌面上
   被遮挡/失焦的窗口不会因为别人的输出改变自己的滚动位置。
2. `mAccessibilityEnabled` 时 `setContentDescription(getText())`：整屏转字符串，每个输出批次一次，
   翻倍的无用开销（TalkBack 只读焦点窗格）。
3. `awakenScrollBars()`：非活动窗格无谓地闪滚动条。

**做法**：`TerminalView` 新增 `onScreenOutput()`
（`terminal-view/.../TerminalView.java`，紧挨 `onScreenUpdated()`）：
只做「`mScrollCounter` 消费 + `mTopRow` 随行数变化平移 + clamp 到 transcript 尾部 + `invalidate()`」。
平移公式与 `onScreenUpdated()` 的「正在选字」分支一致（`setTopRow(mTopRow - rowShift)` +
`decrementYTextSelectionCursors(rowShift)`），所以往上翻看时**内容在视口下滚动、视口里的行不动**；
选中的文本也跟着走。`mTopRow == 0`（在底部）时什么都不用做，行移位天然可见。
**不碰**无障碍描述、不 `awakenScrollBars()`、不 `clearSearch()`。

**分发**：`TermuxActivity.getTerminalViewForSession(session)` 按「视图当前挂的 session 是它」匹配，
分屏关闭时跳过隐藏的 pane 1（否则白算）。活动窗格仍走 `onScreenUpdated()`，语义一字不改。

**节流**：`onScreenOutputThrottled(50)`（`INACTIVE_PANE_REDRAW_INTERVAL_MS`）。为什么还要节流——
两个可见窗格在同一个 window / 同一个主线程里，`invalidate()` 只按帧合并，两边都刷屏时就是每帧
画两次、`TerminalRenderer.render()` 全在主线程，会把活动窗格的按键/滑动挤掉。50ms（≈20fps）在
`top`/`tail -f` 上肉眼与实时无差别，主线程负担减半。0 表示不节流（等价于「每帧都画」），
要改成用户可配就在这里接 `TermuxPropertyConstants`。

**必须补刷的三个时刻**（这三处原来靠「下一次输出」碰巧修好，现在必须显式刷，否则画面停在旧状态）：
`setActiveTerminalPane()`（新活动窗格在非活动期间一直没画）、`swapTerminalPaneSessions()`
（`attachSession()` 在尺寸不变时不会 `invalidate()`）、`setSplitScreen(true)`（pane 1 可能已经跑了很久）、
`onStart()`（回前台时只有活动窗格被刷）。

**不变量**：`mEmulator.clearScrollCounter()` 是 **per-session** 状态，本方案依赖「一个 session
只挂一个视图」（由 `swapTerminalPaneSessions()` 保证）。若以后要做同一会话镜像到两个窗格，
`mScrollCounter` 必须改成 per-view，否则两个视图会互相抢行位移。

**刻意不做**：非活动窗格的光标闪烁（`setTerminalCursorBlinkerState()` 只作用于活动视图，
给非活动窗格也起一个 500ms 定时 `invalidate()` 收益极小）；局部脏行渲染（`TerminalRenderer`
整屏绘制，50/50 分屏的总像素量与单窗格全屏相当，改造收益不匹配风险）。

**未验证**：只通过本地 `./gradlew :app:compileDebugJavaWithJavac :terminal-view:compileDebugJavaWithJavac`
（命令见 FORK_BUILD.md §6）。真机要看：非活动窗格跑 `top -d1` 是否持续刷新且帧率不掉、
`less` 往上翻后不被拽回底部、互换/开关分屏/回前台后画面立即最新、两边同时 `yes` 时活动窗格的
输入延迟（对比把 `INACTIVE_PANE_REDRAW_INTERVAL_MS` 改成 0）。

## 2026-10-27 — 状态栏（`status-bar-*`）

**需求**：像 GNOME 顶栏/任务栏插件那样常驻显示电量、内存、时间、CPU、网络、IP、代理，可配样式、配色、
位置、颗粒度细，配置文件与设置 GUI 都能改。用户确认：占位与覆盖两种都做（默认占位）；网络速率与累计
都支持（默认速率）；点击动作做一组可配（默认 `none`）；图标 + 文字 + 图表。

**为什么 CPU 不用 `/proc/stat` 当唯一来源**：本机实测（Android 13，app 域）`/proc/stat`、
`/proc/loadavg`、`/proc/net/dev`、`/proc/uptime` **全部 `Permission denied`**（Android 9+ 的
procfs 限制），只有 `/proc/meminfo`、`/proc/self/stat` 和 `/sys/devices/system/cpu/*` 可读。所以
`StatusBarMetrics.getCpu()` 三级回退：`/proc/stat` 两次采样 → `cpufreq/policy*/scaling_cur_freq ÷
cpuinfo_max_freq`（频率占用率近似）→ `-`。第一次读失败会把 `mProcStatAvailable` 永久置 false，避免每次
刷新都抛一次异常。同理内存优先 `/proc/meminfo`（`MemTotal - MemAvailable`），`ActivityManager.MemoryInfo`
只作回退；网络用 `TrafficStats`（整机流量，不是本进程）；IP 用 `LinkProperties`（**不读 SSID**，那要
额外申请 `ACCESS_WIFI_STATE`，为一个标签不值得扩权限）。

**配置层为什么不进 `TERMUX_APP_PROPERTIES_LIST`**：那套 `SharedProperties` 走 `getInternalPropertyValue`
的 switch 做类型转换，而状态栏有 ~40 个键、其中 7×8 个是 `status-bar-segment-<id>-<option>` 这种动态
键，逐条注册既啰嗦又容易漏。所以 `StatusBarConfig.parse()` 直接读 `TermuxAppSharedPreferences.getProperties(true)`
返回的原始 `Properties`，自己校验并回退默认值（越界/非法值只记日志，绝不让 App 起不来）。代价是绕过
了共享库的类型检查——用 12 个单测覆盖默认值、越界回退、段顺序/去重/未知名、单测格式与样式、转义、
占位符替换来兜底。

**占位 vs 覆盖**：`status_bar_view` 是 `activity_termux.xml` 里 `drawer_layout` 的**兄弟节点**（放在它
后面，所以天然画在终端之上），`updateStatusBarLayout()` 按 `position` + `overlay` 改
`RelativeLayout` 规则：占位模式把 `drawer_layout` 设成 `BELOW`/`ABOVE` 状态栏（终端行列数随之减少，
会触发 SIGWINCH）；覆盖模式只给状态栏加 `ALIGN_PARENT_TOP`/`ABOVE toolbar` + 4dp elevation，
`drawer_layout` 规则保持不变。底部非覆盖 && 工具栏被隐藏时，`RelativeLayout` 会跳过 GONE 的锚点，
状态栏落到底部，行为仍然合理。

**配色联动**：颜色值支持 `theme:<name>`，直接取 `TerminalColors.COLOR_SCHEME.mDefaultColors[]`
（`background`/`foreground`/`cursor`/`colorN`，N 为 0–255），因此 `colors.properties` 一变、
`TermuxActivity.updateExtraKeysViewColors()` 被调用时，状态栏也一起 `reloadColors()` 重取。
`ColorPreference` 原本只认 `#...`（`Color.parseColor` 校验），现在额外接受 `theme:xxx`（用
`StatusBarColors.isValidThemeColorReference` 校验名字），否则 GUI 里根本没法输入配色方案颜色。

**分段刷新而不是一个 `Runnable` 刷全部**：时钟 1s、CPU/网络/内存 2–3s、电量 10s、IP/代理 5s，
统一 tick 200ms 检查 `nextRefreshTime`。Activity 不可见（`onStop`）就 `removeCallbacks`，避免息屏后
还在读 battery broadcast / 枚举网卡。CPU 与网络速率需要两次采样，所以 `reload()` 时先 `prime()`
一次，否则首屏 2 秒内显示 `-`。

**图表**：`SparklineView` 用固定长度环形缓冲 + `Path` 画折线与半透明填充，`setRange(0, 0)` 表示按样本
自动缩放（网络速率用，百分比段固定 0–100）。不给它加动画/握手势，避免每个状态栏刷新都触发额外重绘。

**已知不做**：不在每个分屏窗格各画一条（状态栏是窗口级的）；不做 SSID/信号强度（`ACCESS_WIFI_STATE`）；
不做点击弹详情面板；不读 shell 的 `http_proxy` 环境变量（那属于会话状态，不是设备状态）。

**未验证**：只通过本地 `./gradlew :app:assembleDebug`（含 aapt2 资源编译）+ `:app:testDebugUnitTest
--tests "*StatusBarTest"`（12 个用例）。真机要看：两种位置的布局与 `tput lines` 变化、设置页逐项改完
是否立即生效、`theme:` 配色跟随 Termux:Styling、CPU 回退值是否与实际负载相关、长期刷新耗电。

## 2026-10-27 — 修状态栏导致的启动崩溃（InflateException）

**现象**：装上带状态栏的包后一进 App 就闪退。

**日志**（`logcat -b crash`）：

```
java.lang.RuntimeException: Unable to start activity ComponentInfo{com.termux/com.termux.app.TermuxActivity}:
  android.view.InflateException: Binary XML file line #156 in com.termux:layout/activity_termux:
  Error inflating class com.termux.app.statusbar.StatusBarView
Caused by: java.lang.NoSuchMethodException:
  com.termux.app.statusbar.StatusBarView.<init> [class android.content.Context, interface android.util.AttributeSet]
```

**原因**：`activity_termux.xml` 里加了 `<com.termux.app.statusbar.StatusBarView .../>`，但 `StatusBarView`
只写了 `(Context)` 一个构造函数。`LayoutInflater` 是**反射取 `(Context, AttributeSet)`** 来创建 XML 里的
View 的，于是 `setContentView()` 直接抛 `InflateException`。编译器不会报这种错（XML 里的类名只是字符串），
`assembleDebug` 也照样成功。

**修复**：补 `(Context, AttributeSet)` 与 `(Context, AttributeSet, int)` 构造函数，并去掉原来为了压 lint
而加的 `@SuppressLint("ViewConstructor")`（那个注解本身就是「只有编程式构造函数」的许可证，等于把
lint 的告警关掉了——正是它本该拦下的问题）。

**防回归**：新增 `CustomViewConstructorTest`（纯 JUnit，反射 `getConstructor(Context.class,
AttributeSet.class)`），覆盖 `StatusBarView` / `SparklineView` / `TerminalView` / `TermuxActivityRootView`。
已实测把构造函数删掉时该测试会失败。这类检查必须用反射而不是实例化，因为不需要 Android 运行时，
也不会触发 Robolectric 在本机的 teardown NPE。

**教训**：往布局 XML 里加自定义 View 时，`(Context, AttributeSet)` 构造函数是硬要求；lint 的
`ViewConstructor` 警告不要用 `@SuppressLint` 压掉，那是设计意图不匹配的信号。

## 2026-10-27 — 状态栏"配了也不显示"：属性被 propertiesList 过滤掉了

**现象**：修好构造函数崩溃后，`termux.properties` 里 `status-bar-enabled=true`、`status-bar-segments`
写得完全正确，但状态栏就是不出现，改任何配置都没反应。

**排查**：直接读用户真实配置文件跑解析逻辑（javac 编译 `StatusBarConfig.parse` + 用
`SharedProperties.getPropertiesFromFile()` 喂文件），结果是 `enabled=true`、5 段——说明**解析没问题**，
问题在"喂给解析器的 `Properties` 对象"。

**根因**：`SharedProperties.loadPropertiesFromDisk()` 只把 **`mPropertiesList` 里的键**放进内存缓存
（源码里那句注释就写着 "We only store values in-memory defined by propertiesList"）：

```java
for (String key : propertiesList) { ... if (putToMap(...)) putToProperties(newProperties, key, value); }
mProperties = newProperties;
```

而 `getProperties(true)` 返回的正是这个被过滤的 `mProperties`。我当时**故意没**把 `status-bar-*` 加进
`TERMUX_APP_PROPERTIES_LIST`（因为 7×8 个 `status-bar-segment-<id>-<option>` 是动态键，逐条注册既啰嗦
又要给 `getInternalPropertyValueFromValue` 加 case），于是缓存里**一个 `status-bar-*` 键都没有** →
`StatusBarConfig.parse()` 全部走默认值 → `enabled=false` → 视图永远 `GONE`。文件是对的，运行时看不到。

顺带解释了一个观察：设置页里显示的值是**对的**，因为 `TermuxPropertyPreferenceDataStore` 走的是
`TermuxSharedProperties.getTermuxPropertyValue(context, key)` → 直接读文件，绕过了缓存；而
`StatusBarController` 读的是缓存。

**修复**：`StatusBarController.reload()` 改为 `sharedProperties.getProperties(false)`（直接读文件）。
`getPropertiesFromFile()` 返回**全部**键（只跳过 `preProcessPropertiesOnReadFromDisk`，那只影响
`extra-keys` 一类需要预处理的老键）。reload 只在 Activity 创建和样式重载时发生，多读一次文件的代价可忽略。

**同时修的另一个布局 bug**：`status-bar-position=bottom` 时把状态栏锚到
`terminal_toolbar_view_pager`，但该 ViewPager 在 `hide-extra-keys-when-soft-keyboard-hidden` 下是
`GONE`，而 `RelativeLayout` 会**丢弃锚点是 GONE 的规则** → 状态栏跑到窗口顶部（或位置错乱）。
现在按工具栏当前可见性选择：可见就 `ABOVE toolbar`，不可见就 `ALIGN_PARENT_BOTTOM`；并在
`setTerminalToolbarVisibility()` 与 `onStart()` 里重新应用规则。

**防回归**：新增 `StatusBarPropertySourceTest`，扫描 `StatusBarController.java` 断言用的是
`sharedProperties.getProperties(false)` 而不是 `(true)`。这种"源码扫描测试"不常见，但该回归在纯 JVM
测试里无法观测（要一个真实 `Context` 才能构造 `SharedProperties`），而失败模式又是静默的、只在运行时
表现为"配置被忽略"，所以用一条显式断言锁住它是值得的。

**教训**：在 Termux 里读 `termux.properties` 的自定义键，**不要**用 `getProperties(true)`（缓存只含
`TERMUX_APP_PROPERTIES_LIST`）；要么用 `getProperties(false)`，要么把键注册进那个列表并在
`getInternalPropertyValueFromValue` 里加 case。

## 2026-10-27 — 状态栏布局重构：内容区 + padding 占位

**问题**（真机反馈）：① 占位模式没有减少终端行数，反而把终端内容挤到软键盘区域；② 状态栏的层级比
会话抽屉还高，拉开侧边栏时被状态栏压住。

**原因**：状态栏原来是 `activity_termux_root_relative_layout` 的**兄弟节点**，靠 `RelativeLayout`
的 `BELOW`/`ABOVE` 规则去挤 `drawer_layout`。这套规则有三个结构性缺陷：
1. 锚点可能是 `GONE` 的（`terminal_toolbar_view_pager` 在 `hide-extra-keys-when-soft-keyboard-hidden`
   下会隐藏），而 `RelativeLayout` 会**丢弃锚点为 GONE 的规则**，位置随机化；
2. 层级由「XML 顺序 + elevation」决定，状态栏写在 `drawer_layout` 之后，所以它盖在抽屉上；
3. `drawer_layout` 高度是 `match_parent`，用 `BELOW` 改它的上边界时，终端是否会真正 resize 依赖
   测量顺序，软键盘弹出时容易和 `TermuxActivityRootView` 的 margin 补偿打架。

**改法**：把状态栏搬进抽屉的**内容区**，并让「占位」通过 padding 实现：

```
DrawerLayout
├── FrameLayout#terminal_content_container      ← 内容区（抽屉只覆盖这里）
│   ├── LinearLayout#terminal_panes_container   ← 终端，padding 里含状态栏高度
│   └── StatusBarView                           ← 最后一个子 View，overlay 时自然在最上层
└── LinearLayout#left_drawer                    ← 抽屉，永远画在内容区之上
```

* 状态栏由 `StatusBarView.setConfig()` 用 `FrameLayout.LayoutParams(MATCH_PARENT, heightDp,
  TOP|BOTTOM)` 定位，高度/边缘全部来自属性；
* 占位模式：`TermuxActivity.setTerminalPadding()` 在 `terminal-padding-*` 之上**再加**
  `status-bar-height`（按 position 加到上或下），`TerminalView` 的尺寸因此真的变小 →
  `onSizeChanged` → `updateSize()` → 行列减少 + `SIGWINCH`；
* 覆盖模式：不改 padding，只给状态栏 4dp elevation，浮在终端上；
* 不再需要任何 `RelativeLayout` 规则、也不需要监听工具栏可见性（状态栏在内容区里，天然位于工具栏
  上方），`isTerminalToolbarVisible()` 与相应的钩子一并删掉；
* 层级问题直接消失：抽屉是 `DrawerLayout` 的 drawer 子 View，永远在内容区之上。

**顺带的好处**：软键盘弹出/收起（`adjustResize` + `TermuxActivityRootView` 的 margin 补偿）、旋转、
分屏、改 padding 都只是在改内容区大小，状态栏高度用 padding 表达，所以**全部自适应**，不需要额外的
刷新逻辑。

**取舍**：占位模式下左右 `terminal-padding-*` 区域会被状态栏横跨（状态栏是内容区全宽），如果希望
状态栏也内缩，可以把 padding 放到状态栏自己身上——目前没做，因为大多数配色下看不出来。

**未验证**：只通过本地编译 + 单测。真机要看：占位模式 `tput lines` 少 1 行、软键盘弹出时底部
不再压到键盘区、bottom+overlay、拉开抽屉时状态栏被抽屉盖住。

## 2026-10-19 — M8.1 通知卡片渲染层：浮层位置、宽度语义与三个自己踩的坑

**范围**：只做渲染层（模型 + 浮层 + 卡片 + 配色 + 布局接入 + 测试入口），数据源（退出码/OSC/事件）在后面里程碑接。

**决策**：
1. 浮层放 `terminal_content_container`（与 `status_bar_view` 同层、且在它之后 → 画在状态栏之上），用 `setStatusBarInsetPx()` 让开状态栏高度。**不改 `terminal_panes_container` 的 padding**，所以终端行列数不变、不会给程序发 `SIGWINCH`（和搜索栏同样的理由）。
2. 浮层 View 自己 `setClickable(false)` / `setFocusable(false)`：`ViewGroup.dispatchTouchEvent()` 在子 View 返回 false 时会把事件继续派发给**同层下一个兄弟**（终端视图），所以除了卡片区域之外的触摸全部穿透，不需要额外算裁剪区域。
3. 模型 `TerminalNotification` 放 `terminal-emulator` 模块：OSC 解析器（`TerminalEmulator`）在那一层，app 反向依赖不了。模型里不放 Bitmap/View，纯数据，单测友好。
4. `notification-*` 复用 `StatusBarColors.resolve()`（支持 `theme:color1` 这类引用），所以配色跟 Termux:Styling 联动零成本。默认背景不是直接用配色 `background`，而是 `blendColors(bg, fg, 0.12)`——否则深色主题下卡片和终端背景同色，只剩阴影能看出来。

**坑（都已修）**：
1. **`enforceMaxVisible()` 死循环**：卡片被 `dismiss()` 后要等 150ms 动画结束才真正 `removeView`，`while (childCount > max) dismiss()` 会因为「已经 dismiss 的卡片还在」而永远减不下去 → 主线程卡死。改成先收集「未处于 dismissing 状态」的卡片列表、按位置算出多余条数、逐条 dismiss；卡片上加 `isDismissing()`。
2. **`notification-width` 只加 max 约束不生效**：`onMeasure` 里把 AT_MOST 收窄成 `makeMeasureSpec(max, AT_MOST)` 只限制上限，内容窄的时候卡片还是窄的。要区分「固定宽度」用 `EXACTLY`、「自适应」用 `AT_MOST`，所以 `NotificationCardView.setCardWidth(fixedPx, maxPx)` 两个参数。
3. **色条方角戳出圆角**：4dp 色条是矩形、卡片背景是 8dp 圆角矩形，色条四角会盖在圆角外面。用 `setClipToOutline(true)`（`GradientDrawable` 提供圆角 outline）裁掉，不要为此新画一个 shape drawable。

**其它注意**：
* 自定义 View 必须有 `(Context, AttributeSet)` 构造函数，否则 `setContentView()` 抛 `InflateException`；`CustomViewConstructorTest` 里已加 `NotificationOverlayView`。
* `notification_meta_separator` 写成 `\u00b7` 而不是 `" · "`，避免依赖 aapt 对首尾空格的处理；空格在 Java 里拼。
* 长按菜单里的「发送测试通知」只在 `notification-enabled = true` 时出现，避免默认配置下污染菜单。

## 2026-10-19 — 本机现在能编译了：JDK17 + Android SDK + aarch64 aapt2 覆盖

之前记录的「本机无法编译，验证只能靠 CI」已经过时（见上方同名条目）。现在本机装了
`openjdk 17.0.21`（`$PREFIX/lib/jvm/java-17-openjdk`）、`$ANDROID_HOME=$PREFIX/opt/android-sdk`
（platforms 34/35/36、build-tools 35.0.0、ndk 29），Gradle 9.2.1 wrapper 能直接跑。

**关键坑**：AGP 从 maven 拿的 `aapt2` 是 linux-x86_64 静态二进制，在 aarch64 上直接
`Syntax error: ")" unexpected`，导致 `:app:processDebugResources` 失败。本机 `pkg install aapt2`
装的是原生 aarch64 版（`$PREFIX/bin/aapt2`），用 AGP 的覆盖开关指过去即可：

```properties
# ~/.gradle/gradle.properties（用户级，别写进仓库，否则 CI 的 x86_64 会挂）
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

配好后 `./gradlew :app:compileDebugJavaWithJavac` / `:app:testDebugUnitTest` 都能跑（几十秒）。
aapt2 会往 stderr 刷一堆 `No package ID 7f found for resource ID 0x7f...`，那是无害噪音，用
`grep -v "No package ID"` 过滤掉再看结果。

**仍然做不到**：`assembleDebug/Release` 会卡在 `configureNdkBuildDebug`——NDK 的 host 工具链只有
linux-x86_64 预编译，aarch64 上跑不了。所以出 APK 还是得靠 CI；本地只能做「Java 编译 + 单测 +
资源处理」这一层。

**本机已知失败**：`app` 的 `FileReceiverActivityTest > testIsSharedTextAnUrl` 在 Robolectric 的
`ShadowActivityThread.reset: ActivityThread not set` 上挂掉，与本 fork 的改动无关（该文件是上游文件、
未修改），是 Android 上跑 Robolectric 的环境问题，CI 的 x86_64 环境不复现。

## 2026-10-19 — M8.2 失败命令通道：命令行上报格式、锚点行与序列号跳转

**上报格式**：`OSC 133 ; D ; <退出码> ; <命令行>`（D 的第二个参数）。选它而不是新开一个 OSC 号，
是因为其它终端只取 `D;<code>` 后面的参数会忽略、tmux 一类的 passthrough 也不会因此报错，而且不用
改 `133` 的语义（A/B/C/D 四种标记不变）。命令行里允许出现 `;`——解析时取「第二个分号之后的全部」。

**为什么不让 shell 发 `133;B`（提示符结束）**：发了就能精确切出命令行，但要改 `PS1`（B 必须打印在
提示符末尾），而 §17 明确承诺「不修改 PS1」。所以命令行以 shell 上报为主，缺失时用缓冲区回退。

**缓冲区回退**：命令锚点行 = `COMMAND_MARK_PROMPT_START` 所在行（`markCommandStart()` 会优先锚到
提示符行），它的文本 = 提示符 + 命令行，用「提示符结尾」启发式截断（`$ ` `# ` `% ` `❯ ` `➜ ` `λ `
`» ` `> `，只在行首 120 字符内认）。这是 best-effort，可能带残留，文档里写清楚了。

**耗时与序列号**：`markCommandStart()` 记 `System.nanoTime()`（**不用** `SystemClock.uptimeMillis()`
——单测是纯 JVM，`android.os.SystemClock` 只会返回 0）。`markCommandFinished()` 里会把运行态清掉，
所以 `TerminalEmulator` 必须**先**读 `getRunningCommandDurationMs()` / `getRunningCommandSequence()`
再调 `markCommandFinished()`，否则拿到 -1 / 0。

**点卡片跳转**：不用「上一条命令」（用户可能已经又跑了别的命令），而是把命令的
`mCommandMarkSequence` 随结果带出去，点击时 `TerminalView.scrollToCommandSequence(seq)` 线性扫
`-activeTranscriptRows..rows` 找同行序列号。序列号是 buffer 内的自增 int，行被回收就不会再匹配，
所以扫不到时退回 `scrollToPreviousCommand()`。

**向后兼容**：`TerminalSessionClient` 新增
`default onCommandFinished(session, TerminalCommandResult)`，默认实现转调旧的
`onCommandFinished(session, int)`，所以 Termux:Float 等插件不改也能跑；`TerminalOutput` 里同样加了
重载（测试夹具 `MockTerminalOutput` 因此不用改）。

**shell 侧成本**：bash 取命令行走 `$(HISTTIMEFORMAT= builtin history 1)`，每次提示符**多一个
subshell**（约 0.5–1ms），可接受但别再往里加 fork。zsh 用 `preexec` 把 `$1`（用户敲的原始文本）
存进全局变量、`precmd` 直接打印，**零 fork**。控制字符统一 `${var//[[:cntrl:]]/ }` 替换成空格，
否则 BEL/ESC 会截断 OSC 序列，多行命令也会把卡片撑乱。

**升级提示**：脚本里写了 `__TERMUX_OSC133_VERSION=2`，`TermuxShellIntegration.isInstalledVersionOutdated()`
读已安装的 `~/.termux/shell-integration.bash` 判断，设置页会把「安装 shell 集成」的 summary 换成
「已安装的 shell 集成是旧版本」。没有这个提示的话，用户升级 App 后会以为功能坏了（卡片只有退出码）。

**本地验证**：`CommandMarksTest` 21 个用例（含 D 参数解析、耗时/序列号、缓冲区取命令行、滚动后仍能
找到）、`ExitCodeNotifierTest` 10 个（提示符截断、控制字符清理、忽略名单、耗时格式化）、
`NotificationConfigTest` 10 个，全部通过；bash 集成用 `bash --norc -i` 喂命令实测
`D;127;nonexistent_cmd_xyz` 正确。

## 2026-10-19 — 坑：卡片高度撑满终端（`match_parent` 子 View 在 AT_MOST 下取满可用高度）

真机装上第一版后，通知卡片**直接占满整个终端高度**。根因不是浮层或卡片容器，而是卡片里用
「左侧 4dp 色条」这个子 `View` 实现的装饰：

```xml
<!-- 等价于代码里的 new LayoutParams(dp(4), LayoutParams.MATCH_PARENT) -->
```

卡片自己是 `wrap_content`，父级（垂直 LinearLayout）给它的高度规格是 `AT_MOST`；`FrameLayout`
把 `MATCH_PARENT` 子 View 的规格也算成 `AT_MOST`，而 `View.getDefaultSize()` 在 `AT_MOST` 下会
**返回 specSize（整个可用高度）**，于是色条撑满 → 卡片跟着撑满。经典陷阱，且 `wrap_content` 的
父级越多越容易踩。

**修法**：色条不再用子 View，改成卡片 `onDraw()` 里 `canvas.drawRect(0, 0, accentWidth, getHeight())`，
完全不参与测量；内容区左内边距 = accentWidth + 10dp，在 `setCardStyle()` 里动态设。

其它同类风险的写法（以后别用）：wrap_content 容器里放 `match_parent` 的纯装饰 `View`（分隔线、
色条、背景块）。要么用 `drawable`/`onDraw` 画，要么给它固定高度。

## 2026-10-19 — 通知卡片默认改成 TUI 像素风（真机第一版反馈）

真机第一版（圆角 8dp + 6dp 阴影 + 混 12% 背景）被否，用户要「TUI / 直角像素风」。改成：

* `notification-corner-radius = 0`、`notification-elevation = 0`、`notification-border-width = 1`
  （`GradientDrawable.setStroke(1dp, accent)`）、`notification-accent-width = 4`、`notification-monospace = true`、
  `notification-body-max-lines = 2`。样式全部由 `NotificationCardView.Style` 传入，不写死在 View 里。
* 背景混色比例 0.12 → 0.08（有描边区分了，不需要那么亮）。
* 卡片外观参数都做成了属性（上面 6 个键），M8.3 的设置页会直接暴露它们。

真机已验证（注入 `OSC 133;D;2;make -j8` 到会话 pty）：内容正确、点卡片能跳到该命令并关闭卡片、
位置在 overlay 状态栏下方不遮挡；**但高度撑满**（见上一条，已修）。

