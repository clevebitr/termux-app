package com.termux.app.ext.menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;

import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「终端上下文菜单与文本选择工具条」作为可启用的扩展提供。
 *
 * <p>行为 — 启用时按 `terminal-menu-items`、`selection-toolbar-items` 属性解析 {@link TerminalMenuConfig}；
 * 面板与宿主通过 {@link #getConfig()} 读取动作集合与顺序；属性变化时重新解析；禁用时面板退回默认动作集合，
 * 文本选择工具条也不再显示自定义动作。</p>
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
    private static final TerminalMenuConfig DEFAULT_CONFIG = TerminalMenuConfig.defaults();

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
        return mConfig != null ? mConfig : DEFAULT_CONFIG;
    }

    private void reloadConfig() {
        // The `terminal-menu-*` properties are not part of the list that the shared library converts to internal
        // values, so they are read from the file, like the `status-bar-*` and `notification-*` ones.
        mConfig = TerminalMenuConfig.parse(com.termux.app.properties.ForkProperties.readTermuxProperties());
    }
}
