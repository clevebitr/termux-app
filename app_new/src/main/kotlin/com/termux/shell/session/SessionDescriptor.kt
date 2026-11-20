package com.termux.shell.session

/**
 * 会话元数据。
 *
 * 目的：为侧栏、标签与状态栏提供展示所需的最小信息集，避免界面直接访问会话内部状态。
 *
 * 约束：本类型不可变；状态变化时由 [SessionHost] 重新构造并通知观察者。
 */
data class SessionDescriptor(
    val id: SessionId,
    val title: String?,
    val workingDirectory: String?,
    val pid: Int,
    val running: Boolean,
)
