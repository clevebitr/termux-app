package com.termux.shell.session;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 会话标识。
 *
 * <p>目的：为会话提供跨进程稳定标识。运行时拆分后，界面进程与运行时进程仅通过该标识引用同一会话，
 * 不得传递终端缓冲区等大对象。
 *
 * <p>约束：标识一经分配不得复用，即使对应会话已经退出。
 */
public final class SessionId {

    private final String mValue;

    private SessionId(@NonNull String value) {
        mValue = value;
    }

    @NonNull
    public static SessionId of(@NonNull String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("会话标识不得为空字符串");
        }
        return new SessionId(value);
    }

    @NonNull
    public String getValue() {
        return mValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionId)) {
            return false;
        }
        return mValue.equals(((SessionId) other).mValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mValue);
    }

    @NonNull
    @Override
    public String toString() {
        return mValue;
    }
}
