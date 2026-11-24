package com.termux.app.terminal.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * 目的 — 把「哪个窗格显示哪个会话、哪个窗格是活动窗格」从 {@code TermuxActivity} 中抽出来，使窗格数量与排列方式
 * 成为可替换的实现，而不是散布在活动里的两个数组。
 *
 * <p>行为 — 提供窗格数量、每个窗格的视图与视图客户端、活动窗格与下一个窗格的索引，以及按会话查找窗格的方法。
 * 实现负责维护「活动窗格」这一状态；调用方不直接读写数组与索引。</p>
 *
 * <p>约束 — 只描述布局，不创建视图、不绑定会话、不处理输入法。会话的 attach/detach 与视图的生命周期仍由活动负责，
 * 因为二者都与 {@code TermuxService} 和活动生命周期耦合。</p>
 */
public interface TerminalPaneHost {

    /** @return The number of panes that the layout can show, which is fixed per implementation. */
    int getPaneCount();

    /**
     * @return The views of all panes, in pane order, for the callers that iterate them. The array belongs to the
     *         implementation and must only be read.
     */
    @NonNull
    TerminalView[] getViews();

    /**
     * Set the view of the pane at {@code index}, which the activity calls after it created the view, since the
     * activity owns the view hierarchy.
     */
    void setView(int index, @Nullable TerminalView view);

    /** @return The view of the pane at {@code index}, or {@code null} if it was not created yet. */
    @Nullable
    TerminalView getView(int index);

    /**
     * Set the view client of the pane at {@code index}, which the activity calls after it created the client, since
     * the client depends on the activity.
     */
    void setViewClient(int index, @Nullable TermuxTerminalViewClient viewClient);

    /** @return The view client of the pane at {@code index}, or {@code null} if it was not created yet. */
    @Nullable
    TermuxTerminalViewClient getViewClient(int index);

    /**
     * @return The view clients of all panes, in pane order, for the callers that iterate them. The array belongs to
     *         the implementation and must only be read.
     */
    @NonNull
    TermuxTerminalViewClient[] getViewClients();

    /** @return The index of the pane that receives the input and the menu actions. */
    int getActiveIndex();

    /**
     * Set the active pane.
     *
     * <p>The caller is responsible for the side effects, like moving the focus, updating the pane borders and
     * refreshing the active session, since only the activity knows about them.</p>
     */
    void setActiveIndex(int index);

    /** @return The index of the pane that is not the active one. */
    int getInactiveIndex();

    /** @return The view of the pane that is not the active one, or {@code null} if it was not created yet. */
    @Nullable
    TerminalView getInactiveView();

    /** @return The session shown in the pane at {@code index}, or {@code null} if it has no session. */
    @Nullable
    TerminalSession getSession(int index);

    /**
     * @return The view that currently shows {@code session}, or {@code null} if no pane shows it.
     */
    @Nullable
    TerminalView findViewForSession(@Nullable TerminalSession session);
}
