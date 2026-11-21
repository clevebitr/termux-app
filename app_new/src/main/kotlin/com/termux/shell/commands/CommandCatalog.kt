package com.termux.shell.commands

import android.content.Context
import com.termux.shell.R
import com.termux.shell.input.KeymapTable
import com.termux.shell.input.ShellAction
import com.termux.shell.pane.PaneDirection

/**
 * 命令面板的命令清单构建器。
 *
 * 目的：把「有哪些命令、如何展示、何时可执行」集中在一处，使窗口控制器只需提供动作与状态。
 *
 * 行为：命令按「标签页 → 窗格 → 窗口与界面」分组排列；快捷键文本取自键位表，未绑定则留空；
 * 当前状态下不可执行的命令仍然展示，但标记为不可执行。
 *
 * 约束：返回的列表不可变；命令动作闭包引用调用方提供的 [Actions]，其生命周期由调用方保证。
 */
object CommandCatalog {

    /** 命令动作宿主。 */
    interface Actions {
        fun newTab()
        fun closeTab()
        fun nextTab()
        fun previousTab()
        fun moveTabLeft()
        fun moveTabRight()
        fun splitRight()
        fun splitDown()
        fun closePane()
        fun focusNextPane()
        fun focusPreviousPane()
        fun focusPane(direction: PaneDirection)
        fun newWindow()
        fun toggleTabBar()
        fun reloadConfig()
    }

    /** 当前窗口状态，用于决定命令是否可执行。 */
    interface State {
        fun canCloseTab(): Boolean
        fun canSplitPane(): Boolean
        fun canClosePane(): Boolean
    }

    fun create(
        context: Context,
        keymap: KeymapTable,
        actions: Actions,
        state: State,
    ): List<ShellCommand> {
        val tabCategory = context.getString(R.string.command_category_tab)
        val paneCategory = context.getString(R.string.command_category_pane)
        val windowCategory = context.getString(R.string.command_category_window)

        return listOf(
            ShellCommand(ShellAction.NEW_TAB, context.getString(R.string.command_new_tab), tabCategory,
                keymap.findShortcut(ShellAction.NEW_TAB), true, actions::newTab),
            ShellCommand(ShellAction.CLOSE_TAB, context.getString(R.string.command_close_tab), tabCategory,
                keymap.findShortcut(ShellAction.CLOSE_TAB), state.canCloseTab(), actions::closeTab),
            ShellCommand(ShellAction.NEXT_TAB, context.getString(R.string.command_next_tab), tabCategory,
                keymap.findShortcut(ShellAction.NEXT_TAB), true, actions::nextTab),
            ShellCommand(ShellAction.PREVIOUS_TAB, context.getString(R.string.command_previous_tab), tabCategory,
                keymap.findShortcut(ShellAction.PREVIOUS_TAB), true, actions::previousTab),
            ShellCommand(ShellAction.MOVE_TAB_LEFT, context.getString(R.string.command_move_tab_left), tabCategory,
                keymap.findShortcut(ShellAction.MOVE_TAB_LEFT), true, actions::moveTabLeft),
            ShellCommand(ShellAction.MOVE_TAB_RIGHT, context.getString(R.string.command_move_tab_right), tabCategory,
                keymap.findShortcut(ShellAction.MOVE_TAB_RIGHT), true, actions::moveTabRight),
            ShellCommand(ShellAction.SPLIT_RIGHT, context.getString(R.string.command_split_right), paneCategory,
                keymap.findShortcut(ShellAction.SPLIT_RIGHT), state.canSplitPane(), actions::splitRight),
            ShellCommand(ShellAction.SPLIT_DOWN, context.getString(R.string.command_split_down), paneCategory,
                keymap.findShortcut(ShellAction.SPLIT_DOWN), state.canSplitPane(), actions::splitDown),
            ShellCommand(ShellAction.CLOSE_PANE, context.getString(R.string.command_close_pane), paneCategory,
                keymap.findShortcut(ShellAction.CLOSE_PANE), state.canClosePane(), actions::closePane),
            ShellCommand(ShellAction.FOCUS_NEXT_PANE, context.getString(R.string.command_focus_next_pane), paneCategory,
                keymap.findShortcut(ShellAction.FOCUS_NEXT_PANE), true, actions::focusNextPane),
            ShellCommand(ShellAction.FOCUS_PREVIOUS_PANE, context.getString(R.string.command_focus_previous_pane),
                paneCategory, keymap.findShortcut(ShellAction.FOCUS_PREVIOUS_PANE), true, actions::focusPreviousPane),
            ShellCommand(ShellAction.FOCUS_PANE_LEFT, context.getString(R.string.command_focus_pane_left),
                paneCategory, keymap.findShortcut(ShellAction.FOCUS_PANE_LEFT), true,
                { actions.focusPane(PaneDirection.LEFT) }),
            ShellCommand(ShellAction.FOCUS_PANE_RIGHT, context.getString(R.string.command_focus_pane_right),
                paneCategory, keymap.findShortcut(ShellAction.FOCUS_PANE_RIGHT), true,
                { actions.focusPane(PaneDirection.RIGHT) }),
            ShellCommand(ShellAction.FOCUS_PANE_UP, context.getString(R.string.command_focus_pane_up),
                paneCategory, keymap.findShortcut(ShellAction.FOCUS_PANE_UP), true,
                { actions.focusPane(PaneDirection.UP) }),
            ShellCommand(ShellAction.FOCUS_PANE_DOWN, context.getString(R.string.command_focus_pane_down),
                paneCategory, keymap.findShortcut(ShellAction.FOCUS_PANE_DOWN), true,
                { actions.focusPane(PaneDirection.DOWN) }),
            ShellCommand(ShellAction.NEW_WINDOW, context.getString(R.string.command_new_window), windowCategory,
                keymap.findShortcut(ShellAction.NEW_WINDOW), true, actions::newWindow),
            ShellCommand(ShellAction.TOGGLE_TAB_BAR, context.getString(R.string.command_toggle_tab_bar),
                windowCategory, keymap.findShortcut(ShellAction.TOGGLE_TAB_BAR), true, actions::toggleTabBar),
            ShellCommand(ShellAction.RELOAD_CONFIG, context.getString(R.string.command_reload_config),
                windowCategory, keymap.findShortcut(ShellAction.RELOAD_CONFIG), true, actions::reloadConfig),
        )
    }
}
