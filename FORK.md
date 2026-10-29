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
| 14 | 终端内边距（类似 Windows Terminal 的 padding） | `terminal-padding-top` / `-bottom` / `-left` / `-right`（dp，0–100，设置页有 4 个滑条） | 0（关闭） |
| 15 | 终端内搜索（回滚历史里查找，全部匹配高亮 + 上下跳转） | 长按菜单 → 搜索；`Ctrl+Alt+F` | 按需打开 |
| 16 | OSC 52 剪贴板（可禁止写入 / 允许程序读取剪贴板） | `terminal-osc52-clipboard`（`write` / `read-write` / `off`，设置页有下拉） | `write`（与上游一致） |
| 17 | 命令标记（OSC 133）：跳转上/下一条命令、右边缘刻度、失败通知（v2 集成额外上报执行的命令行） | 设置页安装 shell 集成（写入 `~/.bashrc`/`~/.zshrc`） | 需手动安装集成，未安装时无任何变化 |
| 18 | 分屏（上下 / 左右，可拖动分隔条，两个独立会话） | 长按菜单 → 分屏 | 默认关闭 |
| 19 | 状态栏（时间 / 电量 / 内存 / CPU / 网络 / IP / 代理，可配图标、图表、配色、位置、点击动作） | `status-bar-*` 属性或设置 → 状态栏 | 默认关闭 |
| 20 | 终端内通知卡片（右上角 nvim-notify 风格，失败命令 / App 事件 / OSC 9·99·777） | `notification-*` 属性或设置 → 通知 | **默认关闭**（关时行为与上游一致） |
| 21 | 可配置的长按菜单 + 文本选择浮条项 + `SEARCH`/`SPLIT_*` 虚拟键 | `terminal-menu-*`、`selection-toolbar-items` 或设置 → 长按菜单 | 菜单 11 项；浮条 复制/粘贴/更多；风格 TUI 像素风 |
| 22 | 设置页 GUI：通知 / 长按菜单（含可调序的双区列表编辑器） | 设置 → Termux → 通知 / 长按菜单 | 与属性等价，改完立即生效 |
| 23 | 程序主动发通知：OSC 99（分块/图标/按钮/查询/关闭/回报）、OSC 9、OSC 777 | `notification-osc-*` 或设置 → 通知 → 程序主动发送的通知 | OSC 通道**默认关**，限流 3 条/秒/会话 |
| 24 | App 事件卡片（会话退出/插件结果/标题变化/bell）+ 后台系统通知 + 最近通知列表 | `notification-events-*`、`notification-background-enabled`、`notification-history-size` 或设置 → 通知 | 事件通道**默认关**；后台通知**默认关**；历史 50 条（仅内存） |

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

## 14. 终端内边距（`terminal-padding-*`）

类似 Windows Terminal 的 padding：让终端内容与世界边缘留白。和上游的
`terminal-margin-horizontal` / `terminal-margin-vertical` 不同，padding 只缩**终端视口**，
底部 extra keys 工具栏和左侧会话抽屉仍是通栏；margin 缩的是整个内容区（含工具栏/抽屉）。两者可叠加。

```properties
# 单位 dp，取值 0-100，默认 0（保持上游外观）
terminal-padding-top=0
terminal-padding-bottom=0
terminal-padding-left=8
terminal-padding-right=8
```

* padding 区域显示**终端背景色**（即 `colors.properties` 的 `background`，包括半透明配色），
  看起来像一圈画框。
* 行列数按缩小后的尺寸重新计算（程序会收到 `SIGWINCH`），所以 `tput cols/lines`、vim/tmux 的
  布局都会跟着变；最大 100dp，极端值下终端至少保留 4 行 4 列。
* 与全屏/状态栏无关；横竖屏都生效（值固定为 dp，不随方向变化）。
* 设置 → Termux → **终端视图** → **终端内边距**（上/下/左/右 4 个滑条，0–100），
  改完返回终端立即生效（会触发一次终端 resize）。

**实现**：`activity_termux.xml` 里给 `TerminalView` 套了一层 `FrameLayout#terminal_view_container`，
`TermuxActivity.setTerminalPadding()` 只对容器 `setPadding()`。这样终端视图自己的坐标体系完全不变，
绘制、触摸定位、文本选择手柄、IME 光标都不用感知 padding；padding 区域本来就是 decor view 的
背景色（终端背景由 `updateWindowBackgroundColor()` 设在窗口上，`TerminalRenderer` 不填背景），
所以不需要额外给容器设背景。

实现位置：`activity_termux.xml`、`TermuxActivity.setTerminalPadding()` /
`reloadActivityStyling()` / `onCreate()`、`TermuxPropertyConstants`（4 个 key）+
`TermuxSharedProperties.getTerminalPadding*()`、`TermuxPropertyPreferenceDataStore`（新增 int 支持）、
`termux_terminal_view_preferences.xml`（4 个 `SeekBarPreference`）。

---

## 15. 终端内搜索（回滚历史查找）

上游 Termux 没有“在回看里搜索”的功能。现在可以在屏幕 + transcript（回滚历史）里查找文本：

* **打开方式**：长按终端 → 菜单中选择 **搜索**（菜单也可以 `Ctrl+Alt+M` 打开）；
  外接键盘直接按 **`Ctrl+Alt+F`**。
* 输入框边输边搜（**忽略大小写**），所有匹配都会高亮，当前匹配用更深的橙色标出，
  右下角显示 `当前/总数`。
* 初始会跳到**最近**的一个匹配（通常就是屏幕上看到的那个），按 `↑` / `↓`（或键盘 Enter）
  在匹配间前后跳转并**循环**，视图会自动滚到匹配所在行（包括 transcript 里的行）。
* 按返回键关闭搜索栏（不会退出 App），高亮同时清除。
* 切换会话时会自动在新会话里重新搜索。

限制：

* 只做字面量匹配，不支持正则；最多返回 5000 个匹配（防止极端情况下卡顿）。
* 搜索的是**逻辑行**：自动换行的行会被拼起来，所以跨行的长路径/URL 也能搜到，跨行匹配会拆成
  每行一段分别高亮。
* 搜索栏是覆盖在终端顶部的浮层（不改变终端行列数，不会触发 `SIGWINCH`）。

实现位置：`TerminalBuffer.findAllMatches()` / `SearchMatch`（逐逻辑行匹配、按行列排序、大小写折叠用
逐字符 `Character.toLowerCase()` 保证列映射不被长度变化破坏）、`TerminalRenderer.setSearchHighlights()`
（按行二分推进 + 文字之前画高亮矩形）、`TerminalView.setSearchQuery()` / `findNextSearchMatch()` /
`clearSearch()`（resize 后重新匹配并保持当前匹配位置）、`activity_termux.xml` 的
`terminal_search_bar`、`app/.../terminal/TerminalSearchController.java`（搜索栏的视图、查询、
上一个/下一个匹配与 `2/7` 计数）、`TermuxActivity.showTerminalSearch()` / `hideTerminalSearch()`
（转发给 controller）、`TermuxTerminalViewClient.onKeyDown()`（`Ctrl+Alt+F`）。

---

## 16. OSC 52 剪贴板（`terminal-osc52-clipboard`）

上游其实**已经支持 OSC 52 写入**（tmux/vim 里复制会写进系统剪贴板），这次补上的是**可控性**和
**读取方向**：

```properties
# write（默认，和上游完全一致）：程序可以写剪贴板
# read-write：程序还可以用 `OSC 52 ; c ; ?` 读剪贴板
# off：完全忽略 OSC 52（程序无法再覆盖你的剪贴板）
terminal-osc52-clipboard=write
```

设置 → Termux → **终端视图** → 剪贴板 → **OSC 52 剪贴板**（下拉）。

* **写入**（`\e]52;c;<base64>\a`）：tmux `set -g set-clipboard on`、vim `set clipboard=unnamed`
  这类场景不用改任何配置，默认就能用。
* **读取**（`\e]52;c;?\a`）：只在 `read-write` 下响应，返回 `OSC 52 ; c ; <base64> ST`；
  请求里的选择目标（`c` / `p` / `cp` …）会原样回显。响应上限 100KB（和写入上限一致），
  避免把超大内容写进 pty 卡住终端。
* **默认不允许读取**是刻意的：读取是无提示的，SSH 到别人的机器后，远端程序可以把你剪贴板里
  的密码/私钥读走。需要时再开。
* `off` 适合完全不信任 OSC 52 的场合（例如只在本地用、或者经常连不可信主机）。
* 非法 base64 / 缺参数的 OSC 52 只记一条日志，不影响终端。

实现位置：`TerminalEmulator.doOsc()` 的 `case 52`（区分写入与 `?` 读取）、`TerminalBase64`
（terminal-emulator 内的纯 Java Base64，因为 `android.util.Base64` 在 JVM 单测里是个空壳）、
`TerminalOutput.getClipboardText()` / `TerminalSessionClient.getClipboardText()`（默认返回
`null`，插件里的其他 client 不需要改动）、`TermuxTerminalSessionActivityClient.onCopyTextToClipboard()`
/ `getClipboardText()`、`TermuxPropertyConstants.KEY_TERMINAL_OSC52_CLIPBOARD` +
`TermuxSharedProperties.isTerminalOsc52ClipboardWriteAllowed()` / `...ReadAllowed()`。

---

## 17. 命令标记 / shell 集成（OSC 133）

上游没有 shell 集成，所以终端不知道「一条命令从哪里开始、到哪里结束、是否失败」。装上随 App
打包的 shell 集成后，命令会发出 `OSC 133;A`（提示符开始）、`OSC 133;C`（命令开始执行）、
`OSC 133;D;<退出码>`（命令结束），Termux 就能：

* **跳到上一条 / 下一条命令**：长按菜单 →「上一条命令 / 下一条命令」，或把音量键动作设成
  `prev-command` / `next-command`；跳到下一条时如果已经没有更下面的命令，会回到当前提示符。
* **右边缘命令刻度**：每条命令在终端右侧画一个小竖条，成功是半透明前景色、失败是红色、
  正在运行是更淡的颜色。**点刻度可以直接跳到那条命令**（右侧 16dp 是触摸区，不影响选择文本）。
* **失败命令提示**：命令退出码非 0 时，优先弹右上角的**通知卡片**（§20，需要
  `notification-enabled = true`），否则回退到上游那个短 toast（`命令退出码 127`）。注意 `grep`、
  `diff`、`test` 这类命令按设计会返回非 0，卡片可以用 `notification-exit-code-ignore-commands` 过滤掉。
* **命令行上报（脚本 v2）**：集成的 `OSC 133;D;<退出码>` 后面多带一个参数
  `;D;<退出码>;<命令行>`，其它终端会忽略它，Termux 用它把命令行显示在通知卡片里（bash 用
  `builtin history 1` 取，zsh 用 `preexec` 的 `$1`，都不改 `PS1`）。控制字符会被替换成空格，多行
  命令因此合并成一行。**从旧版本升级后需要在设置页点一次「安装 shell 集成」重新写入脚本**，否则
  卡片里只能看到退出码（设置页会自动把安装项标成「已安装的 shell 集成是旧版本」）。
* 标记存放在**行对象**里（`TerminalRow#mCommandMark`），所以滚动、回看、改变行数都跟着文字走；
  改变列数的 reflow 也会把标记带到新行（只保留进行中命令的退出码这件事会丢，见 DECISIONS）。
* 标记以**提示符行**为锚点（`OSC 133;A` 所在行），所以跳转/刻度落在 `user@host:~$ ls` 这一行；
  如果 shell 只发 `C` 不发 `A`，则回退到输出首行。

### 安装 / 卸载

设置 → Termux → **终端视图** → **Shell 集成**：

* **安装 shell 集成**：把 `~/.termux/shell-integration.bash` 和 `.zsh` 写入，并在 `~/.bashrc`
  末尾追加一段带 `# >>> termux-shell-integration >>>` 标记的块（`~/.zshrc` 存在时才写它），
  内容只有一行 `[ -f ... ] && . ...`。**不会**修改 `PS1`，也不会覆盖已有的 `PROMPT_COMMAND`/`PS0`
  （只前置/保留），重复安装不会重复追加。
* **卸载 shell 集成**：删除那段标记块和脚本文件，rc 文件的其它内容保持不动。
* 装完要**重开会话**或执行 `source ~/.bashrc` 才生效（当前会话里实时生效需要手动 source）。
* bash 集成用 `PS0` 发 `C`（bash 4.4+）；更老的 bash 仍然有提示符和退出码标记，只是没有命令开始
  标记。zsh 集成用 `precmd`/`preexec` 钩子（`add-zsh-hook`，与已有钩子共存）。
* 通过 **tmux** 运行时，tmux 可能会吞掉这些 OSC 序列（`allow-passthrough` 等），没有做特殊处理。

实现位置：`TerminalEmulator.doOsc()` 的 `case 133`、`TerminalBuffer.markPromptStart()` /
`markCommandStart()` / `markCommandFinished()`（`COMMAND_MARK_*` 位与行内序列号防行复用）、
`TerminalRow.mCommandMark`（`clear()` 时清零）、`TerminalRenderer` 右边缘刻度、
`TerminalView.scrollToPreviousCommand()` / `scrollToNextCommand()` / 刻度点击（16dp 触摸区）、
`TerminalSessionClient.onCommandFinished()`（默认空实现，插件不受影响）、
`TermuxTerminalSessionActivityClient.onCommandFinished()`（失败 toast）、
`TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PREV_COMMAND/NEXT_COMMAND`、
`app/src/main/res/raw/termux_shell_integration_{bash,zsh}.sh`、`TermuxShellIntegration`（安装/卸载）、
`TerminalViewPreferencesFragment`（设置项）。

---

## 18. 分屏（上下 / 左右）

长按菜单 →「分屏（上下）/ 分屏（左右）」，已经分屏时菜单里多一个「关闭分屏」。两个窗格是
**两个独立的会话**，互不影响：

```
┌───────────────────────────┐
│ pane 0（当前活动窗格，有 1dp 边框）│
├───────────────────────────┤  ← 6dp 分隔条，可拖动改变比例（20%–80%）
│ pane 1                    │
├───────────────────────────┤
│ ESC TAB CTRL ALT  ← →     │  ← 工具栏跟随活动窗格
└───────────────────────────┘
```

* **开分屏时**第二个窗格显示一个「当前没被第一个窗格显示」的运行中会话；只有一个会话时会新建一个
  （受 8 个会话上限限制，超了会提示）。关闭分屏**不会**杀掉第二个窗格里的进程，它仍在会话抽屉里。
* **点哪个窗格，哪个就成为活动窗格**：软键盘、extra keys、长按菜单、搜索、新建/切换会话、
  音量键动作、剪贴板粘贴全部作用于活动窗格；活动窗格画 1dp 边框，会话抽屉的选中项也跟着走。
* 在会话抽屉里选中「另一个窗格正在显示」的会话时，两个窗格的会话**互换**（一个会话不能同时挂在
  两个终端视图上，否则两边网格尺寸不同会互相 resize）。
* 分隔条可拖动（40ms 节流，拖动时两个会话各自收到 `SIGWINCH`），比例在 20%–80% 之间；
  上下/左右切换时保留比例。
* 只有活动窗格的会话会收到 `DECSET 1004` 焦点事件（切窗格时旧窗格发失焦、新窗格同步当前焦点）。
* **两个窗格都是实时刷新的**：非活动窗格收到输出同样会重绘（不是挂起/延迟到切回来才画），
  但重绘节流到 **50ms**（`INACTIVE_PANE_REDRAW_INTERVAL_MS`，0 = 每帧都画），避免刷屏的
  非活动窗格抢主线程。非活动窗格的重绘**保持滚动位置**（往上翻看时内容在视口下滚动，不会被
  拽回底部），也不更新无障碍描述（`getText()` 很贵）；点中它之后下一次输出仍按上游语义回到底部。
  分隔条拖动、互换会话、开关分屏、从后台回前台都会立即补刷一次。
* 分屏状态**不持久化**：Activity 重建（换主题等）后回到单窗格，但两个会话都还在服务里。

实现位置：`activity_termux.xml`（`terminal_panes_container` + `terminal_pane_0/1` +
`terminal_split_divider` + 第二个 `TerminalView`）、`app/.../terminal/TerminalSplitScreenController.java`
（开关、方向、窗格权重、分隔条拖动、活动窗格边框）、`TermuxActivity`
（`setSplitScreen()`（选会话/挂载会话）/ `setActiveTerminalPane()` /
`swapTerminalPaneSessions()` / `setTerminalPadding()` 现在作用于两个窗格）、
`TermuxTerminalViewClient`（每个窗格一个实例，所有回调作用于**自己的**视图；
`setSoftKeyboardState()` 只允许活动窗格抢焦点）、
`TerminalExtraKeys.setTerminalView()`（extra keys 点击时改投活动窗格）、
`TermuxTerminalSessionActivityClient`（字体/字号应用到所有窗格视图；`setCurrentSession()` 处理互换；
`onTextChanged()` 按 `getTerminalViewForSession()` 分发到对应窗格，活动窗格走 `onScreenUpdated()`、
非活动窗格走 `TerminalView#onScreenOutput()` / `onScreenOutputThrottled()`）。

---

## 19. 状态栏（`status-bar-*`）

类似 GNOME 顶栏 / 桌面任务栏插件的常驻信息栏，显示设备状态，**默认关闭**。所有值都用 App 侧的
系统 API 采集（不依赖 `termux-api` 插件，也不需要 root）：

```
┌────────────────────────────────────────────┐
│ 🕒12:34  🔋85%  🧠3.2/11G  ⚙23%  ⇅1.2M/s │  ← status-bar-height=24dp
├────────────────────────────────────────────┤
│ user@host:~$                               │
```

* **段（segment）**：`clock` 时间、`battery` 电量/温度/充电状态、`memory` 内存、`cpu` CPU 使用率、
  `net` 网络速率/累计流量、`ip` IP 地址、`proxy` 系统代理。`status-bar-segments` 决定**显示哪些、按什么顺序**。
* **位置**：`status-bar-position=top|bottom`。**占位式**（默认）把状态栏的高度加到终端区的
  padding 上，终端视图因此**真的变小**（`tput lines` 变少、程序收到 `SIGWINCH`），而不是被遮住或
  被挤压；`status-bar-overlay=true` 则浮在终端上方/下方、**不改变行列数**（会遮住顶部/底部若干行）。
* **层级**：状态栏位于会话抽屉的**内容区**里（`DrawerLayout` → `FrameLayout#terminal_content_container`
  → 状态栏 + 终端区），所以**抽屉永远画在状态栏之上**，打开侧边栏时不会被状态栏挡住；软键盘弹出、
  分屏、旋转、改 `terminal-padding-*` 时都跟着内容区自适应，不需要额外刷新。
* **样式**：高度、字号、左右内边距、对齐（`start`/`center`/`end`）、段间分隔符、图标开关与图标在文字前/后。
  每段还可以：自定义格式、加标签、仅文字 / 仅图表 / 文字 + 图表（`Sparkline` 折线图，32 个采样点）、
  单独配色、单独刷新间隔、超阈值变色、点击动作。
* **配色联动**：颜色值支持 `theme:<name>`，直接取当前 `colors.properties` 配色方案里的颜色
  （`background`、`foreground`、`cursor`、`color0`–`color255`），所以改用 Termux:Styling 换配色后
  状态栏会自动跟着变；也可以写 `#aarrggbb` 固定颜色。段没设颜色就跟随 `status-bar-foreground-color`。

### 配置（`~/.termux/termux.properties`）

```properties
# 总开关 / 位置 / 是否浮层
status-bar-enabled = true
status-bar-position = top          # top | bottom
status-bar-overlay = false         # true = 浮在终端上，不占行列

# 外观
status-bar-height = 24             # dp，14–120
status-bar-font-size = 11          # sp，5–32
status-bar-padding-horizontal = 8  # dp，0–64
status-bar-align = start           # start | center | end
status-bar-separator = "  "        # 段之间的字符串，支持 \t \n
status-bar-show-icons = true
status-bar-icons-position = start  # start（文字前）| end（文字后）

# 配色（空 = 跟随配色方案；支持 theme:background / theme:color4 等）
status-bar-background-color = theme:background
status-bar-foreground-color = theme:foreground

# 显示哪些段、什么顺序（未知 id 会被忽略并记日志）
status-bar-segments = clock,battery,memory,cpu,net,ip,proxy

# 每段：enabled / format / label / color / style / action / interval / warn-above / warn-below / warn-color
status-bar-segment-battery-format = %p
status-bar-segment-battery-action = battery-settings
status-bar-segment-battery-warn-below = 20
status-bar-segment-battery-warn-color = #ffff5252
status-bar-segment-cpu-style = text-chart
status-bar-segment-net-format = ↓%rx ↑%tx (%rxTotal/%txTotal)
status-bar-segment-net-interval = 1000
status-bar-segment-ip-format = %if %4
status-bar-segment-proxy-format = %t %p
```

### 各段的格式占位符

| 段 | 占位符 |
| --- | --- |
| `clock` | Java `SimpleDateFormat` 模式，默认 `HH:mm`（如 `HH:mm:ss`、`E HH:mm`） |
| `battery` | `%p` 百分比、`%t` 温度（°C）、`%s` 充电状态（charging/discharging/full/not-charging） |
| `memory` | `%p` 百分比、`%u` 已用、`%a` 可用、`%t` 总量（自动 G/M/K） |
| `cpu` | `%p` 使用率、`%f` 当前平均频率、`%m` 最高频率、`%c` 核心数 |
| `net` | `%rx` 下行速率、`%tx` 上行速率、`%rxTotal` 累计下行、`%txTotal` 累计上行 |
| `ip` | `%4` IPv4、`%6` IPv6、`%i` 首选地址、`%if` 网卡名（`wlan0` / `rmnet_data0` …） |
| `proxy` | `%p` 主机:端口、`%h` 主机、`%o` 端口、`%t` 类型（`none` / `http` / `pac`） |

`%%` 输出一个 `%`；未知占位符原样保留。`label` 会显示在数值前面（如 `label=CPU` → `CPU 23%`）。

### 点击动作（`status-bar-segment-<id>-action`）

`none`（默认）、`wifi-settings`、`battery-settings`、`data-settings`、`app-settings`、`new-session`。

### 设置页

设置 → Termux → **状态栏**：总开关、位置、覆盖模式、高度/字号/内边距滑条、对齐、图标开关与位置、
分隔符、段列表、配色（含 `theme:xxx` 手输）、以及 7 个段各自的分组（开关/格式/标签/样式/动作/颜色）。
改完返回终端立即生效，不需要重启 App。

### 实现注意（踩过的坑）

* 状态栏的属性**不走** `TERMUX_APP_PROPERTIES_LIST` 那套类型化解析，所以读配置必须用
  `TermuxAppSharedProperties.getProperties(false)`（直接读文件）。`getProperties(true)` 返回的内存缓存
  只包含 `TERMUX_APP_PROPERTIES_LIST` 里的键，`status-bar-*` 会被过滤掉，表现为"文件里配了但界面不显示"。
* `StatusBarView` 是被布局 XML 引用的，必须有 `(Context, AttributeSet)` 构造函数，否则 `setContentView()`
  直接抛 `InflateException`（有单测 `CustomViewConstructorTest` 守着）。
* 位置为 `bottom` 时，状态栏只在**终端工具栏可见**时锚到工具栏，否则用 `ALIGN_PARENT_BOTTOM`
  （`RelativeLayout` 会丢弃锚点是 `GONE` 的规则）。

### 数据来源与已知限制

* **CPU**：优先读 `/proc/stat` 两次采样算使用率；**Android 9+ 对 App 屏蔽了 `/proc/stat`**
  （本机实测 `Permission denied`），此时自动回退到 `/sys/devices/system/cpu/cpufreq/policy*/` 的
  `scaling_cur_freq / cpuinfo_max_freq` 作为「频率占用率」近似值；两者都不可用时显示 `-`。
* **内存**：读 `/proc/meminfo` 的 `MemTotal - MemAvailable`（比 `ActivityManager.MemoryInfo` 更贴近实际），
  读不到才回退 `ActivityManager`。
* **网络**：`TrafficStats.getTotalRxBytes/TxBytes()`，是**整机**流量（含其它 App），不是 Termux 进程的；
  速率由两次采样时间差算出，首次显示 `-`。
* **IP**：`ConnectivityManager.getLinkProperties(activeNetwork)`，读不到时枚举网卡；手机数据网络的
  IPv6 会显示完整地址（可能很长，建议 `%4`）。**不显示 SSID**（需要额外 `ACCESS_WIFI_STATE` 权限）。
* **代理**：`ConnectivityManager.getDefaultProxy()`（API 23+，含 PAC 判定），回退 `http.proxyHost`。
  只反映系统全局代理，不反映 shell 里的 `http_proxy` 环境变量。
* **电量**：读 `ACTION_BATTERY_CHANGED` 的 sticky intent；Android 14 起非前台 App 可能取不到，此时显示 `-`。
* 刷新是**分段的**：时间 1s、CPU/网络/内存 2–3s、电量 10s、IP/代理 5s（都可用
  `status-bar-segment-<id>-interval` 覆盖），Activity 不可见时定时器停掉，不耗电。
* 上下分屏时状态栏作用于整个窗口（不在每个窗格各画一条）。

实现位置：`app/src/main/java/com/termux/app/statusbar/`（`StatusBarConfig` 解析属性、`StatusBarColors`
解析 `theme:` 配色、`StatusBarMetrics` 采集系统数据、`SparklineView` 折线图、`StatusBarView` 视图与
分段布局、`StatusBarController` 定时刷新）、`activity_termux.xml`（`terminal_content_container` 里的 `status_bar_view`）、
`TermuxActivity`（`setUpStatusBar()` / `updateStatusBarLayout()` / `setTerminalPadding()` /
`onStatusBarSegmentClicked()`，
以及在 `onStart`/`onStop`/`reloadActivityStyling`/`updateExtraKeysViewColors` 里的挂钩）、
`TermuxPropertyConstants`（`status-bar-*` 常量）、`StatusBarPreferencesFragment` +
`xml/termux_status_bar_preferences.xml`（设置页）、`ColorPreference`（支持手输 `theme:xxx`）、
`res/drawable/ic_status_*.xml`（7 个手绘矢量图标）、`app/src/test/.../StatusBarTest.java`（12 个用例）+ `CustomViewConstructorTest`（校验布局里的自定义 View 都有 `(Context, AttributeSet)` 构造函数，防止 `InflateException` 崩溃）。

---

## 20. 终端内通知卡片（`notification-*`）

类似 `nvim-notify` / 桌面通知的**右上角卡片浮层**，用来替代「命令失败只弹一个系统 toast」的做法。
浮动在终端之上、**不改变终端行列数**（和搜索栏、状态栏同一层，都在会话抽屉的内容区里，所以抽屉
永远画在卡片之上）。

```
┌────────────────────────────────────┐
│ user@host:~$ make -j8              │
│ src/a.c:12:5: error: ...           │
│                   ╭──────────────╮ │
│                   │▌ 退出码 1     │ │  ← 左侧 4dp 色条（失败=color1）
│                   │▌ make -j8    │ │
│                   │▌ ~/repo ·12s │ │
│                   ╰──────────────╯ │
│ user@host:~$ _                     │
└────────────────────────────────────┘
```

### 配置（`~/.termux/termux.properties`）

```properties
notification-enabled = false        # 总开关，默认关（关时一切通知都不显示，行为与上游一致）

# 外观
notification-position = top-end     # top-end | top-start | bottom-end | bottom-start
notification-max-visible = 3        # 1–8，超出的先关最旧的一条
notification-duration = 2000        # 毫秒，0 = 不自动关闭（上限 120000）
notification-width = 0              # dp，0 = 自适应（上限 320dp）
notification-font-size = 13          # sp，标题字号（正文 -1sp、详情 -2.5sp，9–24）
notification-corner-radius = 0       # dp，0 = 直角（TUI 像素风默认）
notification-elevation = 0           # dp，0 = 无阴影（平铺）
notification-border-width = 1        # dp，整卡描边宽度，0 = 不描边
notification-accent-width = 4        # dp，左侧色条宽度，0 = 不要色条
notification-monospace = true        # 用终端等宽字体
notification-body-max-lines = 2      # 正文最多几行（1–6）
notification-background-color =     # 空 = 配色方案 background 向 foreground 混合 12%
notification-foreground-color =     # 空 = 配色方案 foreground
notification-accent-failure-color =     # 空 = theme:color1（红）
notification-accent-success-color =     # 空 = theme:color2（绿）
notification-accent-warning-color =     # 空 = theme:color3（黄）
notification-accent-info-color =        # 空 = theme:color4（蓝）

# 通道一：失败命令（默认开）
notification-exit-code-enabled = true
notification-exit-code-show-cwd = true
notification-exit-code-show-session = true
notification-exit-code-min-duration = 0          # 秒，>0 时只提示慢命令
notification-exit-code-ignore-commands = grep,test,diff,true,false,rg,fd,[

# 通道二：App 事件（默认关）
notification-events-enabled = false
notification-events = session-exit,bell,title,plugin-result

# 通道三：程序自己发的 OSC 通知（默认关）
notification-osc-enabled = false
notification-osc-allow-app =        # 空/* = 全允许，否则按 OSC 99 的 f= 应用名过滤
notification-osc-report-click = true
notification-osc-icons = false

# 后台兜底与历史（默认关）
notification-background-enabled = false
notification-history-size = 50
```

颜色值支持 `#rgb` / `#aarrggbb` 和 `theme:background`、`theme:foreground`、`theme:color0`–`color255`，
和 `status-bar-*` 的取值规则完全一致（复用 `StatusBarColors`），所以换 Termux:Styling 主题后卡片自动跟着变。

### 怎么先看效果

M8.1 只有渲染层，还没有接真实数据源，所以用测试入口：

1. `~/.termux/termux.properties` 里加 `notification-enabled = true`；
2. `termux-reload-settings`（或在终端外重开 App）；
3. 长按终端 → 菜单里多出「**发送测试通知**」，每点一次轮换显示 失败 / 成功 / 警告 / 信息 四种卡片。

### 失败命令卡片的内容与过滤（M8.2）

```
╭──────────────────────────────╮
│▌ 命令退出码 1                │  ← 标题（复用上游 toast 的文案）
│▌ make -j8                    │  ← 命令行（来自 shell 集成；旧集成则从缓冲区的
│▌ ~/repos/app · 12.3s · bash  │     提示符行里抠，剥掉 PS1 的启发式见下）
╰──────────────────────────────╯
```

* **命令行来源（双路）**：优先用 `133;D;<code>;<cmd>` 上报的文本；没有（旧集成 / 只发退出码的
  shell）就回退到「命令锚点行」的缓冲区文本，并按 `$ `、`# `、`% `、`❯ `、`➜ `、`λ `、`» `、`> `
  这些提示符结尾做截断（只在行首 120 字符内认，避免把命令里的 `$ ` 当提示符）。回退结果可能仍带
  提示符残留，属已知限制。
* **耗时**：`OSC 133;C` 的时间戳（`System.nanoTime()`）到 `133;D` 的差；bash 4.4 以下没有 `PS0`
  就没有 `C` 标记，此时不显示耗时。
* **cwd**：用 `$HOME` 缩写成 `~`，由 `notification-exit-code-show-cwd` 控制；会话名由
  `notification-exit-code-show-session` 控制（只显示用户重命名过的会话）。
* **过滤**：`notification-exit-code-ignore-commands`（默认 `grep,test,diff,true,false,rg,fd,[`）
  按「命令名」匹配（去掉路径、跳过 `env`/`command`/`exec` 等前缀）；`notification-exit-code-min-duration`
  （秒）用来只在慢命令失败时提示。
* **点卡片 = 跳到那条命令**：命令行标记里存了**命令序列号**，点卡片时用
  `TerminalView.scrollToCommandSequence(sequence)` 精确定位（不是「上一条命令」，即使之后又跑了
  别的命令也不会跳错）；命令来自别的会话时先切会话，分屏时切到显示该会话的窗格。跳不到（缓冲区
  已被顶掉）就退回 `scrollToPreviousCommand()`。

### 默认外观：TUI 像素风

默认值刻意做成「像终端的一部分」而不是 Material 卡片：**直角（`notification-corner-radius = 0`）、
无阴影（`notification-elevation = 0`）、1dp 强调色描边、4dp 左侧色条、等宽字体**，背景是配色
`background` 向 `foreground` 混 8% 的纯色块。想换成圆角卡片风只要：

```properties
notification-corner-radius = 8
notification-elevation = 6
notification-border-width = 0
notification-accent-width = 4
```

### 实现位置（M8.1 / M8.2）

* `terminal-emulator/.../TerminalNotification.java`：通知模型（kind/source/title/body/meta/expiry id 等），
  不含 android view，方便给 OSC 解析器和事件生成器写单测。
* `app/.../notifications/NotificationConfig.java`：解析 `notification-*`（同 `status-bar-*`，
  **不进** `TERMUX_APP_PROPERTIES_LIST`，必须用 `getProperties(false)` 直读文件）。
* `app/.../notifications/NotificationOverlayView.java`：卡片栈、角落定位、最大条数、自动消失、
  `notification-duration` 与 `OSC 99 w=` 共存、颜色解析（`theme:`）。
* `app/.../notifications/NotificationCardView.java`：单张卡片（左侧色条 + 标题/正文/详情 + ✕），
  点按 = 触发动作、左右滑动 = 关闭、`clipToOutline` 让色条跟随圆角。
* `app/.../notifications/TerminalNotificationManager.java`：读配置、按通道开关决定是否显示。
* `app/src/main/res/layout/activity_termux.xml`：`notification_overlay`（放在 `terminal_content_container`
  里、状态栏之后，所以画在状态栏之上，用 `setStatusBarInsetPx()` 让开状态栏高度）。
* `TermuxActivity`：`setUpNotificationOverlay()` / `updateNotificationOverlayLayout()` /
  `sendTestNotification()` / `jumpToCommand()` / `getTerminalNotificationManager()`、长按菜单项、
  `reloadActivityStyling` 里重载配置。
* `app/.../notifications/ExitCodeNotifier.java`：把 `TerminalCommandResult` 变成卡片（命令行、
  耗时、cwd、会话名、忽略名单、最小耗时、点击动作）。
* `terminal-emulator/.../TerminalCommandResult.java`：`133;D` 的解析结果（退出码 / 命令行 / 耗时 /
  命令序列号）；`TerminalBuffer` 记录命令起始时间、`getCommandTextForSequence()`、
  `getCommandMarkSequence()`；`TerminalSessionClient` 新增
  `onCommandFinished(session, TerminalCommandResult)`（默认转调旧的 `int` 版本，插件无感）。
* `terminal-view/.../TerminalView.java`：`scrollToCommandSequence(int)`。
* `app/src/main/res/raw/termux_shell_integration_{bash,zsh}.sh`：`D` 带上命令行（脚本 v2），
  `TermuxShellIntegration.isInstalledVersionOutdated()` 用于提示重新安装。

---

## 21. 可配置的长按菜单 / 文本选择浮条 / `SEARCH`·`SPLIT_*` 虚拟键

### 21.1 长按菜单改成可配置的自定义对话框（`terminal-menu-items`）

上游长按文字 → 选字 → 系统 `ActionMode` 浮条（复制/粘贴/更多…）→ 点「更多…」才弹系统 context
menu（一长串）。本 fork 把「更多…」那个菜单换成**自己画的对话框**：

* **样式**：直角、1dp 强调色描边、等宽字体、右侧小图标，背景/文字色默认取 `colors.properties`
  （`theme:background` / `theme:foreground` / `theme:color4`），换 Termux:Styling 主题自动跟随。
* **项与顺序可配**：`terminal-menu-items` 一条逗号列表，**顺序就是显示顺序**，不写就不显示。
* **不可用的项自动隐藏**：`split-close` 只在已分屏时出现；`share-selected-text` 只在有选中文本时
  出现；`autofill-*` 只在终端视图开启 autofill 时出现；`test-notification` 只在通知卡片开启时出现。
* **`kill-process`** 的标题带会话 PID，会话已结束时置灰。

```properties
# 默认值（11 项）
terminal-menu-items = search,previous-command,next-command,split-vertical,split-horizontal,select-url,share-transcript,reset-terminal,kill-process,settings,report

# 全部可用 id（新增的靠后，默认不显示，需要就自己加）：
#   search / previous-command / next-command
#   split-vertical / split-horizontal / split-close
#   select-url / share-transcript / share-selected-text
#   autofill-username / autofill-password
#   reset-terminal / kill-process / styling / keep-screen-on
#   help / settings / report / test-notification

# 样式
terminal-menu-font-size = 13        # sp，8–24
terminal-menu-corner-radius = 0     # dp，0 = 直角
terminal-menu-border-width = 1      # dp，0 = 不描边
terminal-menu-monospace = true      # 等宽字体
terminal-menu-show-icons = true     # 每项右侧的符号
terminal-menu-background-color =    # 空 = 配色 background 混 10% foreground
terminal-menu-foreground-color =    # 空 = 配色 foreground
terminal-menu-accent-color =        # 空 = theme:color4（描边 + 按下高亮）
```

实现：`app/.../terminal/menu/TerminalMenuAction.java`（id ↔ 文案 ↔ 图标）、`TerminalMenuConfig.java`
（解析）、`TerminalMenuDialog.java`（对话框）。入口统一到 `TermuxActivity.showTerminalMenu()`，
由 `TerminalView.showTerminalContextMenu()` → `TerminalViewClient.onShowContextMenu()` 钩子触发，
所以鼠标右键、菜单键、`Ctrl+Alt+M`、浮条「更多…」全都走同一套。**旧的系统 context menu
（`onCreateContextMenu`）已删除。**

### 21.2 文本选择浮条可配（`selection-toolbar-items`）

浮条仍然是系统的 `ActionMode`（位置、选择手柄、滚动都由系统管），但**显示哪些项、什么顺序**由属性
决定，并且样式在主题里改成了直角 + 1dp 描边：

```properties
selection-toolbar-items = copy,paste,more     # 默认；可用 id: copy / paste / more
```

* 列表为空或全是未知 id 时**回退到默认三项**，否则用户就复制不了选中的文本了。
* **粘贴项沿用上游语义**：`ClipboardManager.hasPrimaryClip()` 为 false 时该项是 disabled 的。
  注意部分 ROM（实测 ColorOS/Oplus）的浮动 ActionMode 会把**禁用项直接隐藏**而不是画成灰色，
  所以「剪贴板里没有文本时浮条上只有复制/更多」是预期行为，不是 bug。
* 粘贴的目标是**发起粘贴的那个会话所在的窗格**（分屏时选中非活动窗格里的文字也粘对地方），
  找不到窗格时才回退到当前活动窗格。
* 浮条配色只能跟随 App 主题（`actionModeBackground` 是 XML drawable，拿不到运行时
  `colors.properties` 的颜色），这是系统限制；要完全跟随配色方案就得换成自制浮条（未做）。
* 实现：`TerminalViewClient.getTextSelectionToolbarActions()`（默认返回三项）→
  `TextSelectionCursorController` 按返回的顺序建菜单。

### 21.3 虚拟键盘新增特殊键：`SEARCH` / `SPLIT_V` / `SPLIT_H` / `SPLIT_CLOSE`

和 `KEYBOARD`、`DRAWER`、`PASTE`、`SCROLL` 一样由 App 侧处理（`TermuxTerminalExtraKeys`），
写进 `extra-keys` / `extra-keys-panels` 即可用，**默认布局不变**：

```properties
extra-keys = [ \
  ['ESC','/','-','SEARCH','UP','END','PGUP','KEYBOARD'], \
  ['TAB','CTRL','ALT','SPLIT_V','DOWN','SPLIT_H','PGDN','SPLIT_CLOSE'] \
]
```

| 键 | 默认符号 | 行为 |
| --- | --- | --- |
| `SEARCH` | `⌕` | 打开终端内搜索栏并聚焦（等于 `Ctrl+Alt+F`） |
| `SPLIT_V` | `⬓` | 上下分屏；已分屏时**只切换方向**，不关闭 |
| `SPLIT_H` | `◫` | 左右分屏；已分屏时只切换方向 |
| `SPLIT_CLOSE` | `✕` | 关闭分屏 |

符号可以自己换（extra keys 本来就支持 `display`）：

```properties
extra-keys = [['SEARCH', {key: 'SPLIT_V', display: 'V'}, {key: 'SPLIT_H', display: 'H'}]] 
```

---

## 22. 设置页 GUI：通知 / 长按菜单

设置 → Termux 根页新增两个入口，**和 `termux.properties` 里的属性是同一份配置**（读写同一批 key），
所以手写文件和 GUI 可以随便混用。改完返回终端立即生效（走 `TermuxPropertyPreferenceDataStore`
→ `updateTermuxActivityStyling(false)` → `reloadActivityStyling()` → `mNotificationManager.reload()` /
`reloadTerminalMenuConfig()`），不需要重启 App。

### 22.1 设置 → 通知（`NotificationPreferencesFragment`）

```
通用     总开关 / 显示位置（四角下拉）/ 自动消失时间（1·2·3·5·10·30 秒、常驻）/ 最大条数 1–8
外观     宽度（0=自适应）/ 字号 / 命令行最多行数 / 圆角 / 描边宽度 / 色条宽度 /
         等宽字体 / 背景色·文字色·4 个强调色（ColorPreference，支持 theme:color4）
失败命令 通道开关 / 显示 cwd / 显示会话名 / 最短耗时（秒）/ 忽略命令（逗号分隔，可弹框编辑）
程序通知 通道开关 / 允许的程序 / 回报点击与关闭 / 显示图标 / 每秒每会话最多几条
App 事件 通道开关 / 事件多选（会话退出·插件结果·标题变化·bell）
后台与历史 后台系统通知开关 / 最近通知保留条数 0–200
```

「自动消失时间」用**预设下拉**（值仍是毫秒，写进 `notification-duration`），默认 2 秒。
想验证外观：长按终端 → 菜单里的「发送测试通知」（只在通知开启时出现）会轮换显示 4 种卡片。

### 22.2 设置 → 长按菜单（`TerminalMenuPreferencesFragment`）

```
动作  长按菜单项（双区编辑器）/ 文本选择浮条项（双区编辑器，至少保留 1 项）
外观  字号 / 圆角 / 描边宽度 / 等宽字体 / 显示图标 / 背景色·文字色·强调色
```

**双区列表编辑器**（`OrderedItemsPreference`，`TerminalMenuItemsPreference` /
`SelectionToolbarItemsPreference` 两个子类）：

```
╭──────────────────────────────────╮
│  已显示（按此顺序）                │
│  搜索          ⌕        ▲ ▼ ✕   │   ← ▲▼ 调序、✕ 移除
│  上一条命令     ↑        ▲ ▼ ✕   │
│  ────────────────────────────    │
│  未显示，点一下添加                │
│  + 分屏（上下）   ⬓               │   ← 点一下加到最后
│  + 选择 URL       ↗               │
╰──────────────────────────────────╯
```

* 顺序就是 `terminal-menu-items` / `selection-toolbar-items` 里 id 的顺序，按 OK 才写入文件。
* 浮条那一份**至少保留一项**（只剩一项时 ✕ 按钮不显示），否则就复制不了选中的文本了；长菜单允许清空。
* 概要行（summary）显示当前已显示的项，方便不打开对话框就能看出配置。

### 实现注意（踩过的坑）

* **`app:min` / `app:max` 不存在**：androidx.preference 1.2.1 的 `SeekBarPreference` 用的是
  `android:min` / `android:max`（跟「状态栏」页一致）。写成 `app:max` 会在 `processDebugResources`
  直接 fail（`attribute max not found`）。
* **AGP 8 非传递 R**：`terminal-view` 里的 `copy_text` / `paste_text` / `text_selection_more` 在 app
  的 `R` 里找不到，要写 `com.termux.view.R.string.*`（和 DECISIONS 里颜色资源那个坑同源）。
* **`setSummary()` 会 `notifyChanged()`**：在 `onBindViewHolder()` 里调用 `setSummary()`，即使先比较
  新旧值也**依然会崩溃**——`Preference.onBindViewHolder()` 期间 RecyclerView 正在算布局，`notifyChanged()`
  会抛 `IllegalStateException: Cannot call this method while RecyclerView is computing a layout or
  scrolling`（真机实测）。正确做法是**重写 `getSummary()`**（`Preference.onBindViewHolder()` 读的是
  `getSummary()`，纯读不会触发通知），对话框按 OK 后再手动 `notifyChanged()`。
* `values-zh/arrays.xml` **不要**重复声明 `translatable="false"` 的数组；`entries` 数组里引用的是
  `@string/...`，会自动按当前语言解析，所以中文不需要单独一份。
* 「发送测试通知」没有放进设置页：卡片属于正在运行的 `TermuxActivity`，在设置页里触发用户也看不见，
  所以仍然只放在长按菜单里（`test-notification` 项）。

---

## 23. 程序主动发通知：OSC 99 / OSC 9 / OSC 777

任何程序都能让终端弹通知卡片，这是这套卡片真正的「通用通知渠道」（失败命令只是它的一个数据源）：

```sh
# osc9（iTerm2 风格，只有一句正文）
printf '\033]9;Build finished\007'

# osc777（urxvt 风格，标题 + 正文）
printf '\033]777;notify;make;3 targets failed\007'

# osc99（kitty 协议，功能最全）
printf '\033]99;i=build-1:p=title;Build finished\007'
printf '\033]99;i=build-1:p=body;3 targets failed: a.o b.o c.o\007'
printf '\033]99;i=build-1:u=2:p=buttons;Retry\u2028Show log\007'
```

> 注意：`\u2028` 是按钮之间的分隔符（Unicode LINE SEPARATOR）。在 zsh/bash 里可以用
> `printf '\342\200\250'` 或 `$'\u2028'` 生成。

### 支持的 OSC 99 元数据

| 键 | 是否实现 | 说明 |
| --- | --- | --- |
| `i=` | ✅ | 通知 id：相同 id 的第二条会**更新**同一张卡片；`p=close` 用它关卡片；回报里原样带回 |
| `d=0/1` | ✅ | 分块：`d=0` 的块先攒着，`d=1`（或省略）时成一条通知 |
| `e=1` | ✅ | 负载是 base64（分块时按 kitty 的做法「先编码再切块」，最后整体解码） |
| `p=title/body/buttons/icon/close/?/alive` | ✅ | 见下 |
| `u=0/1/2` | ✅ | 紧急度：`2` → 红（同失败命令），其余 → 蓝 |
| `w=<ms>` | ✅ | `-1` 用 `notification-duration`，`0` 常驻，`>0` 指定毫秒 |
| `c=1` | ✅ | 卡片关闭时回报 `p=close`（需 `notification-osc-report-click=true`） |
| `a=report` | ✅ | 点击卡片/按钮时回报（需 `notification-osc-report-click=true`） |
| `o=always/unfocused/invisible` | ✅ | `unfocused`/`invisible` 时只在窗口失焦/不可见时才显示 |
| `f=` / `t=` | ✅ | 应用名 / 类型（base64）；`f` 用于 `notification-osc-allow-app` 过滤 |
| `s=` `n=` `g=` | ⛔ | 声音名、系统图标名、图标缓存键：解析但忽略（卡片不播音效，也不用系统图标主题） |

`p=buttons` 的按钮会渲染成卡片底部一排直角小按钮，点击回报 `OSC 99 ; i=<id> ; <按钮序号>`。
`p=icon`（PNG/JPEG，≤ 64KB 编码后）由 `BitmapFactory` 解码，`notification-osc-icons=true` 时在卡片里
显示成 16dp 图标；否则解码后丢弃。`p=?` 的支持查询会被**立即回应**（宣告 `a`,`c`,`o`,`p`,`s`,`u`,`w`），
`p=alive` 会用当前仍在显示的卡片 id 回应，`p=close` 直接关掉对应卡片。

### 防刷屏与过滤

```properties
notification-osc-enabled = false     # 总开关（默认关）
notification-osc-allow-app =         # 空或 * = 允许所有；否则按 OSC 99 的 f= 应用名（大小写不敏感）
notification-osc-report-click = true # 是否把点击/按钮/关闭回报给程序
notification-osc-icons = false       # 是否显示 p=icon 图标
notification-osc-rate-limit = 3      # 每秒每会话最多几条，0 = 不限；超出的直接丢弃并 log
```

限流是**每会话**的滑动窗口（1 秒窗口），所以一个程序刷屏不会把终端主线程卡死，也不会影响别的会话。

### 已知限制

* 单块负载超过 128KB 会整条丢弃（协议本身建议 ≤2048 字节/块）。
* 通过 tmux 时，tmux 可能吞掉 OSC 99（需要 `allow-passthrough` 等设置），未做特殊处理。
* 卡片只在 App 窗口里显示，所以 `o=unfocused`/`o=invisible` 的实现是「窗口聚焦/可见时不显示」，与后台通知（M8.5）无关。
* 回报写入的是**发起通知的那个会话**的 pty，所以点别的会话发来的卡片也能正确回报。

### 实现位置

* `terminal-emulator/.../TerminalNotificationAssembler.java`：OSC 99 状态机（分块/元数据/base64/按钮/图标/查询/关闭/alive）、OSC 9、OSC 777。
* `terminal-emulator/.../TerminalNotificationProtocol.java`：所有回写序列（点击/按钮/关闭回报、`p=?` 与 `p=alive` 应答、id 净化）。
* `terminal-emulator/.../TerminalEmulator.java`：`doOsc()` 的 `case 9 / 99 / 777`；`TerminalOutput` / `TerminalSession` / `TerminalSessionClient` 新增 `onTerminalNotification` / `onOsc99Close` / `onOsc99AliveQuery`（都是默认空实现，插件不受影响）。
* `app/.../notifications/OscNotificationHandler.java`：开关/白名单/occasion/限流、回报、alive 应答。
* `app/.../notifications/NotificationCardView.java`：按钮行 + 16dp 图标；`TerminalNotificationManager` 的 `NotificationActions`（点击/按钮/关闭三种回调）。
* 设置页：`xml/termux_notification_preferences.xml` 的「程序主动发送的通知」分类。
* 单测：`TerminalNotificationAssemblerTest`（24 个用例：分块、base64、按钮、元数据、查询/关闭/alive、OSC 9/777、id 净化、控制字符）。

---

## 24. App 事件卡片 / 后台系统通知 / 最近通知

### 24.1 App 事件（`notification-events`）

默认关。开启后，这些事件会走卡片，并且**不再弹原来的 toast**（避免双提示）：

| 事件 id | 卡片内容 | 说明 |
| --- | --- | --- |
| `session-exit` | 标题「会话已退出」，正文是会话名/PID，详情行是 cwd（非 0 时加上退出码） | 非 0 退出码用**黄色**（warning）强调色；只对**非当前会话**弹 |
| `plugin-result` | 标题「插件命令已结束」 | 插件（Termux:Tasker 等）发起的命令结束；即使跑在当前会话也弹 |
| `title` | 标题「会话标题变化」，正文是新标题 | 只对非当前会话弹（与上游 toast 的条件一致） |
| `bell` | 标题「会话响铃」，正文是会话名 | **保留** `bell-behaviour` 的震动/响铃，只是额外加一张卡片；只对非当前会话 |

```properties
notification-events-enabled = true
notification-events = session-exit,plugin-result,title,bell
```

### 24.2 后台系统通知（`notification-background-enabled`）

默认关。开启后，**App 不在前台**时收到的通知改为发到 Android 通知栏，而不是往终端里塞一张看不见的卡片：

* 走独立通知渠道 `termux-terminal-notifications`（`IMPORTANCE_DEFAULT`），标题/正文取通知的 title/body，
  正文用 `BigTextStyle` 保留换行；点击打开 Termux。
* **不需要任何新权限**：App 的 `targetSdkVersion = 28`，`POST_NOTIFICATIONS` 运行时权限只在
  targetSdk ≥ 33 时才生效；另外发之前会查 `areNotificationsEnabled()`，用户在系统设置里关掉就不发。
* 我最初考虑过走 Termux:API 的 `termux-notification`，但那条路要起一个后台会话跑 CLI，且依赖
  termux-api 包 + 插件 App，所以放弃了（见 DECISIONS）。

```properties
notification-background-enabled = true
```

### 24.3 最近通知（`notification-history-size`，内存列表）

所有显示过的通知（失败命令 / OSC / App 事件）都会记进一个**只在内存里**的环形列表（默认 50 条，
0 = 不记），App 关闭就没了 —— 因为通知正文可能含命令输出，不应该擅自落盘。

* 入口：长按菜单 →「**最近通知**」（`recent-notifications`，已加入默认 12 项；列表为空时该项自动隐藏）
* 对话框：直角 + 1dp 描边 + 等宽字体 + 配色方案，每行显示「时间 · 标题 / 正文」；失败/警告类左侧有强调色竖条
* **点失败命令的条目 = 跳到那条命令**；其它条目（OSC/事件）= **把文字复制到剪贴板**
* 底部「清空」清掉列表并关对话框

### 实现位置

* `app/.../notifications/AppEventNotifier.java`：四个事件 → 卡片（含 `sesion-exit` 的退出码/强调色规则）
* `app/.../notifications/BackgroundNotifier.java`：通知渠道 + 通知构建 + 点击打开 Termux
* `app/.../notifications/RecentNotifications.java`：环形列表（容量裁剪、最新在前、可选点击动作）
* `app/.../notifications/RecentNotificationsDialog.java`：列表对话框（跳命令 / 复制 / 清空）
* `app/.../widget/TerminalDialogStyles.java`：菜单对话框与最近通知对话框共用的配色/描边/行背景/等宽文字
  （M8.3 的 `TerminalMenuDialog` 已改为复用它，去掉了三份重复代码）
* `TerminalNotificationManager`：`notify()` 里记录历史；不可见 + 后台开关 → 改发系统通知
* `TermuxTerminalSessionActivityClient`：`onSessionFinished` / `onTitleChanged` / `onBell` 挂钩（卡片优先，未开启回退 toast）
* 设置页：`termux_notification_preferences.xml` 新增「App 事件」「后台与历史」两个分类；
  `TermuxPropertyPreferenceDataStore` 新增 `putStringSet/getStringSet`（逗号连接，供 MultiSelectListPreference 用）
* 单测：`RecentNotificationsTest`（6 个用例）

---

## 25. 代码结构（fork 的包划分）

fork 新增的代码按功能分包，`TermuxActivity` 只做「视图装配 + 协调」，具体逻辑在各自的类里：

| 包 / 类 | 职责 |
| --- | --- |
| `app/.../notifications/` | 通知卡片全链路：`NotificationConfig`（属性）、`TerminalNotificationManager`（通道开关/历史/后台切换）、`ExitCodeNotifier`（失败命令）、`OscNotificationHandler`（OSC 9/99/777 + 限流 + 回报）、`AppEventNotifier`（App 事件）、`NotificationOverlayView` + `NotificationCardView`（渲染）、`RecentNotifications` + `RecentNotificationsDialog`（历史）、`BackgroundNotifier`（系统通知） |
| `app/.../terminal/menu/` | `TerminalMenuConfig`（`terminal-menu-*` 属性）、`TerminalMenuAction`（id ↔ 文案 ↔ 图标）、`TerminalMenuDialog`（自定义样式的长按菜单） |
| `app/.../terminal/` | `TerminalSearchController`（搜索栏）、`TerminalSplitScreenController`（分屏布局）、`TermuxShellIntegration`、四个 session/view client |
| `app/.../statusbar/` | `StatusBarConfig` + `StatusBarMetrics` + `StatusBarView` + `SparklineView` + `StatusBarColors` + `StatusBarController` |
| `app/.../properties/ForkProperties.java` | fork 自有属性的统一读取入口（`termux.properties` 直读 + 类型化 getter + 越界回退） |
| `app/.../widget/TerminalDialogStyles.java` | 菜单/历史对话框共用的 TUI 风格（直角、1dp 描边、行按下高亮、等宽文字、`theme:` 配色） |
| `terminal-emulator/.../TerminalNotification*.java` | OSC 协议解析（纯逻辑，26 个单测），不含任何 UI 代码 |

---

## 验证情况（真机）
以下各项都在本机（aarch64，Android 13 系 ROM）真机验证过（§11 音量键自定义动作 §14 终端内边距尚未真机验证）：

* §14 终端内边距：**已通过**本地 `./gradlew :app:compileDebugJavaWithJavac` 编译与相关单测，
  但视觉/触摸/文本选择手柄/软键盘光标尚未在真机上验证。
* §15 终端搜索 / §16 OSC 52：**已通过**本地编译；`TerminalBufferSearchTest`（9 个）、
  `TerminalBase64Test`（6 个）、`OperatingSystemControlTest` 的 OSC 52 用例全部通过。
  真机上的搜索栏交互（软键盘、返回键、与长按选择共存）和剪贴板读写尚未真机验证。
* §17 命令标记：`CommandMarksTest`（21 个）全绿；bash 集成在本地用 `bash --norc -i` 起真实交互
  shell 验证过（`D;0`/`D;1;cmd`/`A`/`C` 顺序、退出码与命令行正确）。真机上的刻度点击、失败提示尚未验证。
* §24 App 事件 / 后台通知 / 最近通知：`RecentNotificationsTest` 6 个用例全绿，`:app` 编译与资源链接
  通过；真机上的事件卡片、后台系统通知、最近通知对话框（跳转/复制/清空）尚未验证。
* §23 OSC 通知：`TerminalNotificationAssemblerTest` 24 个用例全绿，`:app` 编译通过；真机上的
  卡片按钮/图标/回报/限流尚未验证（可以用 `printf` 手测）。
* §22 设置页：**已通过** `:app:processDebugResources` + `:app:compileDebugJavaWithJavac` + 单测；
  真机上的控件交互（SeekBar 越界、双区编辑器调序、改完返回是否立即生效）尚未验证。
* §20 通知卡片：M8.1（渲染层）+ M8.2（失败命令通道）**已通过** `./gradlew :app:compileDebugJavaWithJavac`
  与单测——`NotificationConfigTest`（10 个）、`ExitCodeNotifierTest`（10 个）、`CommandMarksTest`（21 个，
  含 `D;<code>;<cmd>` 解析、耗时/序列号、缓冲区取命令行、滚动后仍能定位）。以下**尚未真机验证**：
  卡片观感（圆角/阴影/动画/配色联动）、点击跳转（含分屏与跨会话）、左右滑动关闭、自动消失、
  与搜索栏/状态栏/软键盘叠放。
* §19 状态栏：**只通过本地编译 + 单测**（`StatusBarTest` 12 个用例全绿），尚未真机验证（重点：
  占位/覆盖两种模式下的终端行列数、设置页改完是否立即生效、`theme:` 配色是否跟随 Termux:Styling 换色、
  分段刷新间隔下的耗电与帧率、`/proc/stat` 不可用时的 CPU 回退值是否合理）。
* §18 分屏：**只通过本地编译**，尚未真机验证（重点：点窗格切活动窗格时的键盘、分隔条拖动、
  会话抽屉选中另一个窗格的会话时的互换）。非活动窗格的实时重绘（`onScreenOutput()` / 50ms 节流）
  同样只通过本地编译，真机上要看：非活动窗格刷屏时帧率与主线程占用、往上翻看后不被拽回底部、
  互换/开关分屏/回前台后画面立即是最新的。

* 备份 26s / 333MB / 43023 条目，校验通过；`--target-dir` 解包演练的符号链接与权限位正确。
* 外观页 6 项、按键行为 6 项改完立即生效；打开设置页**不会**改写 `termux.properties`。
* 真斜体（`font-italic.ttf`）与 OSC 8 链接（自动下划线 + 点击打开）正常。
* IME 收起额外按键行、手指拖拽按鼠标拖拽上报、`DECSET 1004` 焦点事件正常。
* 多面板 extra keys 横滑切换、左右滑切会话、会话列表 `PID · 前台进程 · cwd` 正常。
