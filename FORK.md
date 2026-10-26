# Fork 特性说明

本仓库是 [Termux](https://github.com/termux/termux-app) 的 fork。本文档**只列出本分支新增或改变的功能与行为**，
以及怎么配置它们。

* **怎么编译 / 签名 / 和官方插件共存**（`sharedUserId` 硬约束）→ [`FORK_BUILD.md`](FORK_BUILD.md)
* **怎么编译 / 签名 / 和官方插件共存**（`sharedUserId` 硬约束）→ [`FORK_BUILD.md`](FORK_BUILD.md)
* 每节末尾的「实现」一行指出该特性改动了哪些上游文件；详细的设计取舍与踩坑记录在本地开发日志里，
  **不随仓库发布**。

配置项都写在 `~/.termux/termux.properties`（改完执行 `termux-reload-settings`），其中大部分也能在
**设置 → Termux** 里直接改（见 §4、§7）。新增属性一律**默认关闭或保持上游行为**，只有
`switch-session-on-swipe`（§10.2）默认开启。

## 特性总览

| § | 特性 | 配置 | 默认 |
| --- | --- | --- | --- |
| 1 | Gboard 等输入法可直接用中日韩键盘 | `gboard-cjk-workaround` | 关 |
| 2 | extra keys 行配色跟随 Termux:Styling 主题 | `extra-keys-foreground/background/…`（设置页有 22 色色板） | 跟随配色方案 |
| 3 | 整个 App 跟随配色方案（含状态栏/导航栏图标明暗） | `night-mode=color-scheme` | `system` |
| 4 | 输入法 / 虚拟键盘配色的图形化设置页 | 设置 → Termux → 终端键盘 | — |
| 5 | 虚拟键盘激活态高亮 + 简体中文界面 | `extra-keys-active-foreground/background` | 开 |
| 6 | 环境备份 / 恢复（`termux-backup` / `termux-restore`） | 设置 → Termux → 备份与恢复 | — |
| 7 | 外观页（主题/全屏/工具栏）+ 按键行为 + 鼠标点按唤键盘 | `mouse-tap-shows-keyboard` 等 | 关 |
| 8 | 真斜体字体 + OSC 8 超链接可点 | `~/.termux/font-italic.ttf`；`terminal-onclick-url-open` | 无斜体字体则合成斜切；点击关 |
| 9 | IME 收起额外按键行 / 手指拖拽按鼠标拖拽上报 / 焦点上报（`DECSET 1004`） | `hide-extra-keys-when-soft-keyboard-hidden`、`touch-drag-sends-mouse-drag` | 前两项关；1004 由程序请求 |
| 10 | 多面板 extra keys / 左右滑切会话 / 会话列表显示 PID·进程·cwd | `extra-keys-panels`、`switch-session-on-swipe` | 面板需自行配置；滑切默认开 |
| 11 | 音量键自定义动作（单击 / 长按，含唤醒软键盘等） | `volume-up-action`、`volume-up-long-action`、`volume-down-action`、`volume-down-long-action`、`volume-up-send-keys`、`volume-down-send-keys` | 保持上游：音量减=Ctrl、音量加=Fn |
| 12 | 额外按键右上角显示上滑备用键 | `extra-keys-popup-hints`（设置页有开关） | **开**（唯一一个默认打开的新属性，见 §12 说明） |
| 13 | Gboard 风格的按键块背景（圆角块 + 2dp 间隙） | `extra-keys-tile-background`；颜色 `extra-keys-tile-background-color` / `extra-keys-tile-active-background-color`（设置页有开关 + 调色板） | **开**，颜色跟随配色方案 |

---

## 1. Gboard 相关配置（本次补丁附带的选项）

`~/.termux/termux.properties`（改完执行 `termux-reload-settings`）：

```properties
# 允许 Gboard 在终端视图里直接使用中文/日文/韩文键盘（本 fork 新增）
# 原理：终端视图默认用 InputType.TYPE_NULL，Gboard 会把键盘强制成英文 QWERTY 并隐藏语言切换键；
# 打开后改用 text class inputType，并监听 IME 语言切换（requestCursorUpdates）重启输入，
# 同时用 TYPE_TEXT_VARIATION_NORMAL 让 Gboard 显示候选词。
# 副作用：非 CJK 语言会退化为 visible-password 风格输入（无联想/候选），与
# enforce-char-based-input=true 类似；依赖 Gboard 的行为，其他输入法表现未充分验证。
gboard-cjk-workaround=true

# 部分三星键盘等需要字符级输入时打开
#enforce-char-based-input=true

# 某些 ROM 上 ctrl+space 切换输入法失效时的 workaround
#ctrl-space-workaround=true

# keyboard 开关按钮的行为：show/hide（默认）或 enable/disable
#soft-keyboard-toggle-behaviour=show/hide

# 建议在 extra-keys 里保留 KEYBOARD 键，方便在 TUI 开了鼠标跟踪、点击不弹键盘时手动唤起
#extra-keys=[['ESC','/','-','HOME','UP','END','PGUP','KEYBOARD'],['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]
```

排障要点：

* 点击不弹键盘：TUI 开了鼠标跟踪时设计上就不弹（`TermuxTerminalViewClient.onSingleTapUp`），
  用 `KEYBOARD` 键 / `Ctrl+Alt+K` 手动唤起；键盘处于 disabled 状态时同样点击无效。
* `soft_keyboard_enabled_only_if_no_hardware` + `KeyboardUtils.isHardKeyboardConnected()` 误报
  会导致键盘被禁用。
* 日志：`logcat -s TermuxTerminalViewClient:V TerminalView:V`（需在 Termux 设置里把日志级别调成
  verbose）。

---

## 2. 虚拟键盘（extra keys 行）配色：与 Termux:Styling 主题联动

> 先明确一下“虚拟键盘”：这里指的是 **Termux 自己画的那一行功能键**（`ExtraKeysView`，
> ESC/CTRL/ALT/HOME/方向键……，也就是常说的“额外按键行/虚拟键盘”）。系统输入法（Gboard、
> 搜狗等）的外观由输入法自己控制，第三方 App 无法改——能改的只有这一行。

### 联动规则（本 fork 新增）

`~/.termux/colors.properties` 里的主题色现在也会决定 extra keys 行的配色：

| extra keys 颜色 | colors.properties 里的键 | 未定义时的默认值 |
| --- | --- | --- |
| 文字色 | `extra-keys-foreground` | `foreground` |
| 背景色 | `extra-keys-background` | `background` |
| 按下/激活文字色（CTRL/ALT 等） | `extra-keys-active-foreground` | `cursor` |
| 按下/激活背景色 | `extra-keys-active-background` | `background` 混入 25% `foreground` |

所以在 **Termux:Styling 里换主题就会同时换掉键盘配色**（Styling 写的就是 colors.properties），
浅色主题不会再出现“白色终端 + 黑色键盘”的割裂感。主题里的 `foreground`/`background`/`cursor`
是所有官方主题都定义了的键（例如 `base16-3024-light` → 键盘变成 `#f7f7f7` 底 + `#4a4543` 字）。

### 自定义（优先级更高、且不会被 Styling 覆盖）

Styling 每次应用主题都会重写 `colors.properties`，所以想要固定/自定义的键盘配色，请写进
`~/.termux/termux.properties`（同名键，支持 `#RGB` / `#ARGB` / `#RRGGBB` / `#AARRGGBB`，
带 alpha 可以做半透明）：

```properties
extra-keys-foreground=#e0e0e0
extra-keys-background=#80000000        # 半透明黑，能看到后面的终端背景
extra-keys-active-foreground=#ff8800
extra-keys-active-background=#33ffffff
```

改完执行 `termux-reload-settings` 生效。

也可以在 `colors.properties` 里直接写这四个 `extra-keys-*` 键（格式同该文件的其他颜色：
`#rgb` / `#rrggbb` / `rgb:xx/xx/xx`，该文件不支持 alpha），这样它们会随主题一起切换；
只是 Termux:Styling 换主题时会被覆盖。

### 整条底部工具栏的背景也跟着走

`extra-keys-background` 不只影响按键，还会应用到承载它的整个底部工具栏条（`ViewPager`，
包括左滑出来的文本输入框那一页——它的背景原本在布局里写死为 `@color/black`，文字色写死为白色）。
文本输入框的文字色现在会跟着 `extra-keys-foreground` 走，所以浅色主题下不会出现
“黑色条 + 白色字看不见”的情况。

### 想固定成以前的深色键盘（不跟主题变）

```properties
extra-keys-background=#ff000000
extra-keys-foreground=#ffffffff
extra-keys-active-foreground=#ffff5252
extra-keys-active-background=#ff9e9e9e
```

（这四个值就是原来主题里 `extraKeysButton*Color` 的取值：白字 / 黑底 / 红字 / 灰底。）

### 实现位置

* `terminal-emulator/.../TerminalColorScheme.java`：解析 colors.properties 里的四个
  `extra-keys-*` 键，未定义的从 color scheme 派生（`blendColors()` 做混合）。
* `termux-shared/.../TermuxPropertyConstants.java` + `TermuxSharedProperties.java`：
  termux.properties 的同名键（覆盖项）。
* `app/.../TermuxActivity.java`：`setExtraKeysViewColors()` 解析覆盖项 →
  `ExtraKeysView.setButtonColors()`；`checkForFontAndColors()`（colors.properties 变更时）和
  `reloadActivityStyling()`（termux.properties 变更时）都会重新应用并重建按键。
* `termux-shared/.../ExtraKeysView.java`：`reload()` 时立即应用背景色（原本只有触摸时才应用），
  并保留 CTRL/ALT 已激活时的文字色。

注意：Termux:Float 用的是自己的一套属性/颜色处理，本次没有把联动接过去；它的 extra keys 行只
受上面 `ExtraKeysView` 的行为调整影响。

---

## 3. 整个 App 跟随主题（`night-mode=color-scheme`）+ 状态栏/导航栏同步

之前只有 extra keys 行跟着 `colors.properties` 走，App 的浅/深色主题还是由 `night-mode=system`
决定。现在 `night-mode` 多了一个取值：

```properties
# ~/.termux/termux.properties
night-mode=color-scheme
```

* 按 `colors.properties` 的 `background` 亮度决定（阈值 **130**，与光标色自适应用的是同一个阈值）：
  亮 → 用浅色主题，暗 → 用深色主题。所以 **在 Termux:Styling 里选浅色主题，整个 App
  （抽屉、设置页、长按菜单等）都会切成浅色**，不再只有键盘跟着变。
* 状态栏和导航栏的图标会在浅色背景时自动切成深色（原仓库里**完全没有**这个处理，浅色主题下图标
  是白色的、基本看不见）。两个栏位本身仍是透明的，透出来的就是终端背景色
  （`TermuxActivity.updateWindowBackgroundColor()`）。
* 这两个栏位也会在 `onResume` 时重新应用，避免启动瞬间还是上一次的颜色。

其他取值（`true` / `false` / `system`）行为不变。设成 `color-scheme` 后切换主题时 App 会自动重建
一次（AppCompat 的标准行为），这是预期的。

实现位置：`NightMode` 新增 `COLOR_SCHEME`；`TermuxThemeUtils.resolveNightMode()` /
`isLightBackground()`；`TermuxActivity.updateNightMode()` / `updateWindowBackgroundColor()`；
`TermuxTerminalSessionActivityClient.checkForFontAndColors()` / `updateBackgroundColor()` / `onResume()`。

---

## 4. 设置页里的图形化开关（不用再手改 properties）

设置 → Termux → **Terminal Keyboard**（新页面）里现在可以直接调：

* **Input Method** 分类
  * `Gboard CJK Keyboards` = `gboard-cjk-workaround`
  * `Enforce Character Based Input` = `enforce-char-based-input`
  * `Ctrl+Space Workaround` = `ctrl-space-workaround`
* **Extra Keys Colors** 分类（4 项，点进去是色板）
  * `Text Color` / `Background Color` / `Active Text Color` / `Active Background Color`
    = `extra-keys-foreground` / `extra-keys-background` /
    `extra-keys-active-foreground` / `extra-keys-active-background`
  * 对话框里有 **22 个预设色**（含 `#80000000` 这类半透明色，可让终端背景透出来）、
    **Custom color**（手输 `#RGB` / `#ARGB` / `#RRGGBB` / `#AARRGGBB`，会校验格式）、
    以及 **Follow color scheme**（一键清除覆盖，恢复跟随 Termux:Styling 主题）

这些开关**直接读写 `~/.termux/termux.properties`**：按行替换同名键、保留文件里的注释和其他设置
（重复键会被注释掉，不会留下冲突），改完**立即生效**，不需要 `termux-reload-settings`。
所以手写 properties 和用设置页改，两者看到的是同一份配置。
（⚠️ 这里的「立即生效」在 M3 之前是**错的**：打开设置页时 TermuxActivity 处于停止状态，
广播重载会被丢掉，必须手动 reload 才生效。链路已在 M3 修好。）

实现位置：`TermuxSharedProperties.getTermuxPropertyValue()` / `setTermuxPropertyValue()`（按行改写
properties 文件）、`TerminalKeyboardPreferencesFragment`（把 `PreferenceDataStore` 落到 properties
文件）、`ColorPreference`（色板 + 自定义色对话框 + 校验）、
`app/src/main/res/xml/termux_keyboard_preferences.xml`。

---

## 5. 虚拟键盘激活态高亮 + UI 多语言

### 5.1 CTRL/ALT 等组合键的激活高亮（修复）

之前按下 CTRL/ALT 只改**文字色**，而"激活时文字颜色"默认取 `cursor` 色（常常和普通文字色相同），
所以激活后几乎看不出变化。现在激活状态会**同时**使用激活文字色和**激活背景色**
（`extra-keys-active-foreground` / `extra-keys-active-background`）：

* 激活键在手指松开后保持高亮（不会再被复位成普通背景色）；
* 长按锁定（locked）状态同样保持高亮；
* 同一类键存在多个实例（例如两行都有 CTRL）时一起变化。

想让高亮更醒目，把激活背景色调亮即可，例如 `extra-keys-active-background=#ffff7043`
（或在 设置 → Termux → Terminal Keyboard → 激活时背景颜色 里用色板选）。

### 5.2 UI 多语言（已内置简体中文）

* 新增 `values-zh` 翻译，覆盖 3 个模块：
  * `app`（壳子 UI：标题栏、会话菜单、设置页、对话框、Toast…共 126 条）
  * `termux-shared`（弹窗/错误/通知/报告文案，55 条）
  * `terminal-view`（文本选择工具栏：复制/粘贴/更多，3 条）
  * 终端模拟器（VT）内部没有面向用户的文案，无需翻译。
* Android 13+ 可在 **系统设置 → 应用 → Termux → 语言** 里单独为 Termux 选语言
  （已添加 `app/src/main/res/xml/locales_config.xml` 并在 manifest 里声明 `android:localeConfig`）；
  Android 12 及以下跟随系统语言。
* **新增其他语言**：把 `app/src/main/res/values/strings.xml` 复制为
  `values-<语言码>/strings.xml`（如 `values-ja`、`values-ru`、`values-zh-rTW`）后翻译即可，
  遗漏的条目会自动回退英文。注意占位符 `%1$s` / `%2$d` / `%d`、`\n`、`\"`、
  `&TERMUX_APP_NAME;` 这类实体必须原样保留（`termux-shared`、`terminal-view` 同理）。
* `supportsRtl` 仍是上游的 `false`；若要加阿拉伯语/希伯来语等 RTL 语言，需要改成 `true` 并检查布局。

---

## 6. 备份 / 恢复（`termux-backup` / `termux-restore`）

卸载应用会删掉整个 `/data/data/com.termux/files`（$PREFIX + $HOME），换签名构建时必须卸载，
所以本 fork 内置了两个脚本 + 图形入口。

### 图形入口

**设置 → Termux → 备份与恢复**：

| 条目 | 作用 |
| --- | --- |
| 立即备份 | 提取脚本并在**新终端会话**里运行，输出可见、可 Ctrl+C 取消 |
| 从备份恢复 | 脚本会列出设备上找到的备份（含大小/时间）让你选序号，再要求确认；默认先自动备份当前环境 |
| 把脚本安装为命令 | 复制到 `$PREFIX/bin`，之后随时可 `termux-backup` / `termux-restore` |

脚本以 raw 资源形式随 App 打包（`app/src/main/res/raw/termux_backup.sh`、`termux_restore.sh`），
运行时提取到 `$PREFIX/tmp/` 用 `bash` 执行；CI 也会把这两个脚本作为独立文件放进 Release。

### 命令行用法

```sh
termux-backup                      # 备份到 ~/storage/shared/Download/termux-backup-<日期>-<时间>.tar.zst
termux-backup -o /sdcard/Download -n my-bk -z gzip   # 指定目录/名字/压缩器（zstd 优先，gzip 兜底）
termux-restore                     # 不带参数：交互列出备份并选择
termux-restore /sdcard/Download/termux-backup-20260101-120000.tar.zst --dry-run   # 只校验+报告
termux-restore <归档> --target-dir ~/restore-test --no-pre-backup                  # 解包到别处演练（不碰现有环境）
```

### 行为与安全

* 归档内容：完整的 `./usr`（$PREFIX）与 `./home`（$HOME），排除 `usr/tmp/*`、`usr/var/run/*`、
  `home/storage`（符号链接）、`home/.cache/*`；另有一个 `<名字>.metadata/` 目录记录
  dpkg 包列表、`termux.properties`、`colors.properties` 与 app/bootstrap 版本，便于恢复时对比。
* 生成后会做 `tar -t` 完整性校验并写 `<名字>.sha256`；恢复前会用这个校验值验证（存在时）。
* 恢复是 tar 覆盖式：归档里的文件被覆盖，**归档里没有的文件（例如备份后新装的包）保留**。
* 恢复前默认先自动备份当前环境（`termux-backup-before-restore-<时间>`），可用 `--no-pre-backup` 关闭。
* 共享存储不可用时（还没 `termux-setup-storage`）会自动退回到 `$HOME` 存放，并提示。
* 恢复后建议执行：`termux-reload-settings` → `pkg upgrade` → `termux-setup-storage`
  （`~/storage` 不在备份里）；`~/bin` 下若有绝对 shebang 的脚本用 `termux-fix-shebang` 修一下。

---

## 7. 设置页补齐：外观 / 按键行为 + 鼠标点按唤键盘

§4 里的开关只覆盖了输入法和 extra keys 配色，其余常用属性这次也搬进了设置页，
全部**直接读写 `~/.termux/termux.properties`**（按行替换同名键、保留注释和其他设置），
改完立即生效。

### 7.1 设置 → Termux → **外观**（新页面，`AppearancePreferencesFragment`）

| 分类 | 条目 | 属性 | 说明 |
| --- | --- | --- | --- |
| 主题 | 夜间模式 | `night-mode` | 浅色 / 深色 / 跟随系统 / 跟随配色方案（`color-scheme`，见 §3） |
| 全屏 | 全屏 | `fullscreen` | 隐藏状态栏，终端绘制在状态栏后面 |
| 全屏 | 全屏兼容处理 | `use-fullscreen-workaround` | 仅在开了全屏时生效；终端被画到状态栏下面时打开 |
| 终端工具栏 | 虚拟键盘风格 | `extra-keys-style` | `default` / `arrows-only` / `arrows-all` / `all` / `none` |
| 终端工具栏 | 工具栏高度（%） | `terminal-toolbar-height` | 40–300，100 为默认；滑条存的是百分数，写回文件时会换算成 `0.4`–`3.0` 的倍率 |
| 终端工具栏 | 虚拟键盘字母大写 | `extra-keys-text-all-caps` | 默认开 |

这一页的属性都是**创建 Activity 时读取**的，所以改完会重建一次 `TermuxActivity`
（终端会话和 transcript 都在 `TermuxService` 里，不会被杀掉）。

### 7.2 设置 → Termux → **终端键盘** → 按键行为（新分类）

| 条目 | 属性 | 取值 |
| --- | --- | --- |
| 返回键 | `back-key` | `back`（默认）/ `escape` |
| 音量键 | `volume-keys` | `virtual`（默认，发方向键等虚拟键）/ `volume`（真实音量键） |
| 键盘开关按钮 | `soft-keyboard-toggle-behaviour` | `show/hide`（默认）/ `enable/disable` |
| 启动时隐藏键盘 | `hide-soft-keyboard-on-startup` | 默认关 |
| **鼠标点按唤键盘** | `mouse-tap-shows-keyboard` | 默认关，见下 |
| 停用物理键盘快捷键 | `disable-hardware-keyboard-shortcuts` | 默认关（默认由 App 处理 Ctrl+Alt+C 等） |

这些属性都在用到时读取，所以改完立即生效，不需要重建 Activity。

### 7.3 新属性：`mouse-tap-shows-keyboard`（本 fork 新增）

```properties
# 鼠标点击、以及 TUI 开了鼠标跟踪时的点击，默认只转发给终端，不弹软键盘
# （设计如此，否则 vim/tmux 的选择、点击会被键盘打断）。打开后这些点击也会弹软键盘。
mouse-tap-shows-keyboard=true
```

实现：`TermuxTerminalViewClient.onSingleTapUp()` 里原来只有
「非鼠标来源 && 未开鼠标跟踪」才弹键盘，现在这个限制在属性打开时被取消。
用 `KEYBOARD` 键 / Ctrl+Alt+K 手动唤起键盘的老办法仍然可用（§1）。

---

## 8. 真斜体字体 + OSC 8 超链接可点

### 8.1 真斜体：`~/.termux/font-italic.ttf`

上游的斜体是**合成斜切**（把正体字形按 `setTextSkewX(-0.35)` 歪一下），很多字体歪出来很难看。
现在只要在正体旁边放一个斜体字体文件，斜体就用真字形渲染：

```sh
# 和 font.ttf 同级（Termux:Styling 只写 font.ttf，不会碰这个文件，要自己放）
~/.termux/font-italic.ttf
```

* **没有该文件时行为完全不变**（回退到原来的 -0.35 合成斜切），不会因为升级而突然变形。
* 斜体字形会按 `'X'` 的字宽**水平缩放**到与正体同宽，所以斜体度量与正体不一致时也不会把网格
  撑歪；对度量一致的字体缩放系数正好是 1，等于无失真。
* 字体度量（行高/基线/列宽）一律取正体，保证网格对齐。
* 加载失败（文件损坏）只会记一条日志，不影响正体和配色。
* 相关文件：`TermuxConstants.TERMUX_FONT_ITALIC_FILE`、`TerminalRenderer`（`mItalicTypeface` /
  `mItalicTextScaleX` / `setItalic()`）、`TerminalView.setTypeface(Typeface, Typeface)`、
  `TermuxTerminalSessionActivityClient.checkForFontAndColors()`。

### 8.2 OSC 8 超链接

```sh
# OSC 8 ; params ; URI ST ；URI 留空表示结束链接
printf '\e]8;;https://example.com\e\\点我\e]8;;\e\\ 普通文字\n'
```

* 链接文字**自动加下划线**（和 xterm/VTE/kitty 一致），一眼能看出哪里可点。
* **点击打开**复用现有开关 `terminal-onclick-url-open`（默认关，见 §1）。打开后：
  被点格子的 OSC 8 链接优先，取不到才回退到原来的「纯文本里找 URL」逻辑。
  （之所以共用同一个开关：默认关是为了不干扰在文字上点选，OSC 8 也一样。）
* `params`（例如 `id=xxx`）按规范忽略；`BEL` 和 `ESC \` 两种结尾都支持。
* 链接**按格存储**：滚动、改变列数时的 reflow、宽字符、备用屏切换都跟着文字走；
  `EL`/`ED`/`ECH` 擦除会清掉链接；`ESC c`(RIS) 会清掉「当前链接」状态。
* 换行**不会**自动结束链接（符合 OSC 8 规范：由程序显式用空 URI 结束）。
* 未被覆盖的部分：长按「选择 URL」菜单仍然只看纯文本（不认 OSC 8 链接）。
* 实现位置：`TerminalEmulator`（`mCurrentHyperlinkUri`、`doOsc` 的 `case 8`）、
  `TerminalBuffer.setChar(...)/getHyperlinkAtLocation()`、`TerminalRow`（惰性 `int[] mHyperlinks`
  + 每行 URI 表、`copyInterval`/`clear`）、`TerminalRenderer`（run 分组纳入链接 → 下划线）、
  `TermuxTerminalViewClient.onSingleTapUp()`。
* 单测：`OperatingSystemControlTest#testSetHyperlink*`（设置/结束/params/宽字符/擦除/RIS）、
  `ResizeTest#testResizePreservesHyperlinks`、`TerminalRowTest#testHyperlink` /
  `#testCopyIntervalCopiesHyperlinks`。

---

## 9. 交互三连：收起额外按键行 / 拖拽上报 / 焦点上报

### 9.1 软键盘隐藏时自动收起 extra keys 行（`hide-extra-keys-when-soft-keyboard-hidden`）

上游 issue #140 / #3263。开着 extra keys 行时，软键盘一收（比如只想读输出），那一行就白占着屏幕。

设置 → Termux → 终端键盘 → 按键行为 → **软键盘隐藏时收起虚拟键盘**（默认**关**），对应属性：

```properties
# 软键盘隐藏时收起 extra keys 行，把这行还给终端；软键盘再弹出时自动恢复
hide-extra-keys-when-soft-keyboard-hidden=true
```

* 判定用 `KeyboardUtils.isSoftKeyboardVisible()`（`WindowInsetsCompat.Type.ime()`），**需要 Android 6+
  才有效**；更低版本该属性不生效（不会崩，只是不做处理）。
* 判定时机是布局变化（`TermuxActivityRootView.onGlobalLayout()`），因为 IME 可见性只能这时读；
  另外 `reloadActivityStyling()` 里也会重算，所以属性开关一改就立即生效。
* **手动优先**：用工具栏开关（`Ctrl+Alt+K` / 会话菜单里的切换工具栏）手动显示时，会记住「手动设置」，
  直到软键盘状态再次变化才重新按规则收起——避免开关看起来坏了。
* 实现：`TermuxActivity.updateTerminalToolbarVisibilityForSoftKeyboardVisibility()` /
  `setTerminalToolbarVisibility()`、`toggleTerminalToolbar()` 设手动标志、`TermuxActivityRootView.onGlobalLayout()`。

### 9.2 手指拖拽按鼠标拖拽上报（`touch-drag-sends-mouse-drag`）

上游 issue #1384。鼠标跟踪（1000/1002/1006）生效时，手指拖拽原来一律变成滚轮事件（`doScroll()`），
所以 TUI 里没法做「按住拖拽」（tmux 拉窗格、vim 可视选择等）。

设置 → Termux → 终端键盘 → 按键行为 → **手指拖拽按鼠标拖拽上报**（默认**关**），对应属性：

```properties
# 手指拖拽上报为鼠标指针拖拽（按下 → 移动 → 抬起），而不是滚轮事件
touch-drag-sends-mouse-drag=true
```

* 打开后，鼠标跟踪生效时手指拖拽发 3 个事件：`e1`（按下位置）按下 → 每次移动 → 抬手/取消时抬起，
  这样程序能收到完整的拖拽（没有按下事件只有移动事件的话，多数程序不会当作拖拽）。
* **默认关**是有意的：打开后使用鼠标跟踪的程序（tmux/vim `mouse=a`）就**收不到拖拽产生的滚轮事件**了，
  滚动会变成拖拽；要用滚轮滚动就把属性关掉。要用哪个由你决定，设置页里随时可切。
* 备用屏（没有鼠标跟踪）不受影响：仍然是上下方向键；惯性甩动（fling）在打开该属性时不再补发滚轮事件，
  否则拖拽结束后又滚一下。
* 长按之后（手势归 View，如文本选择）不启动拖拽上报，取消手势（`ACTION_CANCEL`）会补发抬起事件，
  避免程序里鼠标一直处于按下状态。
* 实现：`TerminalViewClient.shouldTouchDragSendMouseDrag()`、
  `TerminalView`（`onScroll(e1, e2, ...)` / `onUp()` / `onFling()` / `ACTION_CANCEL`）、
  `GestureAndScaleRecognizer.onScroll()` 现在也把 `e1` 传出来。

### 9.3 焦点上报（`DECSET 1004`）

上游 issue #3650 / discussion #3644。之前 `DECSET_BIT_SEND_FOCUS_EVENTS` 的位存在、解析也认，
但**从来不发事件**（代码里写着 "NOT implemented"），所以 Emacs 之类靠焦点事件自动存盘的程序拿不到通知。

现在：

* `TermuxActivity.onWindowFocusChanged()` → 当前会话的 emulator `setWindowFocused(hasFocus)` →
  程序若开了 `DECSET 1004` 就收到 `CSI I`（获得焦点）/ `CSI O`（失去焦点）。
  切换 App、拉通知栏、弹对话框都算「失去焦点」。
* 会话切换时（`TermuxTerminalSessionActivityClient.setCurrentSession()`）也给旧会话发失去焦点、
  给新会话同步当前焦点状态，这样切会话也能触发存盘。
* **程序开启 1004 的那一刻会立刻收到当前焦点状态**（`doDecSetOrReset` 的 `case 1004`），
  否则在 App 启动时就开 1004 的程序永远不知道自己是聚焦的（行为对齐 xterm.js #2333）。
* emulator 侧 `mIsWindowFocused` 默认 `true`（会话都是在前台创建的），只有状态真的变化才发，
  避免重复发同一事件。
* 实现：`TerminalEmulator.setWindowFocused()` / `sendFocusEvent()` / `isReportingFocusEvents()`、
  `TermuxActivity.onWindowFocusChanged()` / `isWindowFocused()`、单测 `DecSetTest#testSendFocusEvent`。

---

## 10. 多面板 extra keys / 左右滑切会话 / 会话列表信息

### 10.1 多面板 extra keys（`extra-keys-panels`）

上游 issue #4365（来自 termux-monet）、#5178。以前 `extra-keys` 只能定义一套按键（一或两行），
键多就只能挤在一行里。现在可以定义多个**面板**，像切到文本输入框那样左右滑动切换：

```properties
# 每个面板就是一个普通 extra-keys 矩阵，整个属性是「面板数组」
extra-keys-panels=[ \
  [['ESC','TAB','CTRL','ALT','HOME','UP','END','PGUP'],['BKSP','LEFT','DOWN','RIGHT','PGDN','KEYBOARD']], \
  [['F1','F2','F3','F4','F5','F6'],['|','~','{','}','[',']']], \
  [['-','/','*','+','=','%'],['1','2','3','4','5','6']] \
]
```

* **向后兼容**：不设 `extra-keys-panels` 时，仍按 `extra-keys` 当作**单个面板**（行为与以前完全一致）。
  设了就以 `extra-keys-panels` 为准。
* 面板是终端工具栏 `ViewPager` 的页：第 1..N 页是面板，**文本输入框是最后一页**。
  所以「第 1 个面板 → 左滑 → 第 2 个面板 → … → 文本输入框」。
* 工具栏高度取**所有面板里最大的行数**（面板行数不同时不会来回跳动）；
  配色（`extra-keys-*`）、`extra-keys-style`、`extra-keys-text-all-caps` 对所有面板生效。
* 属性写错（不是「矩阵的数组」）会弹 toast 并**回退到 `extra-keys` 的单面板**；`extra-keys` 也坏掉才会回退到内置默认按键。
* 顺手修了一个老问题：`extra-keys` / `extra-keys-style` 以前**只在 Activity 创建时解析**
  （`TermuxTerminalExtraKeys` 构造函数里），所以改完属性 `termux-reload-settings` 是不生效的、必须重进 App。
  现在 `reloadProperties()` 会重新解析，变化时重建工具栏的页（文本输入框里已输入的文字会保留）。
* 实现：`TermuxPropertyConstants.KEY_EXTRA_KEYS_PANELS`、`TermuxTerminalExtraKeys`（`List<ExtraKeysInfo>`、
  `getExtraKeysPanels()`/`getExtraKeysPanelsMaxRows()`/`reloadExtraKeys()`）、
  `TerminalToolbarViewPager.PageAdapter`（页数 = 面板数 + 1、`getItemPosition()` 返回 `POSITION_NONE`）、
  `TermuxActivity`（`mExtraKeysViews` 列表、`addExtraKeysView()`、`recreateTerminalToolbarViewPagerPages()`）。

### 10.2 左右滑切换会话（`switch-session-on-swipe`）

上游 issue #48 / PR #452。在终端区域左右滑动切换上一个/下一个会话：

```properties
# 默认就是开的；关掉写 false
switch-session-on-swipe=true
```

设置 → Termux → **终端视图** → 左右滑动切换会话（默认**开**）。

* 触发条件：横向滑动**明显**大于纵向（`|vx| > 2·|vy|`）且横移超过视图宽度的 1/10，避免误触。
* **不触发**的情况：正在选择文本；终端程序开了鼠标跟踪（1000/1002/1006，那时手势属于程序）；
  从左边缘开始的滑动仍然是抽屉（DrawerLayout 先接管）。
* 往左滑 = 下一个会话，往右滑 = 上一个会话（和 ViewPager 翻页一致，会循环）。
* 实现：`TerminalViewClient.shouldSwitchSessionOnSwipe()` / `onSwitchSessionSwipe(forward)`、
  `TerminalView.isSessionSwitchSwipe()` / `onFling()`（`GestureAndScaleRecognizer` 现在把 `e1` 也传出来）、
  设置页 `TerminalViewPreferencesFragment`（这一页现在混合两种存储：`terminal_margin_adjustment` 仍走
  SharedPreferences，其余走 `termux.properties`）。

### 10.3 会话列表显示 PID / 前台进程 / cwd

会话抽屉（侧边栏）里每一项现在有两行：第一行还是 `[序号] 会话名 / 标题`，
第二行是 `PID 12345 · vim · ~/projects/foo`：

* **PID**：`TerminalSession.getPid()`。
* **前台进程名**：读 `/proc/<pid>/stat` 的第 8 个字段 `tpgid`（终端前台进程组），
  再取 `/proc/<tpgid>/cmdline` 的第一个参数（取 basename；内核线程空 cmdline 时回退 `/proc/<pid>/comm`）。
  也就是说 `vim` 在前台时显示 `vim`，回到 shell 时显示 `bash`/`zsh`。
* **cwd**：读 `/proc/<pid>/cwd` 符号链接（shell 的工作目录），`$HOME` 前缀缩成 `~`。
* 三项都是**读不到就不显示那一项**（会话已退出、进程已消失、无权限等），整行读不到就隐藏第二行。
* 刷新时机：列表本身在有输出/切标题时重建，另外**打开抽屉时**也会刷新一次
  （`DrawerLayout.SimpleDrawerListener.onDrawerOpened` → `notifyDataSetChanged()`），
  所以 cwd 变了但标题没变也能看到最新值。
* 实现：`item_terminal_sessions_list.xml`（两行布局，选中背景移到根布局）、
  `TermuxSessionsListViewController.getView()` 与 `getSessionDetails()` / `getForegroundProcessName()` /
  `getProcessName()` / `getCurrentWorkingDirectory()`。

---

## 11. 音量键自定义动作（单击 / 长按）

上游的音量键只有两种命运：`volume-keys=virtual`（默认）时音量减当 Ctrl、音量加当 Fn（一张组合键表），
或者 `volume-keys=volume` 时完全不接管。现在可以**逐键、按手势**指定音量键做什么。

### 配置

设置 → Termux → **终端键盘** → 按键行为（也直接写 `~/.termux/termux.properties`）：

| 条目 | 属性 | 默认 |
| --- | --- | --- |
| 音量加键（单击） | `volume-up-action` | `default` |
| 音量加键（长按） | `volume-up-long-action` | `none` |
| 音量减键（单击） | `volume-down-action` | `default` |
| 音量减键（长按） | `volume-down-long-action` | `none` |
| 音量加键序列 | `volume-up-send-keys` | `ESC` |
| 音量减键序列 | `volume-down-send-keys` | `ESC` |

```properties
# 例：音量减单击收放软键盘、长按调音量；音量加保持官方的 Fn 组合键
volume-down-action=keyboard
volume-down-long-action=volume
volume-up-action=default
```

可取的动作值：

| 值 | 作用 |
| --- | --- |
| `default` | **官方的虚拟键行为**：音量减=按住期间当 Ctrl，音量加=按住期间当 Fn（Fn 组合键表：音量加+W/A/S/D 方向键、+P/N 翻页、+1~0 F1~F10、+v 弹音量条等，与上游一致） |
| `none` | 什么都不做（按键仍被吞掉，不会去调系统音量） |
| `volume` | 弹系统音量条并真正调节音量（加/减方向自动对应） |
| `keyboard` | 显示/隐藏软键盘 |
| `toggle-extra-keys` | 显示/隐藏底部 extra keys 行 |
| `page-up` / `page-down` | 向终端发 Page Up / Page Down（适合 less、man、vim） |
| `prev-session` / `next-session` | 切到上/下一个会话（等价左右滑） |
| `new-session` | 新建会话 |
| `paste` | 粘贴剪贴板 |
| `send-keys` | 发送 `volume-*-send-keys` 里配置的按键序列 |

### 手势与生效规则

* **单击**：动作在**抬键时**执行一次；**长按**：动作在按键开始重复（约 500ms，跟随系统长按判定）时执行一次，
  此时不再执行单击动作。
* 长按留默认的 `none` 时，长按**不会**抵消单击动作：按住久一点也还是触发单击动作（不会出现“没反应”）。
* `default` 是修饰键，必须在**按下瞬间**生效（要和其它键组合），因此如果给它又配了一个 `none` 以外的长按动作，
  按住超过 500ms 会额外触发那个长按动作；想把音量键当稳定的 Ctrl 用就把长按留成 `none`。
* 只有在「按键行为 → 音量键」是**虚拟音量键（`volume-keys=virtual`，默认）**时这些动作才生效；
  选了「真实的音量键」就是完全不接管（这是上游语义）。
* 外接全键盘（`KEYBOARD_TYPE_ALPHABETIC`）仍然不抢音量键，和上游一致。
* `send-keys` 的序列与 `extra-keys` 的 macro 语法相同：空格分隔，键名用 `ESC`/`TAB`/`PGUP`/`UP`/`F1` 这类
  名字（全表见 `ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS`），也可以写普通文本；
  键名前加 `CTRL`/`ALT`/`SHIFT`/`FN` 可当修饰键，但**只作用于紧随其后的一个键**：`CTRL c` 是 Ctrl+C，
  `CTRL c d` 是 Ctrl+C 再跟一个 D。

### 恢复默认

设置页底部有 **恢复音量键默认行为**：把上面 6 个属性在文件里注释掉（行首变成 `#`，不删其它内容），
即完全回到官方行为。每个下拉里选“默认 / 无动作”也能单项恢复。

实现位置：`TermuxPropertyConstants`（`KEY_VOLUME_*_ACTION` / `KEY_VOLUME_*_SEND_KEYS`、`MAP_VOLUME_KEY_ACTION`）、
`TermuxSharedProperties.getVolumeKeyAction()` / `getVolumeKeySendKeys()`、
`TermuxTerminalViewClient.handleVolumeKey()`（手势状态机）/ `performVolumeKeyAction()`（动作分发）、
`ExtraKeyButton(String, boolean, String)`（从序列字符串直接构造 macro 按钮）+
`TerminalExtraKeys`（复用它的序列发送逻辑）、
`app/src/main/res/xml/termux_keyboard_preferences.xml` + `TerminalKeyboardPreferencesFragment`（含序列项的条件显示）。

---

## 12. 额外按键右上角显示上滑备用键（`extra-keys-popup-hints`）

上游的 extra keys 按键只有在**手指上滑**时才会弹出 popup 按键，按键本身没有任何提示，用户记不住
哪个键有上滑功能、上滑出来的是哪个键。现在每个定义了 `popup` 的按键会在**右上角用小字**显示
popup 键的显示文本：

```
┌──────────────────┐
│              END │  ← 小字、半透明，即 popup 键的 display
│        →         │  ← 主键
└──────────────────┘
```

例如 `extra-keys` 里写 `{key: RIGHT, popup: END}`，右箭头键右上角就显示 `END` 对应的显示文本
（默认样式下是 `⇲`，想看英文名就写成 `popup: {key: END, display: END}`）。

```properties
# 关闭提示（按键回到上游的外观）
extra-keys-popup-hints = false
```

* 提示文本用的是 **popup 键的 display**，所以和上滑后弹出的那个按钮上显示的文字完全一致，
  默认样式（`extra-keys-style=default`）、`all` 等风格下的箭头 / 图标都会同步变化。
* 提示颜色是按键文字色降低 alpha（`POPUP_HINT_TEXT_ALPHA_RATIO = 0.6`），字号是按键字号的
  `POPUP_HINT_TEXT_SIZE_RATIO = 0.65` 倍，字体和字距直接从按键复制，所以深色 / 浅色主题、
  Termux:Styling 换色、系统字号缩放后都能和主键风格保持一致。
* 提示的垂直位置用 `MaterialButton.getInsetTop()` 偏移：按键的背景相对自己的 bounds 有上下
  6dp 的 inset（`Widget.MaterialComponents.Button.TextButton` 的定义），不补偿的话提示就会
  画在按钮背景外面。
* 提示是**不吃触摸**的 `TextView`：在提示覆盖的那一小块区域上滑，仍然会正常弹出 popup。
* 只在按键有 popup 时出现，没有 popup 的按键外观与上游完全一致，格子宽度 / 行高也不变
  （按键被放进一个 `FrameLayout` 容器里，容器继承原来的 `GridLayout` 权重参数）。
* 和 §10 的多面板冲突：每个面板的按键都会各自生成提示，无需额外配置。

**为什么默认打开**：这条属性是纯显示改进，关掉才能回到上游外观；其余新增属性仍然遵守
“默认关闭或保持上游行为”的约定（见文首）。不想要就在设置页关掉，或写
`extra-keys-popup-hints = false`。

设置位置：设置 → Termux → 终端键盘 → **虚拟键盘显示 → 上滑备用键提示**（改完立即生效，
因为 `TermuxPropertyPreferenceDataStore` 会发 `ACTION_RELOAD_STYLE`，回到终端时
`TermuxActivity.reloadActivityStyling()` 重新 `reload()` 按键）。

实现位置：
`ExtraKeysView`（`mShowPopupHints` / `setShowPopupHints()`、`reload()` 里把按键包进 `FrameLayout`
并加右上角 `TextView`、`createPopupHintView()` / `getPopupHintTextColor()` / `dpToPx()`）、
`TermuxPropertyConstants`（`KEY_EXTRA_KEYS_POPUP_HINTS`，加入 `TERMUX_APP_PROPERTIES_LIST` 与
`TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST`）、
`TermuxSharedProperties.shouldExtraKeysPopupHintsBeShown()`、
`TerminalToolbarViewPager`（创建面板时设置）、`TermuxActivity.reloadActivityStyling()`（改设置后设置）、
`app/src/main/res/xml/termux_keyboard_preferences.xml` + `values/strings.xml` + `values-zh/strings.xml`。

---

## 13. Gboard 风格的按键块背景（`extra-keys-tile-background`）

上游的 extra keys 按键只是浮在行背景上的文字，按下才有底色，平时看不出按键边界。开启这个特性后
每个按键背后画一个 **4dp 圆角的方块**，块间留 **2dp 缝隙**（和 Gboard 平板很像），并且：

* 颜色**跟随 termux 配色方案**：默认块色 = `background` 混入 `foreground` 8%
  （`EXTRA_KEYS_TILE_BACKGROUND_COLOR_BLEND_RATIO`），按下色 = 块色再混入 `foreground` 25%
  （`EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO`）。所以换 Termux:Styling 主题、改
  `colors.properties` 时按键块自动跟着变，深色主题是“亮一丢丢”，浅色主题是“暗一丢丢”。
* 想固定颜色可以用两个新属性（也能在设置页的调色盘里选）：

```properties
# termux.properties（会覆盖 colors.properties 里的同名颜色）
extra-keys-tile-background = true           # 关掉就回到上游的纯文字按键
extra-keys-tile-background-color = #ff2a2a3a
extra-keys-tile-active-background-color = #ff45475a
```

* 按下 / 激活（如 CTRL 打开）时用 `extra-keys-tile-active-background-color`，与行背景的
  `extra-keys-active-background` 互不干扰——行背景仍然只在按键块外的那一圈可见。
* 实现方式：`MaterialButton.setCornerRadius(4dp)` + 把按钮自己的 `inset*` 清零，缝隙由格子的
  1dp margin 提供（相邻两块 = 2dp）；也就是说块本身就是按键的 background，所以按下高亮、
  CTRL/ALT 激活高亮都是同一层，不会出现“块和按键不同步”。
* 与 §12 共存：提示的垂直偏移用 `MaterialButton.getInsetTop()`（块模式下为 0）+ 2dp，
  所以提示总在块里，不会被圆角切掉。
* 默认开启：这是纯视觉改进，关掉即回到上游外观（与 §12 一致）。

设置位置：设置 → Termux → 终端键盘 → **虚拟键盘显示 → 按键块背景**（开关）、
**虚拟键盘配色 → 按键块颜色 / 按键块按下颜色**（调色盘）。改完立即生效。

实现位置：`ExtraKeysView`（`mShowTileBackground` / `setShowTileBackground()`、
`mButtonTileBackgroundColor` / `setButtonTileColors()`、`TILE_CORNER_RADIUS_DP` / `TILE_MARGIN_DP`、
`getButtonBackgroundColor()` / `getActiveButtonBackgroundColor()`）、
`TerminalColorScheme`（`KEY_EXTRA_KEYS_TILE_BACKGROUND`、`KEY_EXTRA_KEYS_TILE_ACTIVE_BACKGROUND`、
`EXTRA_KEYS_TILE_BACKGROUND_COLOR_BLEND_RATIO` 及默认值推导）、
`TermuxPropertyConstants`（`KEY_EXTRA_KEYS_TILE_BACKGROUND`、`KEY_EXTRA_KEYS_TILE_BACKGROUND_COLOR`、
`KEY_EXTRA_KEYS_TILE_ACTIVE_BACKGROUND_COLOR`）、`TermuxSharedProperties`、
`TermuxActivity.setExtraKeysViewColors()`、`app/src/main/res/xml/termux_keyboard_preferences.xml`（开关 +
两个 `ColorPreference`）、`TerminalTest.testExtraKeysColors()`（新增颜色断言）。

---

## 验证情况（真机）

以下各项都在本机（aarch64，Android 13 系 ROM）真机验证过（§11 音量键自定义动作尚未真机验证）：

* 备份 26s / 333MB / 43023 条目，校验通过；`--target-dir` 解包演练的符号链接与权限位正确。
* 外观页 6 项、按键行为 6 项改完立即生效；打开设置页**不会**改写 `termux.properties`。
* 真斜体（`font-italic.ttf`）与 OSC 8 链接（自动下划线 + 点击打开）正常。
* IME 收起额外按键行、手指拖拽按鼠标拖拽上报、`DECSET 1004` 焦点事件正常。
* 多面板 extra keys 横滑切换、左右滑切会话、会话列表 `PID · 前台进程 · cwd` 正常。
