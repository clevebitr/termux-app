package com.termux.app.commands;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 目的 — 把命令的快捷键（如 `Ctrl+Alt+F`）解析成可比较的按键组合，使快捷键既能显示也能真正触发命令。
 *
 * <p>行为 — {@link #matches} 判断一个按键事件是否与显示的快捷键等价：修饰键必须完全一致，主键按字母或按
 * 方向键名比较。{@link #normalize} 把用户输入（`ctrl+alt+f`、`C-A-F`）整理成同一形式，使设置搜索与命令面板
 * 的过滤能按快捷键命中。</p>
 *
 * <p>约束 — 只支持 Ctrl、Alt、Shift 三个修饰键与单主键；显示文本与解析结果来自同一份声明，因此不会出现
 * 「面板写着某个快捷键、实际按下去却是别的动作」的情况。</p>
 */
public final class ShellCommandShortcut {

    private static final String KEY_CTRL = "ctrl";
    private static final String KEY_ALT = "alt";
    private static final String KEY_SHIFT = "shift";

    private ShellCommandShortcut() {
    }

    /**
     * @return Whether {@code event} presses exactly the combination of {@code shortcut}, like {@code Ctrl+Alt+F}.
     *         A shortcut that cannot be parsed never matches, so a wrong label cannot take a key from the terminal.
     */
    public static boolean matches(@Nullable String shortcut, @NonNull KeyEvent event) {
        if (shortcut == null || shortcut.isEmpty()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        return matches(shortcut, event.getKeyCode(), event.getUnicodeChar(),
            event.isCtrlPressed(), event.isAltPressed(), event.isShiftPressed());
    }

    /**
     * The platform independent part of {@link #matches}, so that the parsing can be tested without a key event.
     *
     * @param unicodeChar The character of the key without modifiers, or {@code 0} for a key that is not one.
     */
    static boolean matches(@Nullable String shortcut, int keyCode, int unicodeChar,
                           boolean ctrlPressed, boolean altPressed, boolean shiftPressed) {
        if (shortcut == null || shortcut.isEmpty()) return false;

        String[] parts = shortcut.split("\\+");
        boolean ctrl = false, alt = false, shift = false;
        int expectedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        char keyChar = 0;

        for (String rawPart : parts) {
            String part = rawPart.trim().toLowerCase(Locale.ROOT);
            if (part.isEmpty()) return false;
            if (KEY_CTRL.equals(part)) ctrl = true;
            else if (KEY_ALT.equals(part)) alt = true;
            else if (KEY_SHIFT.equals(part)) shift = true;
            else if (part.length() == 1) keyChar = part.charAt(0);
            else expectedKeyCode = keyCodeOf(part);
        }

        if (keyChar == 0 && expectedKeyCode == KeyEvent.KEYCODE_UNKNOWN) return false;
        if (ctrlPressed != ctrl || altPressed != alt || shiftPressed != shift) return false;

        if (keyChar != 0) {
            if (unicodeChar == 0) return false;
            char pressed = Character.toLowerCase((char) unicodeChar);
            return pressed == keyChar;
        }
        return keyCode == expectedKeyCode;
    }

    /**
     * @return The normalized form of {@code shortcut}, like {@code ctrl+alt+f}, with the modifiers in a fixed order,
     *         or the trimmed lower case value when it cannot be parsed.
     */
    @NonNull
    public static String normalize(@Nullable String shortcut) {
        if (shortcut == null) return "";

        boolean ctrl = false, alt = false, shift = false;
        String key = null;
        for (String rawPart : shortcut.split("\\+")) {
            String part = rawPart.trim().toLowerCase(Locale.ROOT);
            if (part.isEmpty()) continue;
            if (KEY_CTRL.equals(part)) ctrl = true;
            else if (KEY_ALT.equals(part)) alt = true;
            else if (KEY_SHIFT.equals(part)) shift = true;
            else key = part;
        }
        if (key == null) return shortcut.trim().toLowerCase(Locale.ROOT);

        StringBuilder normalized = new StringBuilder();
        if (ctrl) normalized.append(KEY_CTRL).append('+');
        if (alt) normalized.append(KEY_ALT).append('+');
        if (shift) normalized.append(KEY_SHIFT).append('+');
        return normalized.append(key).toString();
    }

    /** @return The key code of a named key, like {@code up} or {@code pgup}, or {@link KeyEvent#KEYCODE_UNKNOWN}. */
    private static int keyCodeOf(@NonNull String name) {
        switch (name) {
            case "up": return KeyEvent.KEYCODE_DPAD_UP;
            case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "enter": return KeyEvent.KEYCODE_ENTER;
            case "tab": return KeyEvent.KEYCODE_TAB;
            case "space": return KeyEvent.KEYCODE_SPACE;
            case "esc":
            case "escape": return KeyEvent.KEYCODE_ESCAPE;
            case "pgup":
            case "pageup": return KeyEvent.KEYCODE_PAGE_UP;
            case "pgdn":
            case "pagedown": return KeyEvent.KEYCODE_PAGE_DOWN;
            case "home": return KeyEvent.KEYCODE_MOVE_HOME;
            case "end": return KeyEvent.KEYCODE_MOVE_END;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
