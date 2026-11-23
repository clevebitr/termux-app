package com.termux.app.terminal.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * 目的 — {@link TerminalPaneHost} 的当前实现：两个窗格，可上下或左右排列，与 {@code ext.split} 的分屏一致。
 *
 * <p>行为 — 持有两个窗格的视图、视图客户端与活动窗格索引，活动窗格默认为第一个。窗格索引越界时返回 {@code null}
 * 而不是抛出异常，使调用方在视图尚未创建时可以安全查询。</p>
 *
 * <p>约束 — 只保存引用，不创建视图、不绑定会话；索引必须落在 {@code [0, getPaneCount())} 内，否则忽略。</p>
 */
public final class TwoPaneTerminalLayout implements TerminalPaneHost {

    private static final int PANE_COUNT = 2;

    @NonNull
    private final TerminalView[] mViews = new TerminalView[PANE_COUNT];
    @NonNull
    private final TermuxTerminalViewClient[] mViewClients = new TermuxTerminalViewClient[PANE_COUNT];

    private int mActiveIndex;

    @Override
    public int getPaneCount() {
        return PANE_COUNT;
    }

    @NonNull
    @Override
    public TerminalView[] getViews() {
        return mViews;
    }

    @Override
    public void setView(int index, @Nullable TerminalView view) {
        if (!isValidIndex(index)) return;
        mViews[index] = view;
    }

    @Nullable
    @Override
    public TerminalView getView(int index) {
        return isValidIndex(index) ? mViews[index] : null;
    }

    @Override
    public void setViewClient(int index, @Nullable TermuxTerminalViewClient viewClient) {
        if (!isValidIndex(index)) return;
        mViewClients[index] = viewClient;
    }

    @Nullable
    @Override
    public TermuxTerminalViewClient getViewClient(int index) {
        return isValidIndex(index) ? mViewClients[index] : null;
    }

    @NonNull
    @Override
    public TermuxTerminalViewClient[] getViewClients() {
        return mViewClients;
    }

    @Override
    public int getActiveIndex() {
        return mActiveIndex;
    }

    @Override
    public void setActiveIndex(int index) {
        if (!isValidIndex(index)) return;
        mActiveIndex = index;
    }

    @Override
    public int getInactiveIndex() {
        return mActiveIndex == 0 ? 1 : 0;
    }

    @Nullable
    @Override
    public TerminalView getInactiveView() {
        return getView(getInactiveIndex());
    }

    @Nullable
    @Override
    public TerminalSession getSession(int index) {
        TerminalView view = getView(index);
        return view != null ? view.getCurrentSession() : null;
    }

    @Nullable
    @Override
    public TerminalView findViewForSession(@Nullable TerminalSession session) {
        if (session == null) return null;

        for (TerminalView view : mViews) {
            if (view != null && view.getCurrentSession() == session) return view;
        }
        return null;
    }

    private static boolean isValidIndex(int index) {
        return index >= 0 && index < PANE_COUNT;
    }
}
