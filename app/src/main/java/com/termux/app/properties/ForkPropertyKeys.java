package com.termux.app.properties;

/**
 * 目的 — 集中声明 fork 自有、且不共享库 {@code TERMUX_APP_PROPERTIES_LIST} 内的属性键。
 *
 * <p>行为 — 只提供常量。这些键由 {@link ForkProperties} 从磁盘读取，因为共享库的属性加载会丢弃不在其列表内的键：
 * 通过共享库写入的属性在下次加载时会被过滤掉，因此只能存在于 `termux.properties` 文本中。</p>
 *
 * <p>约束 — 已由只读模块 {@code TermuxPropertyConstants} 声明的 fork 键（`drawer-*`、`status-bar-*`、
 * `notification-*`、`terminal-menu-*` 等）一律继续使用只读模块的常量，不要在这里重复定义；新增键必须先确认
 * 只读模块没有同名常量，否则两处会指向同一属性却各自解析。</p>
 */
public final class ForkPropertyKeys {

    private ForkPropertyKeys() {
    }

    /**
     * The shortcuts of the commands of the command palette, as `id=shortcut` entries separated by commas,
     * like `search=Ctrl+Alt+F,palette=Ctrl+Shift+P`. An entry with an empty shortcut, like `search=`,
     * removes the shortcut of that command. See {@code CommandShortcuts}.
     */
    public static final String COMMAND_SHORTCUTS = "command-shortcuts";

    /**
     * Whether every hardware key event that the activity, the terminal view and the input connection see is
     * logged at info level, so that a keyboard that does not deliver the expected events can be diagnosed from
     * the log. The keys are only logged while this is `true`.
     */
    public static final String KEYBOARD_COMPAT_LOG = "keyboard-compat-log";

    /**
     * What the secondary mouse button does: `auto` forwards it to the program when it tracks the mouse and
     * opens the command palette otherwise, `forward` never opens the palette and only forwards the button,
     * and `palette` always opens the palette. See {@code TermuxTerminalFrame.SecondaryButtonBehaviour}.
     */
    public static final String MOUSE_RIGHT_CLICK = "mouse-right-click";
}
