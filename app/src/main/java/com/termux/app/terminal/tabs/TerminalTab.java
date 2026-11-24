package com.termux.app.terminal.tabs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

/**
 * 目的 — 表示一个标签页：一组窗格会话与其中的活动窗格，供标签栏、抽屉与终端活动共享同一份状态。
 *
 * <p>行为 — 持有每个窗格索引对应的会话引用、活动窗格索引与可选的自定义标题。标题优先取自定义标题，其次取活动
 * 会话的标题，两者都没有时使用调用方给出的默认标题。越界的窗格索引被忽略，查询返回 {@code null}。</p>
 *
 * <p>约束 — 只描述状态，不创建会话、不绑定视图、不处理生命周期。会话的 attach/detach 与结束仍由活动与会话客户端
 * 负责，本类不持有任何 Android 资源。</p>
 */
public final class TerminalTab {

    /** The number of panes a tab can hold, which matches the two panes of the terminal layout. */
    public static final int MAX_PANES = 2;

    private final int mId;

    @NonNull
    private final TerminalSession[] mSessions = new TerminalSession[MAX_PANES];

    private int mActivePaneIndex;

    @Nullable
    private String mCustomTitle;

    public TerminalTab(int id) {
        mId = id;
    }

    /** @return The stable id of the tab, which the tab bar uses to recognize its views across rebuilds. */
    public int getId() {
        return mId;
    }

    /** @return The number of panes the tab can hold, which is fixed for the current layout. */
    public int getPaneCount() {
        return MAX_PANES;
    }

    /** @return The session shown in the pane at {@code paneIndex}, or {@code null} if the pane is empty. */
    @Nullable
    public TerminalSession getSession(int paneIndex) {
        return isValidPaneIndex(paneIndex) ? mSessions[paneIndex] : null;
    }

    /** Set the session of the pane at {@code paneIndex}, which may be {@code null} to empty the pane. */
    public void setSession(int paneIndex, @Nullable TerminalSession session) {
        if (!isValidPaneIndex(paneIndex)) return;
        mSessions[paneIndex] = session;
    }

    /** @return Whether any pane of the tab shows {@code session}, which is never {@code true} for {@code null}. */
    public boolean hasSession(@Nullable TerminalSession session) {
        if (session == null) return false;

        for (TerminalSession paneSession : mSessions) {
            if (paneSession == session) return true;
        }
        return false;
    }

    /** @return The session of the active pane, or {@code null} if the tab has no session in it. */
    @Nullable
    public TerminalSession getActiveSession() {
        return getSession(mActivePaneIndex);
    }

    /** @return The index of the pane that receives the input when the tab is active. */
    public int getActivePaneIndex() {
        return mActivePaneIndex;
    }

    /** Set the active pane, which is ignored when {@code paneIndex} is out of range. */
    public void setActivePaneIndex(int paneIndex) {
        if (!isValidPaneIndex(paneIndex)) return;
        mActivePaneIndex = paneIndex;
    }

    /** @return The title the user gave the tab, or {@code null} if the tab uses the title of its session. */
    @Nullable
    public String getCustomTitle() {
        return mCustomTitle;
    }

    /** Set a custom title, or {@code null} to fall back to the title of the session again. */
    public void setCustomTitle(@Nullable String title) {
        mCustomTitle = title == null || title.trim().isEmpty() ? null : title.trim();
    }

    /**
     * @param defaultTitle The title to use when neither a custom title nor a session title is available.
     * @return The title to show in the tab bar.
     */
    @NonNull
    public String getTitle(@NonNull String defaultTitle) {
        if (mCustomTitle != null) return mCustomTitle;

        TerminalSession session = getActiveSession();
        if (session != null) {
            String title = session.getTitle();
            if (title != null && !title.trim().isEmpty()) return title.trim();
        }

        return defaultTitle;
    }

    private static boolean isValidPaneIndex(int paneIndex) {
        return paneIndex >= 0 && paneIndex < MAX_PANES;
    }
}
