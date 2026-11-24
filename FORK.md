# Fork 特性说明

本仓库是 [Termux](https://github.com/termux/termux-app) 的 fork。本文档只列出本 fork 相对上游新增或改变的
功能，以及各功能的配置方式。构建、签名与插件共存的说明见 [README.md](README.md)。

配置约定：

1. 属性写入 `~/.termux/termux.properties`，修改后执行 `termux-reload-settings`。文中注明「立即生效」的属性
   由设置页写入后直接生效，不必执行该命令。
2. 除特别说明外，属性均可在「设置 → Termux」中修改；设置页与属性文件读写同一份配置，两种方式可以混用。
3. 新增属性默认关闭或保持上游行为；默认开启的属性在「默认」列注明原因。
4. 颜色属性接受 `#RGB`、`#ARGB`、`#RRGGBB`、`#AARRGGBB`，部分属性另接受 `theme:<名称>`，取当前配色方案
   （`~/.termux/colors.properties`）中的颜色。

---

## 特性总览

| § | 特性 | 配置 | 默认 |
| --- | --- | --- | --- |
| 1 | 输入法兼容模式、硬件键盘直通、键盘诊断日志 | `gboard-cjk-workaround`、`keyboard-compat-log` | 关 |
| 2 | 虚拟键盘配色、激活高亮、按键块背景、上滑提示 | `extra-keys-*`、`extra-keys-tile-background`、`extra-keys-popup-hints` | 配色跟随配色方案；按键块与上滑提示开启 |
| 3 | 多面板虚拟键盘、软键盘隐藏时收起、新增特殊键 | `extra-keys-panels`、`hide-extra-keys-when-soft-keyboard-hidden` | 单面板；不收起 |
| 4 | 音量键自定义动作 | `volume-up-action`、`volume-down-action` 及长按与序列属性 | 上游行为 |
| 5 | 全应用跟随配色方案、外观与按键行为设置页 | `night-mode`、`fullscreen`、`extra-keys-style`、`back-key`、`mouse-tap-shows-keyboard` 等 | 夜间模式跟随系统 |
| 6 | 会话抽屉外观、概要、把手与开合方式 | `drawer-open-mode`、`drawer-handle-*` | 把手与边缘滑动并存 |
| 7 | 左右滑动切换会话、手指拖拽上报、鼠标交互 | `switch-session-on-swipe`、`touch-drag-sends-mouse-drag`、`mouse-hover-sends-mouse-motion`、`mouse-right-click` | 滑切开启，其余关闭 |
| 8 | 分屏 | 长按菜单与 `SPLIT_*` 键 | 默认不启用 |
| 9 | 终端内搜索 | 无 | 按需打开 |
| 10 | 命令面板、终端菜单、选择工具栏、命令快捷键 | `terminal-menu-items`、`selection-toolbar-items`、`command-shortcuts` | 13 项动作；复制、粘贴、更多；四条快捷键 |
| 11 | 命令标记、shell 集成与命令跳转 | 设置 → 终端视图 → Shell 集成 | 需手动安装集成 |
| 12 | 命令历史跨会话保留 | `shell-history-sync` | 开启 |
| 13 | 滚动位置保持与回滚行数 | `keep-scroll-position-on-output`、`terminal-transcript-rows` | 保持开启；5000 行 |
| 14 | 真斜体字体、OSC 8 超链接、OSC 52 剪贴板 | `~/.termux/font-italic.ttf`、`terminal-onclick-url-open`、`terminal-osc52-clipboard` | 无斜体字体时合成斜切；链接点击关闭；`write` |
| 15 | 终端内边距 | `terminal-padding-top`、`-bottom`、`-left`、`-right` | 0 |
| 16 | 状态栏 | `status-bar-*` | 关闭 |
| 17 | 终端内通知卡片与 OSC 通知 | `notification-*` | 关闭 |
| 18 | 界面形状令牌与系统界面适配 | 无 | 总是生效 |
| 19 | 设置页分组、搜索、长按恢复默认与扩展管理 | 无 | 总是可用 |
| 20 | 简体中文界面与按应用选择语言 | 无 | 跟随系统语言 |
| 21 | 关于页与问题报告指向本 fork | 无 | 总是生效 |
| 22 | 备份与恢复 | `termux-backup`、`termux-restore` | 需手动安装命令 |
| 23 | 已废弃的属性 | 无 | 写入不生效 |

---

## 1. 输入法兼容与硬件键盘直通

### 1.1 输入法兼容模式（`gboard-cjk-workaround`）

上游的终端视图以 `InputType.TYPE_NULL` 上报输入类型，部分输入法（例如 Gboard）因此强制使用英文
QWERTY 布局并隐藏语言切换键，无法输入中文、日文或韩文。开启该属性后，未接硬件键盘时终端对所有输入法
上报普通文本框类型（`TYPE_CLASS_TEXT`、关闭联想），语言切换键与候选词保持可用。

```properties
gboard-cjk-workaround=true
```

约束：输入法按文本框语义工作，部分输入法在英文下会保留组合文本，字符在提交后才送达终端。接有硬件键盘
且软键盘收起时终端仍上报 `TYPE_NULL`，物理按键直接送达终端。

### 1.2 硬件键盘直通的唯一规则

本 fork 不再提供输入类型选项，只遵循一条规则：终端上报给输入法的类型由「是否接有硬件键盘」与
「软键盘是否可见」决定。

| 硬件键盘 | 软键盘 | 上报类型 | 结果 |
| --- | --- | --- | --- |
| 有 | 收起 | `TYPE_NULL` | 输入法不介入，`Tab`、组合键、方向键与字符直接送达终端 |
| 有 | 弹出 | 文本框类型 | 输入法显示候选栏，软键盘与物理键盘均可输入 |
| 无 | 任意 | 由输入法兼容模式决定 | 与 §1.1 一致 |

输入连接在键盘接入或拔出、软键盘显示或隐藏时自动重启，不必重启应用。软键盘可见期间物理按键归输入法
所有，`Tab` 亦然；需要 `Tab` 直达终端时应先收起软键盘。Android 5.0 与 6.0 无法读取软键盘可见性，
在这两个版本上终端始终按文本框上报。

### 1.3 键盘诊断日志（`keyboard-compat-log`）

```properties
keyboard-compat-log=true
```

开启后按键分发、命令快捷键命中、终端视图收到的按键与输入法提交的文本分别以 `[activity]`、
`[terminal-view]`、`[ime]` 为前缀写入 logcat 与 `~/.termux/keyboard-compat.log`（超过 128 KB 时重建）。

---

## 2. 虚拟键盘的外观

「虚拟键盘」指应用自绘的额外按键行（`extra-keys`），系统输入法的外观不受本 fork 控制。

### 2.1 配色跟随配色方案

`~/.termux/colors.properties` 中的下列键决定按键行的配色；未定义时由配色方案的通用颜色派生。

| 按键行颜色 | `colors.properties` 的键 | 未定义时的取值 |
| --- | --- | --- |
| 文字 | `extra-keys-foreground` | `foreground` |
| 背景 | `extra-keys-background` | `background` |
| 激活文字 | `extra-keys-active-foreground` | `cursor` |
| 激活背景 | `extra-keys-active-background` | `background` 混合 25% `foreground` |

`~/.termux/termux.properties` 中的同名键优先级更高，且不会被 Termux:Styling 应用主题时覆盖：

```properties
extra-keys-foreground=#e0e0e0
extra-keys-background=#80000000
extra-keys-active-foreground=#ff8800
extra-keys-active-background=#33ffffff
```

`extra-keys-background` 同时作用于承载按键行的工具栏及其文本输入页，因此浅色配色下不会出现深色底
配白色文字的情况。设置页提供 22 个预设色、自定义颜色输入与「跟随配色方案」清除项。

### 2.2 激活高亮

组合键（`CTRL`、`ALT` 等）激活时同时使用激活文字色与激活背景色，手指松开或长按锁定期间保持高亮；
同一类键的多个实例一起变化。

### 2.3 按键块背景（`extra-keys-tile-background`）

为每个按键绘制 4dp 圆角方块，块间留 2dp 间隙，颜色默认由配色方案派生，可按需覆盖：

```properties
extra-keys-tile-background=true
extra-keys-tile-background-color=#ff2a2a3a
extra-keys-tile-active-background-color=#ff45475a
```

该属性默认开启后按键外观与上游不同；设为 `false` 即恢复上游的纯文字按键。设置页提供开关与两个调色板项。

### 2.4 上滑备用键提示（`extra-keys-popup-hints`）

定义了 `popup` 的按键在右上角以小字显示备用键的显示文本，样式与上滑后弹出的按钮一致，提示区域不拦截
触摸。

```properties
extra-keys-popup-hints=false
```

该属性为纯显示改进，默认开启；设为 `false` 即恢复上游外观。

---

## 3. 虚拟键盘的布局与行为

### 3.1 多面板（`extra-keys-panels`）

按键行可由多个面板组成，面板之间与文本输入页之间以左右滑动切换。不设置该属性时仍按 `extra-keys`
作为单面板处理。

```properties
extra-keys-panels=[ \
  [['ESC','TAB','CTRL','ALT','HOME','UP','END','PGUP'],['BKSP','LEFT','DOWN','RIGHT','PGDN','KEYBOARD']], \
  [['F1','F2','F3','F4','F5','F6'],['|','~','{','}','[',']']] \
]
```

工具栏高度取所有面板中的最大行数；配色、`extra-keys-style` 与 `extra-keys-text-all-caps` 对所有面板生效。
取值不是「矩阵的数组」时回退到 `extra-keys` 的单面板。`extra-keys` 与 `extra-keys-style` 的修改在
`termux-reload-settings` 后立即生效。

### 3.2 软键盘隐藏时收起按键行（`hide-extra-keys-when-soft-keyboard-hidden`）

```properties
hide-extra-keys-when-soft-keyboard-hidden=true
```

软键盘收起时隐藏按键行，软键盘再次弹出时恢复；用工具栏开关手动切换的可见性优先，直到软键盘状态再次
变化。该属性依赖软键盘可见性检测，在 Android 6.0 以下不生效。

### 3.3 新增特殊键

`extra-keys` 与 `extra-keys-panels` 中可使用下列由应用处理的键，默认布局不变：

| 键 | 默认显示 | 行为 |
| --- | --- | --- |
| `SEARCH` | `⌕` | 打开终端内搜索栏并聚焦 |
| `SPLIT_V` | `⬓` | 上下分屏；已分屏时仅切换方向 |
| `SPLIT_H` | `◫` | 左右分屏；已分屏时仅切换方向 |
| `SPLIT_CLOSE` | `✕` | 关闭分屏 |

显示文本可用 `display` 自定义，例如 `{key: 'SPLIT_V', display: 'V'}`。

---

## 4. 音量键自定义动作

| 条目 | 属性 | 默认 |
| --- | --- | --- |
| 音量加键单击 | `volume-up-action` | `default` |
| 音量加键长按 | `volume-up-long-action` | `none` |
| 音量减键单击 | `volume-down-action` | `default` |
| 音量减键长按 | `volume-down-long-action` | `none` |
| 音量加键序列 | `volume-up-send-keys` | `ESC` |
| 音量减键序列 | `volume-down-send-keys` | `ESC` |

| 取值 | 行为 |
| --- | --- |
| `default` | 上游行为：音量减按住期间等效 `Ctrl`，音量加按住期间等效 `Fn` |
| `none` | 不执行动作，按键仍被消费 |
| `volume` | 调整系统音量 |
| `keyboard` | 显示或隐藏软键盘 |
| `toggle-extra-keys` | 显示或隐藏按键行 |
| `page-up`、`page-down` | 向终端发送 `Page Up`、`Page Down` |
| `prev-session`、`next-session` | 切换会话 |
| `new-session` | 新建会话 |
| `paste` | 粘贴剪贴板 |
| `send-keys` | 发送 `volume-*-send-keys` 配置的按键序列 |

单击动作在抬键时执行；长按动作在按键开始重复时执行，此时不再执行单击动作。长按保持默认的 `none` 时，
长按不抵消单击动作。`default` 作为修饰键在按下瞬间生效，若同时配置了非 `none` 的长按动作，按住超过长按
判定时间会额外触发该动作。上述动作仅在「按键行为 → 音量键」为虚拟音量键（`volume-keys=virtual`，
默认值）时生效；外接全键盘时不接管音量键。

`send-keys` 使用与 `extra-keys` 相同的宏语法：空格分隔，键名取 `ESC`、`TAB`、`PGUP`、`UP`、`F1` 等，
也可写普通文本；`CTRL`、`ALT`、`SHIFT`、`FN` 只作用于紧随其后的一个键。设置页底部提供「恢复音量键默认
行为」，将上述六个属性从配置文件中注释掉。

---

## 5. 主题与外观

### 5.1 全应用跟随配色方案（`night-mode=color-scheme`）

```properties
night-mode=color-scheme
```

按配色方案 `background` 的亮度决定浅色或深色主题，抽屉、设置页、对话框随之切换；状态栏与导航栏的图标
在浅色背景下自动转为深色。`true`、`false`、`system` 三个上游取值行为不变。切换配色后应用会重建一次
Activity，会话与终端内容不受影响。

### 5.2 外观设置页

设置 → Termux → 外观。该页属性在创建 Activity 时读取，修改后应用会重建一次 Activity。

| 条目 | 属性 | 取值 |
| --- | --- | --- |
| 夜间模式 | `night-mode` | `true`、`false`、`system`、`color-scheme` |
| 全屏 | `fullscreen` | 布尔 |
| 全屏兼容处理 | `use-fullscreen-workaround` | 布尔，仅在启用全屏时生效 |
| 虚拟键盘风格 | `extra-keys-style` | `default`、`arrows-only`、`arrows-all`、`all`、`none` |
| 工具栏高度 | `terminal-toolbar-height` | 40–300 的百分数，写入文件时换算为 0.4–3.0 的倍率 |
| 虚拟键盘字母大写 | `extra-keys-text-all-caps` | 布尔，默认开启 |
| 会话抽屉 | `drawer-open-mode`、三个 `drawer-handle-*` | 见 §6.2 |

### 5.3 按键行为设置页

设置 → Termux → 终端键盘 → 按键行为。该页属性在使用时读取，修改后立即生效。

| 条目 | 属性 | 取值 |
| --- | --- | --- |
| 返回键 | `back-key` | `back`（默认）、`escape` |
| 音量键 | `volume-keys` | `virtual`（默认）、`volume` |
| 键盘开关按钮 | `soft-keyboard-toggle-behaviour` | `show/hide`（默认）、`enable/disable` |
| 启动时隐藏键盘 | `hide-soft-keyboard-on-startup` | 布尔，默认关闭 |
| 鼠标点按唤键盘 | `mouse-tap-shows-keyboard` | 布尔，默认关闭 |
| 停用物理键盘快捷键 | `disable-hardware-keyboard-shortcuts` | 布尔，默认关闭 |

`mouse-tap-shows-keyboard` 开启后，鼠标点击以及在启用鼠标跟踪的程序内的点击同样弹出软键盘；默认情况下
这些点击只转发给终端，以免打断程序内的选择与点击。软键盘收起后仍可用 `KEYBOARD` 键或 `Ctrl+Alt+K` 唤起。

---

## 6. 会话抽屉

### 6.1 外观与概要

抽屉配色取自当前配色方案（`background`、`foreground` 与调色板 `color4`），当前会话左侧显示强调色竖条；
头部显示会话数、活动窗格的前台进程与工作目录，其中主目录前缀缩写为 `~`。会话列表每项显示两行：会话名与
`PID`、前台进程名、工作目录；读取不到的信息不显示。抽屉打开、切换会话与更换配色时刷新。

### 6.2 开合方式（`drawer-open-mode`）

Android 10 起，从屏幕边缘内滑属于系统返回手势，抽屉难以通过边缘滑动打开，因此本 fork 在左边缘中部
提供把手。

```properties
drawer-open-mode=auto          # auto（把手与边缘滑动）/ handle（仅把手）/ edge（仅边缘滑动）/ none（均不使用）
drawer-handle-length=96        # 把手长度 dp，24–160
drawer-handle-opacity=45       # 把手不透明度 %，10–100
drawer-handle-position=50      # 把手中心在内容高度上的位置 %，0 为顶部
```

把手为紧贴左边缘的竖条，点击或向右滑动超过 16dp 即打开抽屉，抽屉拖动时淡出；其触摸区域已从系统返回
手势中排除。`handle` 与 `none` 模式下抽屉不响应边缘手势，但长按菜单的「会话列表」、按键行的 `DRAWER`
键与代码内的打开入口仍然可用。三个 `drawer-handle-*` 也可在设置 → 外观 → 会话抽屉内调整。

---

## 7. 会话切换与鼠标交互

### 7.1 左右滑动切换会话（`switch-session-on-swipe`）

```properties
switch-session-on-swipe=true
```

在终端区域横向滑动切换上一个或下一个会话，会话循环。选择文本、程序启用鼠标跟踪、以及从屏幕左边缘
起始的滑动不触发切换。该属性默认开启，设为 `false` 关闭。

### 7.2 手指拖拽上报为鼠标拖拽（`touch-drag-sends-mouse-drag`）

```properties
touch-drag-sends-mouse-drag=true
```

程序启用鼠标跟踪时，手指拖拽上报按下、移动与抬起三个事件，使 `tmux`、`vim` 等程序可以接收拖拽。启用
后拖拽不再产生滚轮事件，需要滚轮滚动时应关闭该属性。长按之后的手势与备用屏不受影响。

### 7.3 鼠标滚轮坐标

鼠标与触控板的滚轮事件始终上报当前指针位置，不需要先点击。手指滑动产生的滚轮事件坐标仍固定在滑动
起点，与上游一致。

### 7.4 鼠标悬停上报（`mouse-hover-sends-mouse-motion`）

```properties
mouse-hover-sends-mouse-motion=true
```

为启用 `DECSET 1003`（全事件跟踪）的程序上报鼠标移动，使悬停反馈可用。仅在格子变化时上报，两次上报
间隔不小于 10ms；仅对声明 `1003` 的程序生效，且只处理鼠标输入源。该属性默认关闭。无论该属性是否开启，
`1003` 均被正确解析，程序的点击、滚轮与拖拽因此正常上报。

### 7.5 鼠标右键（`mouse-right-click`）

```properties
mouse-right-click=auto     # auto（默认）/ forward / palette
```

| 取值 | 行为 |
| --- | --- |
| `auto` | 程序启用鼠标跟踪时转发右键；否则打开命令面板 |
| `forward` | 不打开命令面板；程序未跟踪鼠标时该次点击不产生任何序列 |
| `palette` | 始终打开命令面板 |

### 7.6 焦点上报（`DECSET 1004`）

程序请求 `DECSET 1004` 后，应用获得或失去焦点时向终端发送 `CSI I` 或 `CSI O`；切换会话与程序开启该模式
时同步当前焦点状态。该行为由程序请求触发，无对应属性。

---

## 8. 分屏

长按菜单选择「分屏（上下）」或「分屏（左右）」，已分屏时菜单提供「关闭分屏」。两个窗格是两个独立会话：

- 开启分屏时，第二个窗格显示当前未被第一个窗格显示的运行中会话；只有一个会话时新建一个会话。
- 点击窗格即切换活动窗格，软键盘、按键行、菜单、搜索、会话操作、音量键动作与粘贴均作用于活动窗格；
  活动窗格显示 1dp 边框，会话抽屉的选中项随之改变。
- 分隔条可拖动，比例限制在 20%–80%，两个会话各自收到 `SIGWINCH`。
- 在会话抽屉中选中已被另一窗格显示的会话时，两个窗格互换会话。
- 非活动窗格同样实时重绘，重绘节流至 50ms，并保持其滚动位置。
- 关闭分屏不结束第二个窗格中的进程，该会话仍在会话抽屉中。
- Activity 重建后恢复分屏开关、方向、比例、活动窗格与两个会话；进程被终止时会话随服务结束，应用回退
  到上游的「恢复上次会话到单窗格」行为。

---

## 9. 终端内搜索

长按终端后选择「搜索」，或按 `Ctrl+Alt+F`。搜索范围包含屏幕与回滚历史：

- 边输入边搜索，默认忽略大小写，全部匹配高亮，当前匹配以强调色标出，右侧显示「当前/总数」。
- `Aa` 按钮切换区分大小写，`.*` 按钮将查询按正则表达式解释；无效正则显示提示且不匹配任何内容。
- 时钟图标打开最近 10 条查询，查询记录保存在独立的 `termux-terminal-search` 偏好中。
- `↑`、`↓` 或回车在匹配之间循环跳转，视图滚动到对应行；按返回键关闭搜索栏并清除高亮。
- 切换会话后在新会话内重新执行搜索。

限制：最多返回 5000 个匹配；匹配按逻辑行进行，自动换行的行会被拼接；正则表达式的灾难性回溯可能延长
单次搜索时间。搜索栏是覆盖在终端上的浮层，不改变终端行列数。

---

## 10. 命令面板、终端菜单与选择工具栏

### 10.1 命令面板

命令面板是终端动作的唯一列表，跟随配色方案显示。打开方式：

| 入口 | 方式 |
| --- | --- |
| 快捷键 | `Ctrl+Shift+P` |
| 会话抽屉 | 抽屉头部的「命令面板」按钮 |
| 终端菜单 | 在 `terminal-menu-items` 中加入 `command-palette` |
| 虚拟键盘 | 在 `extra-keys` 中加入 `F12` |

面板按标题、分组或快捷键过滤，回车执行首个可用项；面板打开期间按下列出的快捷键可直接执行对应命令。

### 10.2 终端动作列表（`terminal-menu-items`）

```properties
terminal-menu-items=search,sessions,previous-command,next-command,split-vertical,split-horizontal,select-url,share-transcript,reset-terminal,kill-process,recent-notifications,settings,report
```

列表顺序即面板中的顺序，不在列表中的动作不显示。可用标识：`search`、`sessions`、`command-palette`、
`previous-command`、`next-command`、`split-vertical`、`split-horizontal`、`split-close`、
`select-url`、`share-transcript`、`share-selected-text`、`autofill-username`、`autofill-password`、
`reset-terminal`、`kill-process`、`styling`、`keep-screen-on`、`help`、`settings`、`report`、
`test-notification`、`recent-notifications`。不可用的动作自动隐藏，例如未分屏时的 `split-close`、
没有选中文本时的 `share-selected-text`、通知未开启时的 `test-notification`。面板在配置的动作之后追加
新建会话、新建命名会话与切换键盘，并保证「设置」「帮助」可达。设置页提供可调序的双区列表编辑器。

### 10.3 文本选择工具栏（`selection-toolbar-items`）

```properties
selection-toolbar-items=copy,paste,more
```

可用标识为 `copy`、`paste`、`more`。列表为空或全部为未知标识时回退到上述默认三项。粘贴目标是发起粘贴
的会话所在窗格。工具栏背景由系统 `ActionMode` 绘制，只能跟随应用主题。

### 10.4 命令快捷键（`command-shortcuts`）

```properties
command-shortcuts=command-palette=Ctrl+Shift+P,search=Ctrl+Alt+F,toggle-keyboard=Ctrl+Alt+K,new-session=Ctrl+Alt+C
```

格式为 `标识=快捷键` 的逗号列表，未列出的标识保留默认值，写成 `标识=` 取消该命令的快捷键。可用标识与
`terminal-menu-items` 一致，另加 `new-session`、`new-named-session`、`toggle-keyboard`。快捷键在活动层
拦截，终端视图、按键行与子视图获得焦点时均生效；extra keys 的宏按键同样被识别。`Ctrl+Alt+M` 固定用于
打开命令面板。

---

## 11. 命令标记与 shell 集成

安装随应用打包的 shell 集成后，shell 在提示符开始、命令开始与命令结束时发出 `OSC 133` 序列，应用据此提供：

- 长按菜单或 `command-shortcuts` 中的「上一条命令」「下一条命令」跳转；没有更下方命令时跳到当前提示符。
- 终端右侧的命令刻度：成功、失败与进行中分别使用不同颜色，点击刻度跳转到该命令。
- 失败命令的提示：通知卡片开启时显示卡片，否则回退为上游的退出码提示。
- 通知卡片内的命令行：集成脚本在 `OSC 133;D` 后附带命令行，供卡片显示。

安装与卸载入口为设置 → Termux → 终端视图 → Shell 集成。安装写入 `~/.termux/shell-integration.bash`
与 `.zsh`，并在 `~/.bashrc`（以及存在时的 `~/.zshrc`）末尾追加一段带标记的加载语句；不会修改 `PS1`，
也不覆盖已有的 `PROMPT_COMMAND`、`PS0` 或 `precmd`、`preexec` 钩子，重复安装不会重复追加。卸载删除该
标记块与脚本文件，配置文件其余内容保持不变。安装后需重开会话或重新加载 shell 配置。旧版本集成需在
设置页重新安装，设置页会标注版本过期。通过 `tmux` 运行时，`tmux` 可能丢弃这些序列。

命令标记随行存储，滚动、回看与列数变化后仍附着于对应文本；标记以提示符行为锚点，shell 未发送提示符
标记时以输出首行为锚点。

---

## 12. 命令历史跨会话保留（`shell-history-sync`）

```properties
shell-history-sync=true
```

上游在应用退出时直接终止全部会话进程，shell 没有机会写回历史文件，重新打开应用后无法用上方向键取出
历史命令。本 fork 为会话进程注入 `PROMPT_COMMAND="history -a; history -n"`，使每条命令即时追加到
`$HISTFILE`，并让同时打开的会话互相看到新命令。该注入仅在环境变量中没有 `PROMPT_COMMAND` 时进行，
非交互 shell 不受影响。zsh 由 shell 集成脚本设置 `inc_append_history` 与 `share_history`，未安装集成的
zsh 需自行配置。环境变量在创建会话时读取，修改属性后需新开会话。该属性默认开启，设为 `false` 恢复上游
行为。

---

## 13. 滚动位置与回滚行数

### 13.1 输出时保持滚动位置（`keep-scroll-position-on-output`）

```properties
keep-scroll-position-on-output=true
```

向上查看历史时，新输出不再把视图拉回底部；手动滑回底部即恢复跟随。输入或粘贴内容时自动回到底部。
该属性默认开启，设为 `false` 恢复上游行为。

### 13.2 回滚行数与冻结滚动

回滚行数默认为 5000 行（约 5 MB），可通过属性调整，上限 50000：

```properties
terminal-transcript-rows=10000
```

按键行中的 `SCROLL` 键切换「冻结滚动」：输出继续追加，视图完全不动。默认布局不含该键，需要时加入
`extra-keys`。`Shift+PageUp`、`Shift+PageDown` 按页滚动，需要硬件键盘。

---

## 14. 字体、超链接与剪贴板

### 14.1 真斜体（`~/.termux/font-italic.ttf`）

在 `~/.termux/font.ttf` 旁放置斜体字体后，斜体使用该字体的真实字形，并按正体字符宽度水平缩放以保持
网格对齐；字体度量一律取自正体。未提供该文件时行为与上游一致（合成斜切）。文件损坏时仅记录日志，
不影响正体与配色。

### 14.2 OSC 8 超链接

程序可用 `OSC 8` 为文本附加链接，链接文字自动加下划线。点击打开行为复用 `terminal-onclick-url-open`：

```properties
terminal-onclick-url-open=true
```

开启后被点击格子的链接优先，取不到时回退到在纯文本中识别 URL 的逻辑；关闭时链接只显示下划线。链接
随文本移动，擦除与重置序列会清除链接状态，换行不结束链接。长按菜单的「选择 URL」仍只识别纯文本中的 URL。

### 14.3 OSC 52 剪贴板（`terminal-osc52-clipboard`）

```properties
terminal-osc52-clipboard=write    # write（默认）/ read-write / off
```

| 取值 | 行为 |
| --- | --- |
| `write` | 程序可写入剪贴板，与上游一致 |
| `read-write` | 程序还可读取剪贴板，响应上限 100 KB |
| `off` | 忽略 OSC 52 |

默认不允许读取，因为读取没有提示，远端程序可能借此取得剪贴板内容。

---

## 15. 终端内边距（`terminal-padding-*`）

```properties
terminal-padding-top=0
terminal-padding-bottom=0
terminal-padding-left=8
terminal-padding-right=8
```

取值为 dp，范围 0–100，默认 0。内边距只缩小终端视口，工具栏与会话抽屉仍为通栏；与
`terminal-margin-horizontal`、`terminal-margin-vertical` 可以叠加。内边距区域显示终端背景色，行列数按
缩小后的尺寸重新计算并向程序发送 `SIGWINCH`，极端取值下至少保留 4 行 4 列。设置 → 终端视图 → 终端
内边距提供四个滑条。

---

## 16. 状态栏（`status-bar-*`）

常驻信息栏，显示设备状态，默认关闭。所有数据由应用侧系统接口采集，不依赖 `termux-api` 插件。

```properties
status-bar-enabled=true
status-bar-position=top            # top / bottom
status-bar-overlay=false           # true 时浮在终端上，不改变行列数
status-bar-height=24               # dp，14–120
status-bar-font-size=11            # sp，5–32
status-bar-padding-horizontal=8    # dp，0–64
status-bar-align=start             # start / center / end
status-bar-separator="  "
status-bar-show-icons=true
status-bar-icons-position=start    # start / end
status-bar-background-color=theme:background
status-bar-foreground-color=theme:foreground
status-bar-segments=clock,battery,memory,cpu,net,ip,proxy
```

段可用 `status-bar-segment-<id>-<键>` 单独配置：`enabled`、`format`、`label`、`color`、`style`
（`text`、`chart`、`text-chart`）、`action`、`interval`、`warn-above`、`warn-below`、`warn-color`。

| 段 | 占位符 |
| --- | --- |
| `clock` | `SimpleDateFormat` 模式，默认 `HH:mm` |
| `battery` | `%p` 百分比、`%t` 温度、`%s` 充电状态 |
| `memory` | `%p` 百分比、`%u` 已用、`%a` 可用、`%t` 总量 |
| `cpu` | `%p` 使用率、`%f` 当前频率、`%m` 最高频率、`%c` 核心数 |
| `net` | `%rx`、`%tx` 速率，`%rxTotal`、`%txTotal` 累计流量 |
| `ip` | `%4`、`%6`、`%i` 首选地址、`%if` 网卡名 |
| `proxy` | `%p` 主机与端口、`%h` 主机、`%o` 端口、`%t` 类型 |

点击动作取 `none`、`wifi-settings`、`battery-settings`、`data-settings`、`app-settings`、`new-session`。
颜色取 `theme:` 名称或固定色值。

占位模式（`status-bar-overlay=false`）把状态栏高度计入终端区，终端行列数随之减少；覆盖模式不改变行列数，
但会遮挡边缘若干行。状态栏位于抽屉内容区内，抽屉始终绘制在其上方。内存与网络数据读不到时显示 `-`；
网络流量为整机统计；Android 9 起系统限制读取 `/proc/stat`，CPU 使用率回退为频率占用率。

---

## 17. 终端内通知卡片与 OSC 通知

### 17.1 总开关与外观

```properties
notification-enabled=false
notification-position=top-end       # top-end / top-start / bottom-end / bottom-start
notification-max-visible=3          # 1–8
notification-duration=2000          # 毫秒，0 为常驻
notification-width=0                # dp，0 为自适应
notification-font-size=13           # sp
notification-corner-radius=0        # dp
notification-elevation=0            # dp
notification-border-width=1         # dp
notification-accent-width=4         # dp
notification-monospace=true
notification-body-max-lines=2
notification-background-color=
notification-foreground-color=
notification-accent-failure-color=
notification-accent-success-color=
notification-accent-warning-color=
notification-accent-info-color=
```

颜色为空时由配色方案派生。默认外观为直角、无阴影、1dp 描边、左侧强调色条与等宽字体；设置圆角与阴影
即可改为卡片外观。卡片浮于终端之上，不改变行列数。

### 17.2 通道

| 通道 | 属性 | 默认 | 内容 |
| --- | --- | --- | --- |
| 失败命令 | `notification-exit-code-enabled`、`-show-cwd`、`-show-session`、`-min-duration`、`-ignore-commands` | 开启 | 退出码、命令行、工作目录、会话名与耗时 |
| 应用事件 | `notification-events-enabled`、`notification-events` | 关闭 | `session-exit`、`plugin-result`、`title`、`bell` |
| 程序通知 | `notification-osc-enabled`、`notification-osc-allow-app`、`-report-click`、`-icons`、`-rate-limit` | 关闭 | `OSC 9`、`OSC 99`、`OSC 777` |
| 后台通知 | `notification-background-enabled` | 关闭 | 应用不在前台时改发系统通知 |
| 历史 | `notification-history-size` | 50（仅内存） | 长按菜单的「最近通知」 |

失败命令卡片按命令名匹配 `notification-exit-code-ignore-commands`（默认
`grep,test,diff,true,false,rg,fd,[`），并以 `notification-exit-code-min-duration` 过滤短命令。点击失败
命令卡片跳转到该命令；点击其它条目复制其文字。

程序通知支持 `OSC 99` 的 `i`、`d`、`e`、`p`、`u`、`w`、`c`、`a`、`o`、`f`、`t` 元数据与
`title`、`body`、`buttons`、`icon`、`close`、`?`、`alive` 动作；`s`、`n`、`g` 元数据被忽略。限流为
每会话每秒 3 条。单块负载超过 128 KB 时整条丢弃；图标在 `notification-osc-icons=false` 时不解析。

### 17.3 测试入口

通知开启后，长按菜单出现「发送测试通知」，每次点击轮换显示失败、成功、警告与信息四种卡片。

---

## 18. 界面形状与系统界面适配

界面使用统一的形状令牌：对话框与浮动窗口 16dp 圆角，对话框内行、通知卡片与抽屉会话行 12dp 圆角，
快捷键标签 8dp 圆角。通知卡片与终端菜单的原圆角属性仍可覆盖默认值。

系统界面适配：

- 手势导航的横条区域由系统栏内边距保留，终端最后一行与按键行不被覆盖，该区域显示终端背景色。
- 全屏（`fullscreen=true`）时终端绘制到刘海与挖孔区域；非全屏时内容避让挖孔，状态栏图标不被遮挡。
- `fullscreen` 在启动时读取，修改后需重启应用。

---

## 19. 设置页

设置 → Termux 按「终端」「界面」「行为」「高级」四组排列，每个入口配图标，摘要压为一行。设置页跟随
系统明暗，强调色取自当前配色方案。

- **搜索**：根页顶部的「搜索设置」按标题、摘要与所属页面过滤，点选结果跳转到对应页面并将目标项滚动到
  可见位置并高亮。
- **长按恢复默认**：长按任意设置项并确认后，将 `termux.properties` 中对应的键注释掉；使用
  `SharedPreferences` 的项则删除对应键。属性不存在时应用回退到默认值。
- **扩展管理**：设置 → 扩展列出本构建包含的扩展，可逐项启用或禁用。禁用被其他扩展依赖的扩展时，应用
  先说明连带范围并要求确认。禁用后该扩展的设置项与搜索条目隐藏，功能页只显示停用说明，已安装到
  `$PREFIX/bin` 的脚本保留。当前登记的扩展为「备份与恢复」，默认启用。

---

## 20. 界面语言

内置简体中文翻译，覆盖 `app`、`termux-shared` 与 `terminal-view` 中面向用户的文案。Android 13 及以上可在
系统设置中为 Termux 单独选择语言，更低版本跟随系统语言。新增语言时复制
`app/src/main/res/values/strings.xml` 为对应语言目录后翻译，占位符与转义序列须原样保留。

---

## 21. 关于页与问题报告

「关于」页与问题报告的报告末尾使用本 fork 的仓库、发行与问题追踪地址，并列出最新构建与全部发行版本的
链接，避免用户向上游报告本 fork 特有的问题。

---

## 22. 备份与恢复

应用内置 `termux-backup` 与 `termux-restore` 脚本，入口为设置 → Termux → 备份与恢复：

| 条目 | 作用 |
| --- | --- |
| 立即备份 | 在新会话中运行备份脚本，可中途取消 |
| 从备份恢复 | 列出设备上的备份并选择，确认后执行，默认先备份当前环境 |
| 把脚本安装为命令 | 复制到 `$PREFIX/bin`，之后可直接调用命令 |

```sh
termux-backup                                     # 备份到共享存储的下载目录
termux-backup -o /sdcard/Download -n my-bk -z gzip
termux-restore                                    # 交互列出备份并选择
termux-restore <归档> --dry-run                    # 仅校验并报告
termux-restore <归档> --target-dir ~/restore-test  # 解包到指定目录演练
```

归档包含完整的 `$PREFIX` 与 `$HOME`，排除临时目录、`home/storage` 与 `home/.cache`，并附带一份记录包
列表与配置文件的元数据目录。归档生成后执行完整性校验并写入校验值，恢复前据此验证。恢复为覆盖式，归档中
不存在的文件保留；共享存储不可用时会回退到 `$HOME`。恢复后建议执行 `termux-reload-settings`、
`pkg upgrade` 与 `termux-setup-storage`。

---

## 23. 已废弃的属性

下列属性已随命令面板接管终端菜单而废弃，设置页对应项已移除，写入不再生效：
`terminal-menu-font-size`、`terminal-menu-corner-radius`、`terminal-menu-border-width`、
`terminal-menu-monospace`、`terminal-menu-show-icons` 与三个 `terminal-menu-*-color`。
