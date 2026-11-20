package com.termux.shell.input;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 外壳动作名称。
 *
 * <p>目的：把键位表与命令面板中出现的动作字符串集中到一处，避免拼写差异导致「绑定了但无响应」。
 *
 * <p>约束：本类型只描述动作名称，不执行动作；执行由窗口宿主完成。未知动作名称应被忽略而非抛出异常，
 * 以便用户在不更新应用的情况下尝试新版键位表。
 */
public final class ShellAction {

    public static final String NEW_TAB = "new-tab";
    public static final String CLOSE_TAB = "close-tab";
    public static final String NEXT_TAB = "next-tab";
    public static final String PREVIOUS_TAB = "previous-tab";
    public static final String MOVE_TAB_LEFT = "move-tab-left";
    public static final String MOVE_TAB_RIGHT = "move-tab-right";
    public static final String SPLIT_RIGHT = "split-right";
    public static final String SPLIT_DOWN = "split-down";
    public static final String CLOSE_PANE = "close-pane";
    public static final String FOCUS_NEXT_PANE = "focus-next-pane";
    public static final String FOCUS_PREVIOUS_PANE = "focus-previous-pane";
    public static final String NEW_WINDOW = "new-window";
    public static final String COMMAND_PALETTE = "command-palette";
    public static final String TOGGLE_TAB_BAR = "toggle-tab-bar";
    public static final String RELOAD_CONFIG = "reload-config";

    private static final Set<String> ALL = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, MOVE_TAB_LEFT, MOVE_TAB_RIGHT,
        SPLIT_RIGHT, SPLIT_DOWN, CLOSE_PANE, FOCUS_NEXT_PANE, FOCUS_PREVIOUS_PANE,
        NEW_WINDOW, COMMAND_PALETTE, TOGGLE_TAB_BAR, RELOAD_CONFIG)));

    private ShellAction() {
    }

    /**
     * 判断动作名称是否由当前版本实现。未知名称用于诊断日志，不影响其余绑定生效。
     */
    public static boolean isKnown(@Nullable String action) {
        return action != null && ALL.contains(action);
    }

    /**
     * 返回全部动作名称。
     */
    @NonNull
    public static Set<String> all() {
        return ALL;
    }
}
