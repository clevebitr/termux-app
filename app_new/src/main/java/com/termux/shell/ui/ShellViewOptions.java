package com.termux.shell.ui;

import androidx.annotation.NonNull;

import com.termux.shell.config.SettingsRepository;
import com.termux.shell.config.SettingsSpec;

/**
 * 外壳视图选项。
 *
 * <p>目的：把配置文件中与界面形态相关的键解析为不可变值对象，使 {@link LayoutHost} 与
 * {@link TabStripView} 只接收已解释的选项，而不直接读取配置。
 *
 * <p>约束：本类型不可变；配置文件中的非法取值一律回退到 {@link SettingsSpec} 给出的默认值。
 */
public final class ShellViewOptions {

    private final boolean mTabBarVisible;
    private final boolean mTabBarAtBottom;
    private final boolean mShowTabCloseButton;
    private final boolean mEqualTabWidth;

    private ShellViewOptions(boolean tabBarVisible, boolean tabBarAtBottom,
                             boolean showTabCloseButton, boolean equalTabWidth) {
        mTabBarVisible = tabBarVisible;
        mTabBarAtBottom = tabBarAtBottom;
        mShowTabCloseButton = showTabCloseButton;
        mEqualTabWidth = equalTabWidth;
    }

    /**
     * 按配置文件构造视图选项。
     *
     * @param settings        配置来源。
     * @param tabBarVisible   标签栏是否可见。该值可能被运行时切换覆盖，因此由调用方显式传入。
     */
    @NonNull
    public static ShellViewOptions fromSettings(@NonNull SettingsRepository settings, boolean tabBarVisible) {
        boolean atBottom = SettingsSpec.TAB_POSITION_BOTTOM.equals(
            settings.getString(SettingsSpec.KEY_TAB_BAR_POSITION, SettingsSpec.TAB_POSITION_TOP));
        boolean showClose = settings.getBoolean(SettingsSpec.KEY_TAB_CLOSE_BUTTON, true);
        boolean equalWidth = !SettingsSpec.TAB_WIDTH_COMPACT.equals(
            settings.getString(SettingsSpec.KEY_TAB_WIDTH_MODE, SettingsSpec.TAB_WIDTH_EQUAL));
        return new ShellViewOptions(tabBarVisible, atBottom, showClose, equalWidth);
    }

    @NonNull
    public ShellViewOptions withTabBarVisible(boolean visible) {
        return new ShellViewOptions(visible, mTabBarAtBottom, mShowTabCloseButton, mEqualTabWidth);
    }

    public boolean isTabBarVisible() {
        return mTabBarVisible;
    }

    public boolean isTabBarAtBottom() {
        return mTabBarAtBottom;
    }

    public boolean isShowTabCloseButton() {
        return mShowTabCloseButton;
    }

    public boolean isEqualTabWidth() {
        return mEqualTabWidth;
    }
}
