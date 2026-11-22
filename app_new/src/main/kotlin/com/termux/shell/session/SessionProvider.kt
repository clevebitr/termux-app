package com.termux.shell.session

import com.termux.shell.terminal.TerminalSession
import com.termux.shell.termview.TerminalView

/**
 * 进程内会话宿主的最小扩展契约。
 *
 * 目的：在 [SessionHost] 的通用契约之外，补充「按标识取会话实例」「挂载/解绑视图」两项
 * 只有进程内实现才需要的能力，使窗口状态持有者与终端视图池都依赖同一抽象，并可在单元测试中
 * 用假实现替代真实 PTY。
 *
 * 约束：
 * - 实现方必须在主线程提供视图挂载方法；
 * - 一个会话同一时刻只允许挂载一个视图，重复挂载应替换旧引用。
 */
interface SessionProvider : SessionHost {

    fun terminalSession(id: SessionId): TerminalSession?

    fun attachView(id: SessionId, view: TerminalView): Boolean

    fun detachView(id: SessionId, view: TerminalView)
}
