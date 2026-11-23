package com.termux.shell.ui

import androidx.annotation.StringRes
import com.termux.shell.R

/**
 * 一次性提示。
 *
 * 目的：把「发生了什么」与「如何呈现」分开。状态持有者只产生 [ShellNotice]，由界面层映射为文案并通过
 * 提示条显示，因此状态层不依赖 Android 资源，可以被单元测试覆盖。
 *
 * 约束：提示是一次性事件，界面读取后必须调用 `consumeNotice()` 清除，避免重组时重复显示。
 */
enum class ShellNotice {
    TAB_LIMIT,
    PANE_LIMIT,
    TAB_CLOSE_DENIED,
    PANE_CLOSE_DENIED,
    CONFIG_RELOADED,
    CONFIG_RELOAD_FAILED,
    SETTINGS_SAVE_FAILED,
    PROFILE_SAVE_FAILED,
    PROFILE_DELETED,
    PROFILE_DELETE_FAILED,
    SESSION_ACTION_UNAVAILABLE,
}

/** 提示对应的字符串资源。 */
@get:StringRes
val ShellNotice.stringRes: Int
    get() = when (this) {
        ShellNotice.TAB_LIMIT -> R.string.shell_tab_limit
        ShellNotice.PANE_LIMIT -> R.string.shell_pane_limit
        ShellNotice.TAB_CLOSE_DENIED -> R.string.shell_tab_close_denied
        ShellNotice.PANE_CLOSE_DENIED -> R.string.shell_pane_close_denied
        ShellNotice.CONFIG_RELOADED -> R.string.shell_config_reloaded
        ShellNotice.CONFIG_RELOAD_FAILED -> R.string.shell_config_reload_failed
        ShellNotice.SETTINGS_SAVE_FAILED -> R.string.settings_save_failed
        ShellNotice.PROFILE_SAVE_FAILED -> R.string.settings_profile_save_failed
        ShellNotice.PROFILE_DELETED -> R.string.settings_profile_deleted
        ShellNotice.PROFILE_DELETE_FAILED -> R.string.settings_profile_delete_failed
        ShellNotice.SESSION_ACTION_UNAVAILABLE -> R.string.session_action_unavailable
    }
