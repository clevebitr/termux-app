package com.termux.shell.commands;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shell.R;
import com.termux.shell.input.KeymapTable;
import com.termux.shell.input.ShellAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 命令面板的命令清单构建器。
 *
 * <p>目的：把「有哪些命令、如何展示、何时可执行」集中在一处，使窗口宿主只需提供动作与状态。
 *
 * <p>行为：命令按「标签页 → 窗格 → 窗口与界面」分组排列；快捷键文本取自键位表，未绑定则留空；
 * 当前状态下不可执行的命令仍然展示，但标记为不可执行。
 *
 * <p>约束：返回的列表不可变；命令动作闭包引用调用方提供的 {@link Actions}，其生命周期由调用方保证。
 */
public final class CommandCatalog {

    /**
     * 命令动作宿主。
     */
    public interface Actions {

        void newTab();

        void closeTab();

        void nextTab();

        void previousTab();

        void moveTabLeft();

        void moveTabRight();

        void splitRight();

        void splitDown();

        void closePane();

        void focusNextPane();

        void focusPreviousPane();

        void newWindow();

        void toggleTabBar();

        void reloadConfig();
    }

    /**
     * 当前窗口状态，用于决定命令是否可执行。
     */
    public interface State {

        boolean canCloseTab();

        boolean canSplitPane();

        boolean canClosePane();
    }

    private CommandCatalog() {
    }

    @NonNull
    public static List<ShellCommand> create(@NonNull Context context, @NonNull KeymapTable keymap,
                                            @NonNull Actions actions, @NonNull State state) {
        String tabCategory = context.getString(R.string.command_category_tab);
        String paneCategory = context.getString(R.string.command_category_pane);
        String windowCategory = context.getString(R.string.command_category_window);

        List<ShellCommand> commands = new ArrayList<>();
        commands.add(new ShellCommand(ShellAction.NEW_TAB, context.getString(R.string.command_new_tab),
            tabCategory, keymap.findShortcut(ShellAction.NEW_TAB), true, actions::newTab));
        commands.add(new ShellCommand(ShellAction.CLOSE_TAB, context.getString(R.string.command_close_tab),
            tabCategory, keymap.findShortcut(ShellAction.CLOSE_TAB), state.canCloseTab(), actions::closeTab));
        commands.add(new ShellCommand(ShellAction.NEXT_TAB, context.getString(R.string.command_next_tab),
            tabCategory, keymap.findShortcut(ShellAction.NEXT_TAB), true, actions::nextTab));
        commands.add(new ShellCommand(ShellAction.PREVIOUS_TAB, context.getString(R.string.command_previous_tab),
            tabCategory, keymap.findShortcut(ShellAction.PREVIOUS_TAB), true, actions::previousTab));
        commands.add(new ShellCommand(ShellAction.MOVE_TAB_LEFT, context.getString(R.string.command_move_tab_left),
            tabCategory, keymap.findShortcut(ShellAction.MOVE_TAB_LEFT), true, actions::moveTabLeft));
        commands.add(new ShellCommand(ShellAction.MOVE_TAB_RIGHT, context.getString(R.string.command_move_tab_right),
            tabCategory, keymap.findShortcut(ShellAction.MOVE_TAB_RIGHT), true, actions::moveTabRight));

        commands.add(new ShellCommand(ShellAction.SPLIT_RIGHT, context.getString(R.string.command_split_right),
            paneCategory, keymap.findShortcut(ShellAction.SPLIT_RIGHT), state.canSplitPane(), actions::splitRight));
        commands.add(new ShellCommand(ShellAction.SPLIT_DOWN, context.getString(R.string.command_split_down),
            paneCategory, keymap.findShortcut(ShellAction.SPLIT_DOWN), state.canSplitPane(), actions::splitDown));
        commands.add(new ShellCommand(ShellAction.CLOSE_PANE, context.getString(R.string.command_close_pane),
            paneCategory, keymap.findShortcut(ShellAction.CLOSE_PANE), state.canClosePane(), actions::closePane));
        commands.add(new ShellCommand(ShellAction.FOCUS_NEXT_PANE, context.getString(R.string.command_focus_next_pane),
            paneCategory, keymap.findShortcut(ShellAction.FOCUS_NEXT_PANE), true, actions::focusNextPane));
        commands.add(new ShellCommand(ShellAction.FOCUS_PREVIOUS_PANE,
            context.getString(R.string.command_focus_previous_pane),
            paneCategory, keymap.findShortcut(ShellAction.FOCUS_PREVIOUS_PANE), true, actions::focusPreviousPane));

        commands.add(new ShellCommand(ShellAction.NEW_WINDOW, context.getString(R.string.command_new_window),
            windowCategory, keymap.findShortcut(ShellAction.NEW_WINDOW), true, actions::newWindow));
        commands.add(new ShellCommand(ShellAction.TOGGLE_TAB_BAR, context.getString(R.string.command_toggle_tab_bar),
            windowCategory, keymap.findShortcut(ShellAction.TOGGLE_TAB_BAR), true, actions::toggleTabBar));
        commands.add(new ShellCommand(ShellAction.RELOAD_CONFIG, context.getString(R.string.command_reload_config),
            windowCategory, keymap.findShortcut(ShellAction.RELOAD_CONFIG), true, actions::reloadConfig));

        return Collections.unmodifiableList(commands);
    }
}
