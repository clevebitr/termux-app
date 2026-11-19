package com.termux.shell.log;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 新外壳的统一日志出口。
 *
 * <p>目的：在骨架阶段即固定日志标签格式，避免各包自行拼接标签导致过滤困难。
 * 标签格式为 {@code Shell/<子标签>}，子标签通常取调用方的类名简称。
 *
 * <p>约束：仅用于诊断信息，不得在其中输出会话内容、剪贴板数据等敏感信息。
 */
public final class ShellLog {

    private static final String TAG_PREFIX = "Shell/";

    private ShellLog() {
    }

    public static void debug(@NonNull String subTag, @NonNull String message) {
        Log.d(TAG_PREFIX + subTag, message);
    }

    public static void info(@NonNull String subTag, @NonNull String message) {
        Log.i(TAG_PREFIX + subTag, message);
    }

    public static void warn(@NonNull String subTag, @NonNull String message, @Nullable Throwable throwable) {
        if (throwable == null) {
            Log.w(TAG_PREFIX + subTag, message);
        } else {
            Log.w(TAG_PREFIX + subTag, message, throwable);
        }
    }
}
