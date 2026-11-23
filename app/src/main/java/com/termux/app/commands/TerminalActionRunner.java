package com.termux.app.commands;

import androidx.annotation.NonNull;

import com.termux.app.terminal.menu.TerminalMenuAction;
import com.termux.shared.logger.Logger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 目的 — 保存「终端菜单动作 → 宿主行为」的唯一映射，使动作的分发不再散落在 Activity 的 `switch` 里。
 *
 * <p>行为 — 构造时把每个 {@link TerminalMenuAction} 绑定到 {@link Host} 上的一个方法，{@link #run} 按键查表
 * 后执行；动作与实现一一对应，新增动作而忘记登记实现会由 {@link #handles} 暴露，并由单元测试在构建前拦下。</p>
 *
 * <p>约束 — 映射在构造时固定，不可在运行期增删；{@code Host} 的方法必须自行保证在会话不存在时安全返回，
 * 因为映射表不携带会话参数，动作是否可用由上层的 {@code TerminalMenuItems.isEnabled} 决定。</p>
 */
public final class TerminalActionRunner {

    private static final String LOG_TAG = "TerminalActionRunner";

    /**
     * The behaviours that the actions trigger.
     *
     * <p>Every method takes no argument so that the mapping can be built from method references, and every
     * method has to tolerate a missing session, since the menu and the palette both decide whether an action
     * is available before it runs.</p>
     */
    public interface Host {
        void showTerminalSearch();

        void openSessionsDrawer();

        void showCommandPalette();

        void scrollToPreviousCommand();

        void scrollToNextCommand();

        void splitScreenVertically();

        void splitScreenHorizontally();

        void closeSplitScreen();

        void selectUrl();

        void shareTranscript();

        void shareSelectedText();

        void autofillUsername();

        void autofillPassword();

        void resetTerminal();

        void killSession();

        void showStylingDialog();

        void toggleKeepScreenOn();

        void openHelp();

        void openSettings();

        void reportIssue();

        void showRecentNotifications();

        void sendTestNotification();
    }

    @NonNull
    private final Map<TerminalMenuAction, Runnable> actions;

    public TerminalActionRunner(@NonNull Host host) {
        Map<TerminalMenuAction, Runnable> actions = new EnumMap<>(TerminalMenuAction.class);

        actions.put(TerminalMenuAction.SEARCH, host::showTerminalSearch);
        actions.put(TerminalMenuAction.SESSIONS, host::openSessionsDrawer);
        actions.put(TerminalMenuAction.COMMAND_PALETTE, host::showCommandPalette);
        actions.put(TerminalMenuAction.PREVIOUS_COMMAND, host::scrollToPreviousCommand);
        actions.put(TerminalMenuAction.NEXT_COMMAND, host::scrollToNextCommand);
        actions.put(TerminalMenuAction.SPLIT_VERTICAL, host::splitScreenVertically);
        actions.put(TerminalMenuAction.SPLIT_HORIZONTAL, host::splitScreenHorizontally);
        actions.put(TerminalMenuAction.SPLIT_CLOSE, host::closeSplitScreen);
        actions.put(TerminalMenuAction.SELECT_URL, host::selectUrl);
        actions.put(TerminalMenuAction.SHARE_TRANSCRIPT, host::shareTranscript);
        actions.put(TerminalMenuAction.SHARE_SELECTED_TEXT, host::shareSelectedText);
        actions.put(TerminalMenuAction.AUTOFILL_USERNAME, host::autofillUsername);
        actions.put(TerminalMenuAction.AUTOFILL_PASSWORD, host::autofillPassword);
        actions.put(TerminalMenuAction.RESET_TERMINAL, host::resetTerminal);
        actions.put(TerminalMenuAction.KILL_PROCESS, host::killSession);
        actions.put(TerminalMenuAction.STYLING, host::showStylingDialog);
        actions.put(TerminalMenuAction.KEEP_SCREEN_ON, host::toggleKeepScreenOn);
        actions.put(TerminalMenuAction.HELP, host::openHelp);
        actions.put(TerminalMenuAction.SETTINGS, host::openSettings);
        actions.put(TerminalMenuAction.REPORT, host::reportIssue);
        actions.put(TerminalMenuAction.RECENT_NOTIFICATIONS, host::showRecentNotifications);
        actions.put(TerminalMenuAction.TEST_NOTIFICATION, host::sendTestNotification);

        this.actions = Collections.unmodifiableMap(actions);
    }

    /** @return Whether the action has an implementation; {@code false} means the mapping is incomplete. */
    public boolean handles(@NonNull TerminalMenuAction action) {
        return actions.containsKey(action);
    }

    /**
     * Run the action.
     *
     * <p>An action without implementation is logged and ignored instead of throwing, so that a partially
     * updated build still opens the menu.</p>
     */
    public void run(@NonNull TerminalMenuAction action) {
        Runnable actionRunner = actions.get(action);
        if (actionRunner == null) {
            Logger.logWarn(LOG_TAG, "No implementation registered for the action \"" + action.getId() + "\"");
            return;
        }
        actionRunner.run();
    }
}
