package com.termux.shell.log

import android.util.Log

/**
 * 新外壳的统一日志出口。
 *
 * 目的：在骨架阶段即固定日志标签格式，避免各包自行拼接标签导致过滤困难。
 * 标签格式为 `Shell/<子标签>`，子标签通常取调用方类型名。
 *
 * 约束：仅用于诊断信息，不得输出会话内容、剪贴板数据等敏感信息。
 */
object ShellLog {

    private const val TAG_PREFIX = "Shell/"

    fun debug(subTag: String, message: String) {
        Log.d(TAG_PREFIX + subTag, message)
    }

    fun info(subTag: String, message: String) {
        Log.i(TAG_PREFIX + subTag, message)
    }

    fun warn(subTag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(TAG_PREFIX + subTag, message)
        } else {
            Log.w(TAG_PREFIX + subTag, message, throwable)
        }
    }
}
