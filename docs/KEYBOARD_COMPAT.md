# KEYBOARD_COMPAT — 硬件键盘与输入法兼容层设计

> 评估对象：`app/`（`com.termux`）与只读模块 `terminal-view/` 的输入路径。
> 本文是设计方案，不是已经交付的行为；已实现的诊断开关见 §6。

---

## 1. 现状

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

### 1.2 相关属性与现有行为

| 属性 | 作用 |
| --- | --- |
| `enforce-char-based-input` | `InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`，绕开部分输入法在 `TYPE_NULL` 下的异常 |
| `gboard-cjk-workaround` | IME 语言为 CJK 时用 `TYPE_CLASS_TEXT + NORMAL` 并重启输入；否则 `VISIBLE_PASSWORD` |
| `disable-hardware-keyboard-shortcuts` | 关闭 `Ctrl+Alt+*` 组合 |
| `back-key-the-escape-key` | 返回键映射为 Esc |
| `ctrl-space-workaround` | 部分 ROM 的 `Ctrl+Space` 修正 |
| `mouse-tap-shows-keyboard` | 鼠标点击是否也弹软键盘 |

`TerminalView.onCreateInputConnection()`（只读模块）当前只会产出三种输入类型：

- `TYPE_NULL`（默认，最接近终端语义）；
- `VISIBLE_PASSWORD | NO_SUGGESTIONS`（`enforce-char-based-input`）；
- `TYPE_CLASS_TEXT | NO_SUGGESTIONS | VISIBLE_PASSWORD 或 NORMAL`（CJK workaround 且 IME 有 subtype 时）。

### 1.3 已知问题

1. **命令快捷键不响应**。`Ctrl+Shift+P` 等组合在部分硬件键盘与输入法组合下不生效。已确认的脆弱点：
   字符比较依赖 `KeyEvent.getUnicodeChar()`，某些键盘在按住修饰键时返回 0；事件也可能从 (B)/(C) 路径到达，
   不经过 `Activity.dispatchKeyEvent`。
2. **Gboard 悬浮栏不出现**。连接硬件键盘时，Gboard 只在「看起来是普通文本输入框」的组件上显示悬浮栏。
   终端当前在英文 IME 下上报 `VISIBLE_PASSWORD`，Gboard 视为密码框而不显示；只有切到 CJK 语言
   （workaround 用 `NORMAL`）或聚焦真正的 `EditText`（工具栏文本框）时才出现。
3. **没有诊断手段**。用户报告「按键没反应」时无法判断事件走的是哪条路径、输入法上报了什么。

---

## 2. 设计目标

1. 硬件键盘快捷键在**任意焦点状态**下生效，且与面板标签同源。
2. 让 Android 与输入法把终端识别为**普通可编辑文本框**，从而在连接硬件键盘时仍能显示 Gboard 悬浮栏，
   同时不破坏终端的字符输入、CJK 组合与 `Ctrl` 组合键。
3. 三个层次都可单独关闭，默认行为与上游一致。
4. 出问题时能用一份日志定位到具体层次。

---

## 3. 分层设计

### 3.1 A 层：事件采集（已成体系，需补齐）

| 入口 | 覆盖 | 现状 |
| --- | --- | --- |
| `TermuxActivity.dispatchKeyEvent()` | 窗口级，与焦点无关 | 已接入 `handleCommandShortcut()` |
| `TerminalViewClient.onKeyDown()` | (A)(B)(D) 三条路径 | 已接入同一个方法 |
| `TerminalView.onKeyPreIme()` | 软键盘前的返回键/`Ctrl+Space` | 上游已有，未接入命令快捷键 |
| `InputConnection` 文本路径 (C) | 输入法提交的字符 | 无按键语义，命令快捷键无法从这一层恢复 |

结论：**A 层不需要新接口**，只需要把 `onKeyPreIme` 路径也接到同一个分发器（遇到命令快捷键时在 pre-IME 阶段吞掉，
避免返回键映射与快捷键互相干扰）。

### 3.2 B 层：统一路由（建议新增 `commands/KeyEventRouter`）

```
KeyEvent
  → CommandShortcuts.matches(id)            命令快捷键（可配置，见 §37.1）
  → TerminalMenuItems 可用性过滤            禁用扩展的动作直接忽略
  → TerminalActionRunner / ShellCommandHost 唯一执行入口
  → 终端虚拟键（handleVirtualKeys）
  → 程序
```

- 三个采集点都只调用 `KeyEventRouter.dispatch(activity, event)`，不再各自判断快捷键。
- 匹配时先比字符、再比物理键码（已实现于 `ShellCommandShortcut`），避免键盘在修饰键下不报字符导致失配。
- 路由结果写日志（`keyboard-compat-log=true`），日志字段：`action/code/unicode/ctrl/alt/shift/source/device`。

### 3.3 C 层：IME 兼容（需要只读模块一处钩子）

只读模块 `terminal-view` 追加两个**默认不改变行为**的客户端回调：

```java
// TerminalViewClient
default int getInputType(int defaultInputType) { return defaultInputType; }
default int getImeOptions(int defaultImeOptions) { return defaultImeOptions; }

// TerminalView.onCreateInputConnection(EditorInfo outAttrs)
// ... 现有逻辑算完之后 ...
outAttrs.inputType = mClient.getInputType(outAttrs.inputType);
outAttrs.imeOptions = mClient.getImeOptions(outAttrs.imeOptions);
```

app 侧新增属性（`ForkPropertyKeys`）：

```properties
ime-input-type = auto     # auto | text | password | none
```

| 取值 | 输入类型 | 适用 |
| --- | --- | --- |
| `auto`（默认） | 现有逻辑（`TYPE_NULL` 或 `enforce-char-based-input`/CJK workaround 的结果） | 与上游一致 |
| `text` | `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS | TYPE_TEXT_VARIATION_NORMAL` | 让 Gboard 视为普通文本框，显示悬浮栏；仍不发组合文本给终端 |
| `password` | `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` | 旧输入法兼容 |
| `none` | `TYPE_NULL` | 最纯的终端语义 |

实现要点：

1. `TermuxTerminalViewClient` 缓存解析结果，`onReloadProperties()` 后调用
   `TerminalView.restartInputConnection()`（只读模块已有公开方法），让输入法立即重建。
2. `text` 模式保留 `IME_FLAG_NO_FULLSCREEN`，避免横屏全屏提取框。
3. `text` 模式不设置 `IME_FLAG_NO_PERSONALIZED_LEARNING` 等隐私标志，避免输入法把文本框当作搜索框。
4. 与 `gboard-cjk-workaround` 的关系：`ime-input-type != auto` 时以显式值为准，CJK workaround 只负责语言与重启。

**为什么必须改只读模块**：`onCreateInputConnection` 是 `TerminalView` 自己实现的，app 无法在外部替换编辑器信息；
包装层（`TermuxTerminalFrame`）拿不到 IME 连接，`TermuxActivity` 也没有可用的注入点。
只读模块的改动是 6 行（接口两个默认方法 + 视图两行赋值），不改变默认行为。

### 3.4 D 层：软键盘与硬件键盘共存

| 行为 | 方案 |
| --- | --- |
| 连接硬件键盘时点终端仍弹软键盘 | 保留现有 `showSoftInput` 行为；`toggle-keyboard` 命令继续可用 |
| Gboard 悬浮栏 | `ime-input-type=text` + 点击终端请求 IME；悬浮栏本身由 Gboard 决定，应用不伪造输入法窗口 |
| `Ctrl`/`Alt` 修饰状态 | 继续由 `ExtraKeysView` 的虚拟键与硬件 `KeyEvent.metaState` 各自提供，不做合并 |
| 快捷键与终端程序冲突 | `disable-hardware-keyboard-shortcuts` 保持全局开关；命令快捷键单独由 `command-shortcuts` 控制 |

---

## 4. 配置与默认值

```properties
# 输入法兼容（新增，默认与上游一致）
ime-input-type = auto

# 命令快捷键（已实现）
command-shortcuts = command-palette=Ctrl+Shift+P,search=Ctrl+Alt+F,toggle-keyboard=Ctrl+Alt+K,new-session=Ctrl+Alt+C

# 键盘诊断日志（新增）
keyboard-compat-log = false
```

设置页落点：

| 设置项 | 页面 |
| --- | --- |
| `ime-input-type` | 终端键盘页 →「输入法兼容」 |
| `keyboard-compat-log` | 终端键盘页 →「键盘诊断日志」 |
| `command-shortcuts` | 终端键盘页（已实现） |

---

## 5. 验收条件

1. 硬件键盘 + Gboard（英文）：`ime-input-type=text` 时，点击终端出现 Gboard 悬浮栏；输入字符进入终端，
   不出现组合文本残留。
2. `auto` 模式下行为与当前版本完全一致（回归项）。
3. `Ctrl+Shift+P`、`Ctrl+Alt+F/K/C` 在以下状态都能触发：终端聚焦、软键盘弹出、分屏非活动窗格、
   多选浮条打开。
4. `command-shortcuts` 改绑后，面板标签与新键一致，旧键不再拦截。
5. `keyboard-compat-log=true` 时，日志能区分三条到达路径（Activity / 视图 / pre-IME）。

---

## 6. 交付状态

已实现并提交：

| 项 | 位置 | 提交 |
| --- | --- | --- |
| `TerminalViewClient.getInputType/getImeOptions` 钩子（只读模块，需求方已书面豁免） | `terminal-view/` | `8a1fc86` |
| `ime-input-type` 属性（`auto` 默认 / `text` / `password` / `none`）与 `ImeInputType` | `app/terminal/` | `a5cba39` |
| 设置页「硬件键盘与输入法」分类（`ime-input-type`、`keyboard-compat-log`） | `res/xml/termux_keyboard_preferences.xml` | `a5cba39` |
| 快捷键匹配回退物理键码 + `keyboard-compat-log` 日志 | `commands/`、`terminal/` | `a1b589e` |
| 诊断日志写入 `~/.termux/keyboard-compat.log`（128KB 重建） | `terminal/KeyboardCompatLog.java` | `0a16a59` |
| 输入法事件日志钩子（`onImeEvent`，只读模块） | `terminal-view/` | 本次 |
| Tab / Shift+Tab 直通（避免被窗口焦点切换吞掉） | `TermuxActivity.handleTerminalTab` | 本次 |
| `command-shortcuts` 属性与设置项、右键行为、面板标签同源 | 见 `FORK.md` §37 | `7c0ed84` |

仍待真机验收（`text` 模式与日志）：

1. 英文 Gboard + 硬件键盘：`ime-input-type=text` 是否出现悬浮栏，输入是否无组合文本残留。
2. `auto` 模式下行为与上游完全一致。
3. 其他输入法（Samsung、Hacker's Keyboard）在 `text` 模式下的表现。
4. `keyboard-compat-log=true` 时日志能否覆盖按键、视图与输入法三条路径。
5. TUI（Neovim / pi）中的 Tab 与 IME 文本：Tab 应出现在 `[activity] sent Tab to the terminal`；
   IME 文本应出现 `[ime] commitText(...)`。若只有 `setComposingText(...)` 而没有 `commitText`，
   说明输入法把文本留在组合状态，TUI 只能等候选词提交后才收到字符。

---

## 7. 风险

1. `TYPE_CLASS_TEXT` 会让部分输入法启用自动纠错/联想，虽然 `NO_SUGGESTIONS` 能压制大部分行为，
   仍需在 Gboard、Samsung、Hacker's Keyboard 上确认不出现组合文本进入终端的问题。
2. `restartInput` 会短暂关闭输入法窗口，切换属性时可见闪烁；仅在属性变化时调用。
3. 硬件键盘的 `Ctrl+Alt+*` 与命令快捷键可能重叠，路由顺序固定为「命令快捷键优先」，
   用户若把某个动作绑到 `Ctrl+Alt+F`，搜索命令将不再执行——这是预期行为，需在设置摘要中说明。
