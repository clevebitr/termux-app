package com.termux.shell.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 会话元数据。
 *
 * <p>目的：为侧栏、页签与状态栏提供展示所需的最小信息集，避免界面直接访问会话内部状态。
 *
 * <p>约束：本类型不可变；状态变化时由 {@link SessionHost} 重新构造并通知观察者。
 */
public final class SessionDescriptor {

    private final SessionId mId;
    private final String mTitle;
    private final String mWorkingDirectory;
    private final int mPid;
    private final boolean mRunning;

    public SessionDescriptor(@NonNull SessionId id, @Nullable String title, @Nullable String workingDirectory,
                             int pid, boolean running) {
        mId = id;
        mTitle = title;
        mWorkingDirectory = workingDirectory;
        mPid = pid;
        mRunning = running;
    }

    @NonNull
    public SessionId getId() {
        return mId;
    }

    /**
     * 返回会话标题。终端程序可通过转义序列设置标题；未设置时返回 null，由界面回退到命令名。
     */
    @Nullable
    public String getTitle() {
        return mTitle;
    }

    @Nullable
    public String getWorkingDirectory() {
        return mWorkingDirectory;
    }

    public int getPid() {
        return mPid;
    }

    public boolean isRunning() {
        return mRunning;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionDescriptor)) {
            return false;
        }
        SessionDescriptor that = (SessionDescriptor) other;
        return mPid == that.mPid
            && mRunning == that.mRunning
            && mId.equals(that.mId)
            && Objects.equals(mTitle, that.mTitle)
            && Objects.equals(mWorkingDirectory, that.mWorkingDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle, mWorkingDirectory, mPid, mRunning);
    }
}
