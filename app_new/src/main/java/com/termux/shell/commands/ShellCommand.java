package com.termux.shell.commands;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 命令面板中的一条命令。
 *
 * <p>目的：把「展示信息」与「执行动作」组合为一个不可变对象，使面板只负责筛选、排序与渲染，
 * 不持有窗口状态。
 *
 * <p>约束：{@link #getAction()} 在面板关闭后仍可能被调用（例如命令执行会重新打开面板），
 * 因此动作必须能够重复执行，且不得持有已销毁的视图。
 */
public final class ShellCommand {

    private final String mId;
    private final String mTitle;
    private final String mCategory;
    private final String mShortcut;
    private final boolean mEnabled;
    private final Runnable mAction;

    /**
     * @param id       命令标识，通常与外壳动作名称一致。
     * @param title    展示标题。
     * @param category 分类名称，用于筛选匹配与视觉分组。
     * @param shortcut 快捷键展示文本；无绑定时为 null。
     * @param enabled  是否可执行。不可执行的命令仍然展示，以便用户了解当前状态。
     * @param action   执行动作。
     */
    public ShellCommand(@NonNull String id, @NonNull String title, @NonNull String category,
                        @Nullable String shortcut, boolean enabled, @NonNull Runnable action) {
        mId = id;
        mTitle = title;
        mCategory = category;
        mShortcut = shortcut;
        mEnabled = enabled;
        mAction = action;
    }

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    @NonNull
    public String getCategory() {
        return mCategory;
    }

    @Nullable
    public String getShortcut() {
        return mShortcut;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * 执行命令。调用方负责线程与生命周期约束：仅允许在界面存活的条件下调用。
     */
    public void execute() {
        mAction.run();
    }
}
