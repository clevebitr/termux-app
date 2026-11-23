# KEYBOARD_COMPAT — 硬件键盘与输入法兼容层

> 评估对象：`app/`（`com.termux`）与只读模块 `terminal-view/` 的输入路径。
> 本文记录当前的**唯一规则**、实现位置、诊断手段与边界；不再有可调选项。

---

## 1. 结论与证据

### 1.1 按键到达终端的三条路径

```
物理键盘（USB / 蓝牙）
   │
   ├─(A) Window → Activity.dispatchKeyEvent → 视图树 → TerminalView.onKeyDown
   │        → TermuxTerminalViewClient.onKeyDown → 程序
   │
   ├─(B) InputMethodManager.sendKeyEvent → View.dispatchKeyEvent（从视图开始，绕过 Activity）
   │        → TerminalView.onKeyDown → 视图客户端
   │
   └─(C) 输入法 commitText / 组合文本 → TerminalView 的 InputConnection → 程序
```

额外键（`extra-keys`）走 (D)：`TerminalExtraKeys` 直接调用 `TerminalView.onKeyDown(keyCode, event)`，同样绕过 Activity。

### 1.2 根因

输入法一旦把终端当作**文本类编辑器**（`TYPE_CLASS_TEXT` 及变体），就会在 (C) 路径上抢先接管物理键盘：
按键的**按下事件**被输入法取走用于组合，应用只收到抬起事件，文本稍后以 `commitText` 回灌。`Tab` 会被一并取走，
应用根本看不到该事件。

真机日志（`~/.termux/keyboard-compat.log`）给出的直接证据：

| 观测 | 含义 |
| --- | --- |
| `[activity]` 中字母只有 `action=up`，没有 `action=down` | 按下事件在到达 Activity 之前已被输入法取走 |
| `[ime] commitText("e", 1)` 逐字符出现 | 文本经输入连接回灌，与按键分属两条通道 |
| `Tab` 在日志中完全没有记录 | 输入法连 `Tab` 一起取走，应用层无法拦截 |

因此**任何在应用层拦截 `Tab` 的做法都不可能生效**：事件在 Activity 之前就没了。有效的杠杆只有上报给输入法的
输入类型。

### 1.3 既有属性

| 属性 | 作用 |
| --- | --- |
| `enforce-char-based-input` | `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`，绕开部分输入法在 `TYPE_NULL` 下的异常 |
| `gboard-cjk-workaround` | IME 语言为 CJK 时用 `TYPE_CLASS_TEXT + NORMAL` 并重启输入；否则 `VISIBLE_PASSWORD` |
| `disable-hardware-keyboard-shortcuts` | 关闭 `Ctrl+Alt+*` 组合 |
| `back-key-the-escape-key` | 返回键映射为 Esc |
| `ctrl-space-workaround` | 部分 ROM 的 `Ctrl+Space` 修正 |
| `mouse-tap-shows-keyboard` | 鼠标点击是否也弹软键盘 |

---

## 2. 唯一规则

终端上报给输入法的类型只由「软键盘是否可见」决定，不设任何用户选项：

| 硬件键盘 | 软键盘 | 上报类型 | 结果 |
| --- | --- | --- | --- |
| 有 | 收起 | `TYPE_NULL` | 输入法不介入，`Tab`、`Ctrl`/`Alt` 组合键、方向键与字符全部直达终端 |
| 有 | 弹出 | `TYPE_CLASS_TEXT \| NO_SUGGESTIONS \| NORMAL` | 输入法显示悬浮栏与候选词，软键盘与物理键盘都能打拼音 |
| 无 | 任意 | 视图算出的类型（含 `gboard-cjk-workaround`） | 与上游一致 |

规则的含义：**看得见软键盘就是输入法在打字，看不见就是终端在收键**。

---

## 3. 实现

### 3.1 输入类型解析

`TermuxTerminalViewClient.resolveInputType(defaultInputType, hardwareKeyboardAttached, softKeyboardVisible)`
是唯一的判定点，`getInputType()` 只负责取状态并记录日志：

```java
if (!hardwareKeyboardAttached) return defaultInputType;
return softKeyboardVisible
    ? TYPE_CLASS_TEXT | NO_SUGGESTIONS | NORMAL
    : TYPE_NULL;
```

`TerminalView` 在算完自己的 `inputType` 之后调用客户端的 `getInputType()`，因此这里的返回值覆盖
`enforce-char-based-input` 与 `gboard-cjk-workaround` 的结果。这两个属性仍然保留，只在「没有硬件键盘」或
「软键盘可见」的分支里间接生效。

### 3.2 状态的来源

| 状态 | 来源 |
| --- | --- |
| 是否接有硬件键盘 | `KeyboardUtils.isHardKeyboardConnected()`（`Configuration.keyboard` 与 `hardKeyboardHidden`） |
| 软键盘是否可见 | `KeyboardUtils.isSoftKeyboardVisible()`（窗口 inset 的 IME 可见性），在既有的全局布局回调中读取 |

Android 5.0 与 6.0 读不到软键盘可见性，这两个版本上 `TermuxTerminalViewClient.getInputType()` 按「可见」处理，
即始终上报文本类类型：保持输入法可用，放弃直通。

### 3.3 重启时机

输入法只在**创建输入连接**时读取编辑器信息，所以下列变化必须重启连接：

| 变化 | 处理 |
| --- | --- |
| 硬件键盘接入或拔出 | `TermuxActivity` 的 `InputManager.InputDeviceListener` 与 `onConfigurationChanged` |
| 软键盘显示或隐藏 | `updateTerminalToolbarVisibilityForSoftKeyboardVisibility()` 中的可见性比较 |
| 属性重载 | `TermuxTerminalViewClient.onReloadProperties()` |

重启通过 `TerminalView.restartInputConnection()` 完成，两个窗格都会重启。只在状态真正变化时重启，避免每次布局
都触发。

软键盘**弹出**时还会在 200ms 后再重启一次（`IME_RESTART_AFTER_SOFT_KEYBOARD_SHOWN_DELAY_MS`）：输入法在自己
的窗口播放弹出动画期间可能忽略 `restartInput()`，随后继续使用弹出前读取的类型，终端因此停留在 `TYPE_NULL`，
表现为「软键盘出来了但没有悬浮栏、没有候选词」。收起时只重启一次。

---

## 4. 中文输入流程

1. 需要输入中文时弹出软键盘：`Ctrl+Alt+K`、额外键栏的键盘键或命令面板的「切换键盘」。

   在 TUI（如 pi、nvim、tmux）里**点击终端不会弹出软键盘**：程序开启鼠标跟踪后，点击被当作鼠标事件转发给程序，
   这是上游行为。需要点击也能弹出时把 `mouse-tap-shows-keyboard` 设为 `true`，代价是点击不再作为鼠标事件送达
   程序。
2. 软键盘弹出期间终端按文本框上报，输入法显示悬浮栏与候选词，软键盘与物理键盘都可用来打拼音，提交文本经
   输入连接写入终端；
3. 收起软键盘后自动回到 `TYPE_NULL`，物理按键重新直达终端。

---

## 5. 已删除的内容

| 删除项 | 原因 |
| --- | --- |
| `ime-input-type` 属性、`ImeInputType`、设置页列表项与 5 个取值 | 行为已由唯一规则决定；多套取值互相干扰，且用户无法判断当前生效的是哪一个 |
| `TermuxActivity.handleTerminalTab()` 等活动层 `Tab` 拦截 | 输入法接管物理键盘时根本收不到事件；不接管时终端视图自己就能处理 `Tab` |
| `HardwareKeyboard` 辅助类 | 与只读模块已有的 `KeyboardUtils.isHardKeyboardConnected()` 重复 |

保留 `keyboard-compat-log`（默认关闭）：它只影响日志，不改变行为，是无 `adb` 环境下唯一的现场证据。

---

## 6. 诊断日志

```properties
keyboard-compat-log = true   # 默认 false
```

日志同时写入 logcat 与 `~/.termux/keyboard-compat.log`（超过 128KB 自动重建）：

| 日志前缀 | 来源 |
| --- | --- |
| `[activity]` | 窗口级按键分发、命令快捷键命中 |
| `[terminal-view]` | 终端视图收到的按键 |
| `[ime]` | 上报的输入类型与输入法提交的文本 |

排障判据：

- 直通生效时，每个按键都应有一条 `[terminal-view]`，且 `[ime] getInputType` 的 `reported=0x0`；
- 若只有 `[activity]` 的 `action=up`、没有按下事件、也没有 `[terminal-view]`，说明输入法仍在接管物理键盘，
  此时 `reported` 必然不是 `0x0`，需要检查软键盘是否被判定为可见；
- `Tab` 若在日志中完全没有记录，说明事件被输入法取走，问题在输入类型而不在应用层处理。

---

## 7. 验收条件

1. 接有硬件键盘、软键盘收起：终端里运行 `od -c`，按 `Tab`、回车、`Ctrl-D`，输出应含 `\t`。
   `lazyvim` 插入模式按 `Tab` 是否能缩进取决于 `:verbose imap <Tab>` 的结果，见 §7.1。
2. 同状态下 `Ctrl`/`Alt` 组合键、方向键、`Enter` 顺序正确，node 写的 TUI 输入不再错位。
3. 弹出软键盘后中文（拼音）可输入，候选与悬浮栏正常；收起后恢复直通。
4. 键盘接入或拔出后无需重启应用即生效。
5. `keyboard-compat-log=true` 时日志符合 §6 的判据。

### 7.1 常见误判

| 现象 | 实际原因 |
| --- | --- |
| `cat -v` 里按 `Tab` 只看到一片空白，没有 `^I` | 看到的是行规程的**回显**，终端把 `0x09` 渲染成到下一个制表位的空白；`cat` 的输出是行缓冲，要按回车后才会出现 `^I`。改用 `od -c` 或 `stty -echo` 后再测 |
| 日志里有 `[terminal-view] action=down, code=KEYCODE_TAB`，但 nvim 插入模式不缩进 | `Tab` 已到达程序。LazyVim 默认给 blink.cmp 使用 `super-tab` 预设，`<Tab>` 用于展开或选择补全，**不会插入缩进**。用 `nvim --clean` 对照，并在 LazyVim 里执行 `:verbose imap <Tab>` 确认 |
| 软键盘可见时 `Tab` 没有反应 | 按 §2 的规则此时由输入法接管，`Tab` 归输入法；收起软键盘即恢复 |
| TUI 里点击终端不弹软键盘、因此看不到输入法悬浮栏 | 程序开启鼠标跟踪时点击被转发给程序，见 §4；用 `Ctrl+Alt+K` 或额外键拉起，或设置 `mouse-tap-shows-keyboard=true` |
| 软键盘弹出了但没有悬浮栏与候选词 | 输入法在弹出动画期间忽略了重启，见 §3.3；本版本已加 200ms 后的二次重启 |

---

## 8. 风险与边界

1. `TYPE_NULL` 下输入法不接管物理键盘，物理键盘上的拼音候选不可用；中文依赖软键盘可见这一条件。
2. 重启输入连接可能让输入法短暂重建窗口；只在状态真正变化时重启。若某个输入法在重启时收起软键盘，会退化为
   直通状态，不会形成循环。
3. 个别 ROM 在蓝牙键盘刚连接时 `Configuration` 与输入设备回调都尚未更新，输入类型要等下一次配置变更或设备
   回调才会切换。
4. 软键盘可见性依赖窗口 inset，Android 5.0 与 6.0 不可用，见 §3.2。
