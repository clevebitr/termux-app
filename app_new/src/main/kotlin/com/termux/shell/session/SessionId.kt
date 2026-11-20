package com.termux.shell.session

/**
 * 会话标识。
 *
 * 目的：为会话提供跨进程稳定标识。运行时拆分后，界面进程与运行时进程仅通过该标识引用同一会话，
 * 不得传递终端缓冲区等大对象。
 *
 * 约束：标识一经分配不得复用，即使对应会话已经退出。
 */
@JvmInline
value class SessionId(val value: String) {

    init {
        require(value.isNotEmpty()) { "会话标识不得为空字符串" }
    }

    companion object {
        fun of(value: String): SessionId = SessionId(value)
    }
}
