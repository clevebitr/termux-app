package com.termux.shell.input

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 外壳动作名称。
 *
 * 目的：把键位表与命令面板中出现的动作字符串集中到一处，避免拼写差异导致「绑定了但无响应」。
 *
 * 约束：本类型只描述动作名称，不执行动作；执行由窗口控制器完成。未知动作名称应被忽略而非抛出异常，
 * 以便用户在不更新应用的情况下尝试新版键位表。
 */
object ShellAction {

    const val NEW_TAB = "new-tab"
    const val CLOSE_TAB = "close-tab"
    const val NEXT_TAB = "next-tab"
    const val PREVIOUS_TAB = "previous-tab"
    const val MOVE_TAB_LEFT = "move-tab-left"
    const val MOVE_TAB_RIGHT = "move-tab-right"
    const val SPLIT_RIGHT = "split-right"
    const val SPLIT_DOWN = "split-down"
    const val CLOSE_PANE = "close-pane"
    const val FOCUS_NEXT_PANE = "focus-next-pane"
    const val FOCUS_PREVIOUS_PANE = "focus-previous-pane"
    const val NEW_WINDOW = "new-window"
    const val COMMAND_PALETTE = "command-palette"
    const val TOGGLE_TAB_BAR = "toggle-tab-bar"
    const val RELOAD_CONFIG = "reload-config"

    private val ALL: Set<String> = linkedSetOf(
        NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, MOVE_TAB_LEFT, MOVE_TAB_RIGHT,
        SPLIT_RIGHT, SPLIT_DOWN, CLOSE_PANE, FOCUS_NEXT_PANE, FOCUS_PREVIOUS_PANE,
        NEW_WINDOW, COMMAND_PALETTE, TOGGLE_TAB_BAR, RELOAD_CONFIG,
    )

    /** 判断动作名称是否由当前版本实现。 */
    fun isKnown(action: String?): Boolean = action != null && action in ALL

    /** 返回全部动作名称。 */
    fun all(): Set<String> = ALL
}
