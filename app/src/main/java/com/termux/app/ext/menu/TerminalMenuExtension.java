package com.termux.app.ext.menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;
import com.termux.shared.data.DataUtils;

import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「终端上下文菜单与文本选择工具条」作为可启用的扩展提供。
 *
 * <p>行为 — 启用时按 `terminal-menu-*`、`selection-toolbar-items` 属性解析 {@link TerminalMenuConfig}；
 * 宿主的长按、右键与硬件菜单键都通过 {@link #showMenu()} 打开菜单；属性或配色变化时重新解析；禁用时菜单
 * 不再打开，文本选择工具条也不再显示自定义动作，行为退回宿主默认。</p>
 *
 * <p>约束 — 只能通过 {@link TermuxActivity} 的公开方法读取终端状态（当前会话、选中文本、自动填充、分屏、通知）
 * 并派发动作；禁止读取宿主字段。菜单项的顺序与可见性由 {@link TerminalMenuItems} 的纯函数决定，本类不得再写
 * 一份判断。</p>
 */
public final class TerminalMenuExtension implements Extension {

    /** The stable id of the extension. */
    public static final String ID = "menu";

    /** The preference key of the settings entry that this extension owns. */
    public static final String PREFERENCE_KEY = "terminal-menu";

    /** The configuration that is used while the extension is disabled, so that callers never see a null. */
    @NonNull
    private static final TerminalMenuConfig EMPTY_CONFIG = new TerminalMenuConfig();

    @Nullable
    private TermuxActivity mActivity;
    @Nullable
    private TerminalMenuConfig mConfig;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_terminal_menu_preferences_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_terminal_menu_preferences_summary;
    }

    @NonNull
    @Override
    public List<String> preferenceKeys() {
        return Collections.singletonList(PREFERENCE_KEY);
    }

    @NonNull
    @Override
    public List<ExtensionSettingsPage> settingsPages() {
        return Collections.singletonList(new ExtensionSettingsPage(
            R.string.termux_terminal_menu_preferences_title, R.xml.termux_terminal_menu_preferences,
            TerminalMenuPreferencesFragment.class.getName()));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        if (!(context.activity() instanceof TermuxActivity)) {
            context.logWarn("The terminal menu needs the terminal activity, not " + context.activity());
            return;
        }
        mActivity = (TermuxActivity) context.activity();
        reloadConfig();
        context.logInfo("Terminal menu enabled");
    }

    @Override
    public void onReload() {
        if (mActivity != null) reloadConfig();
    }

    @Override
    public void onDisable() {
        mConfig = null;
        mActivity = null;
    }

    /**
     * @return The configuration of the menu and of the selection toolbar, or an empty one while the extension is
     *         disabled, so that the selection toolbar of the host shows no fork specific action.
     */
    @NonNull
    public TerminalMenuConfig getConfig() {
        return mConfig != null ? mConfig : EMPTY_CONFIG;
    }

    /**
     * Show the terminal context menu, which is a styled dialog instead of the system context menu, with the
     * actions and the order that the `terminal-menu-items` property defines.
     *
     * <p>An action that cannot be used is not shown, like the action that shares the selected text when no text
     * is selected, so that the menu only shows what the user can actually do.</p>
     */
    public void showMenu() {
        TermuxActivity activity = mActivity;
        if (activity == null || mConfig == null) return;
        if (activity.getCurrentSession() == null || activity.getTerminalView() == null) return;

        TerminalMenuItems.State state = new TerminalMenuItems.State(
            activity.isSplitScreenAvailable(),
            activity.isSplitScreenEnabled(),
            activity.isSearchAvailable(),
            activity.isSessionsDrawerAvailable(),
            !DataUtils.isNullOrEmpty(activity.getTerminalView().getStoredSelectedText()),
            activity.getTerminalView().isAutoFillEnabled(),
            activity.getNotificationManager() != null && activity.getNotificationManager().isEnabled(),
            // The list is only useful when a notification was shown since the app was started.
            activity.getNotificationManager() != null
                && !activity.getNotificationManager().getRecentNotifications().isEmpty(),
            activity.getCurrentSession().isRunning());

        List<TerminalMenuDialog.Item> items = TerminalMenuItems.build(mConfig.menuItems, state,
            action -> action.getTitle(activity, activity.getCurrentSession()));

        if (items.isEmpty()) return;

        TerminalMenuDialog.show(activity, mConfig, items, activity::runTerminalMenuAction,
            () -> activity.getTerminalView().unsetStoredSelectedText());
    }

    private void reloadConfig() {
        // The `terminal-menu-*` properties are not part of the list that the shared library converts to internal
        // values, so they are read from the file, like the `status-bar-*` and `notification-*` ones.
        mConfig = TerminalMenuConfig.parse(com.termux.app.properties.ForkProperties.readTermuxProperties());
    }
}
