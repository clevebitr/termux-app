package com.termux.shell.input;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外壳级键位表。
 *
 * <p>目的：把外壳快捷键从代码中分离为可配置数据，使同一份外壳可适配不同键盘布局与用户习惯，
 * 并使快捷键与终端键序编码互不干扰。
 *
 * <p>语法：每行一条绑定，格式为 {@code 修饰键+按键 = 动作}，例如：
 * <pre>
 * # 窗口与窗格
 * ctrl+shift+t = new-tab
 * ctrl+shift+w = close-tab
 * alt+left     = focus-pane-left
 * alt+1        = focus-pane-1
 * </pre>
 * 以 {@code #} 开头的行与空行被忽略；修饰键取值为 {@code ctrl}、{@code alt}、{@code shift}、
 * {@code meta}，可组合；按键取值为单个字母、单个数字，或 {@code tab}、{@code enter}、
 * {@code escape}、{@code space}、{@code left}、{@code right}、{@code up}、{@code down}、
 * {@code home}、{@code end}、{@code pageup}、{@code pagedown}、{@code backspace}、
 * {@code delete}。
 *
 * <p>约束：
 * <ul>
 *     <li>解析为纯计算过程，不得依赖 Android 运行时，因此可在 JVM 单元测试中直接验证；</li>
 *     <li>非法行被跳过而非抛出异常，跳过的行数与原因由调用方记录日志；</li>
 *     <li>同一按键组合重复出现时，首条绑定生效，后续绑定被忽略。</li>
 * </ul>
 */
public final class KeymapTable {

    /** 空表。用于外壳快捷键被整体禁用的场景。 */
    public static final KeymapTable EMPTY = new KeymapTable(Collections.emptyMap());

    private static final int MODIFIER_NONE = 0;
    /** 内部修饰位。使用自有位定义而非平台掩码，避免平台掩码的低位落在同一位置导致组合冲突。 */
    private static final int MODIFIER_CTRL = 1;
    private static final int MODIFIER_ALT = 1 << 1;
    private static final int MODIFIER_SHIFT = 1 << 2;
    private static final int MODIFIER_META = 1 << 3;

    private final Map<Integer, String> mBindings;

    private KeymapTable(@NonNull Map<Integer, String> bindings) {
        mBindings = bindings;
    }

    /**
     * 解析键位表文本行。
     *
     * @param lines 按文件顺序排列的文本行，可以为 null。
     * @return 解析结果；无法识别的行被跳过。
     */
    @NonNull
    public static KeymapTable parse(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return EMPTY;
        }
        Map<Integer, String> bindings = new HashMap<>();
        for (String rawLine : lines) {
            Binding parsed = parseLine(rawLine);
            if (parsed == null) {
                continue;
            }
            int key = bindingKey(parsed.mKeyCode, parsed.mModifiers);
            if (!bindings.containsKey(key)) {
                bindings.put(key, parsed.mAction);
            }
        }
        return new KeymapTable(Collections.unmodifiableMap(bindings));
    }

    /**
     * 便于单元测试与调试的字符串入口。
     */
    @NonNull
    public static KeymapTable parse(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return EMPTY;
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.add(line);
        }
        return parse(lines);
    }

    /**
     * 查找按键组合对应的动作。
     *
     * @param keyCode   平台键码。
     * @param metaState 修饰键状态，可包含左右变体位；实现内部只保留通用修饰位。
     * @return 动作名称；未绑定时返回 null。
     */
    @Nullable
    public String findAction(int keyCode, int metaState) {
        if (mBindings.isEmpty()) {
            return null;
        }
        return mBindings.get(bindingKey(keyCode, normalizeModifiers(metaState)));
    }

    public int size() {
        return mBindings.size();
    }

    public boolean isEmpty() {
        return mBindings.isEmpty();
    }

    @NonNull
    private static Binding parseLine(@Nullable String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        int separator = line.indexOf('=');
        if (separator <= 0 || separator == line.length() - 1) {
            return null;
        }
        String accelerator = line.substring(0, separator).trim();
        String action = line.substring(separator + 1).trim();
        if (accelerator.isEmpty() || action.isEmpty()) {
            return null;
        }

        String[] tokens = accelerator.split("\\+");
        int modifiers = MODIFIER_NONE;
        for (int i = 0; i < tokens.length - 1; i++) {
            int modifier = modifierOf(tokens[i].trim());
            if (modifier == MODIFIER_NONE) {
                return null;
            }
            modifiers |= modifier;
        }

        int keyCode = keyCodeOf(tokens[tokens.length - 1].trim());
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return null;
        }
        return new Binding(keyCode, modifiers, action);
    }

    private static int modifierOf(@NonNull String token) {
        switch (token.toLowerCase(java.util.Locale.ROOT)) {
            case "ctrl":
            case "control":
                return MODIFIER_CTRL;
            case "alt":
                return MODIFIER_ALT;
            case "shift":
                return MODIFIER_SHIFT;
            case "meta":
            case "win":
            case "super":
                return MODIFIER_META;
            default:
                return MODIFIER_NONE;
        }
    }

    private static int keyCodeOf(@NonNull String token) {
        String name = token.toLowerCase(java.util.Locale.ROOT);
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'a' && c <= 'z') {
                return KeyEvent.KEYCODE_A + (c - 'a');
            }
            if (c >= '0' && c <= '9') {
                return KeyEvent.KEYCODE_0 + (c - '0');
            }
            return KeyEvent.KEYCODE_UNKNOWN;
        }
        switch (name) {
            case "tab":
                return KeyEvent.KEYCODE_TAB;
            case "enter":
            case "return":
                return KeyEvent.KEYCODE_ENTER;
            case "escape":
            case "esc":
                return KeyEvent.KEYCODE_ESCAPE;
            case "space":
                return KeyEvent.KEYCODE_SPACE;
            case "left":
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right":
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "up":
                return KeyEvent.KEYCODE_DPAD_UP;
            case "down":
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case "home":
                return KeyEvent.KEYCODE_MOVE_HOME;
            case "end":
                return KeyEvent.KEYCODE_MOVE_END;
            case "pageup":
            case "pgup":
                return KeyEvent.KEYCODE_PAGE_UP;
            case "pagedown":
            case "pgdn":
                return KeyEvent.KEYCODE_PAGE_DOWN;
            case "backspace":
                return KeyEvent.KEYCODE_DEL;
            case "delete":
            case "del":
                return KeyEvent.KEYCODE_FORWARD_DEL;
            default:
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    /**
     * 把平台修饰位归一化为内部修饰位。
     *
     * <p>说明：平台掩码包含左右变体（例如 {@code META_CTRL_LEFT_ON}），且部分通用位与变体位不重叠，
     * 因此必须按掩码判定，不能直接按通用位判定。
     */
    private static int normalizeModifiers(int metaState) {
        int normalized = MODIFIER_NONE;
        if ((metaState & KeyEvent.META_CTRL_MASK) != 0) {
            normalized |= MODIFIER_CTRL;
        }
        if ((metaState & KeyEvent.META_ALT_MASK) != 0) {
            normalized |= MODIFIER_ALT;
        }
        if ((metaState & KeyEvent.META_SHIFT_MASK) != 0) {
            normalized |= MODIFIER_SHIFT;
        }
        if ((metaState & KeyEvent.META_META_MASK) != 0) {
            normalized |= MODIFIER_META;
        }
        return normalized;
    }

    private static int bindingKey(int keyCode, int modifiers) {
        return (keyCode << 8) | (modifiers & 0x0F);
    }

    /**
     * 解析后的单条绑定。
     */
    private static final class Binding {

        private final int mKeyCode;
        private final int mModifiers;
        private final String mAction;

        private Binding(int keyCode, int modifiers, @NonNull String action) {
            mKeyCode = keyCode;
            mModifiers = modifiers;
            mAction = action;
        }
    }
}
