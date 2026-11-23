package com.termux.app.terminal;

import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 目的 — 决定终端视图向输入法上报的输入类型，使连接硬件键盘时的输入法（如 Gboard）可以把终端识别为普通可编辑
 * 文本框，从而显示悬浮栏，同时保留终端原有的字符输入行为。
 *
 * <p>行为 — `auto` 使用 `TerminalView` 结合属性与输入法语言算出的类型，与上游完全一致；`text` 上报
 * `TYPE_CLASS_TEXT | NO_SUGGESTIONS | NORMAL`，输入法会按文本框处理，但不会把联想文本提交给终端；
 * `password` 上报旧的 `VISIBLE_PASSWORD` 变体；`none` 上报 `TYPE_NULL`；`hardware` 在接有物理
 * 键盘时上报 `TYPE_NULL`，其余情况等同 `auto`。解析失败回退到 `auto`。</p>
 *
 * <p>输入法在 `text`、`password` 与 `auto` 的文本类类型下会把物理按键先收进自己再以文本提交，字符与
 * Tab、方向键因此走两条通道，顺序可能错乱；`hardware` 通过 `TYPE_NULL` 让输入法不接管物理按键，
 * 顺序回到与桌面终端一致。中文输入由软键盘触发：软键盘弹出期间 `hardware` 改为上报文本框类型，
 * 输入法因此可以显示悬浮栏与候选词，物理键也一并进入拼音组合；软键盘收起后重新回到 `TYPE_NULL`。</p>
 *
 * <p>约束 — 只影响 {@code EditorInfo}，不改变输入连接、按键或终端渲染；切换取值后必须调用
 * {@code TerminalView.restartInputConnection()} 才会让输入法重新读取类型，这一调用由
 * {@code TermuxTerminalViewClient.onReloadProperties()} 负责。</p>
 */
public enum ImeInputType {

    /** Keep the input type that the view computes from the properties and the IME language. */
    AUTO("auto"),
    /** Present the terminal as a normal text editor, which is what makes an IME toolbar appear. */
    TEXT("text"),
    /** Present the terminal as a password field, the legacy workaround for some IMEs. */
    PASSWORD("password"),
    /** Report {@code TYPE_NULL}, the plainest terminal input type. */
    NONE("none"),
    /** Report {@code TYPE_NULL} while a hardware keyboard is attached, so that its keys bypass the IME. */
    HARDWARE("hardware");

    @NonNull
    private final String mValue;

    ImeInputType(@NonNull String value) {
        mValue = value;
    }

    /** @return The value of the `ime-input-type` property that selects this mode. */
    @NonNull
    public String getValue() {
        return mValue;
    }

    /** @return The mode of {@code value}, or {@link #AUTO} if it is not a known mode. */
    @NonNull
    public static ImeInputType parse(@Nullable String value) {
        if (value == null) return AUTO;

        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "text":
                return TEXT;
            case "password":
                return PASSWORD;
            case "none":
                return NONE;
            case "hardware":
                return HARDWARE;
            case "auto":
            default:
                return AUTO;
        }
    }

    /**
     * @param defaultInputType The input type that the terminal view computed, which is used by {@link #AUTO}.
     * @return The input type that is reported to the IME.
     */
    public int inputType(int defaultInputType) {
        // Without the state of the keyboards the `hardware` mode is the same as `auto`.
        return inputType(defaultInputType, false, false);
    }

    /**
     * @return A normal text field without suggestions: the IME shows its editor affordances, like the floating
     *         toolbar and the candidate view, but does not offer completions for a terminal.
     */
    private static int textEditorInputType() {
        return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | InputType.TYPE_TEXT_VARIATION_NORMAL;
    }

    /**
     * @param defaultInputType The input type that the terminal view computed, which `auto` and an unattached
     *                         hardware keyboard use.
     * @param hardwareKeyboardAttached Whether a hardware keyboard is attached, which `hardware` turns into
     *                                 {@code TYPE_NULL} so that the IME does not take the physical keys.
     * @param softKeyboardVisible Whether the soft keyboard is visible, during which `hardware` presents the
     *                            terminal as a text editor so that the IME can compose text, like pinyin.
     * @return The input type that is reported to the IME.
     */
    public int inputType(int defaultInputType, boolean hardwareKeyboardAttached, boolean softKeyboardVisible) {
        switch (this) {
            case HARDWARE:
                if (!hardwareKeyboardAttached) return defaultInputType;
                return softKeyboardVisible ? textEditorInputType() : InputType.TYPE_NULL;
            case TEXT:
                return textEditorInputType();
            case PASSWORD:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            case NONE:
                return InputType.TYPE_NULL;
            case AUTO:
            default:
                return defaultInputType;
        }
    }
}
