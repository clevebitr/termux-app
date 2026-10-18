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
