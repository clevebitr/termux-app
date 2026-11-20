package com.termux.shell.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.session.SessionId;

/**
 * 叶子窗格。
 *
 * <p>目的：描述一个可显示区域及其承载的会话。一个叶子在界面上对应一个终端视图实例。
 *
 * <p>骨架阶段说明：会话标识可以为空，此时该叶子显示空窗格占位内容；会话创建与视图挂载在后续里程碑完成。
 *
 * <p>约束：本类型非线程安全，仅在主线程或控制器内部使用。
 */
public final class PaneLeaf extends PaneNode {

    private SessionId mSessionId;
    private int mScrollRow;

    public PaneLeaf(@NonNull String id) {
        super(id);
    }

    /**
     * 返回本窗格承载的会话标识；尚未分配会话时返回 null。
     */
    @Nullable
    public SessionId getSessionId() {
        return mSessionId;
    }

    /**
     * 设置本窗格承载的会话标识。传入 null 表示解除绑定，用于会话结束后的占位状态。
     */
    public void setSessionId(@Nullable SessionId sessionId) {
        mSessionId = sessionId;
    }

    public boolean hasSession() {
        return mSessionId != null;
    }

    public int getScrollRow() {
        return mScrollRow;
    }

    public void setScrollRow(int scrollRow) {
        mScrollRow = Math.max(scrollRow, 0);
    }
}
