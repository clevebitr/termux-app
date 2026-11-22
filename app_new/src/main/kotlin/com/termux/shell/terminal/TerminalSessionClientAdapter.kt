package com.termux.shell.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.termux.shell.log.ShellLog
import com.termux.shell.terminal.TerminalSession
import com.termux.shell.terminal.TerminalSessionClient
import com.termux.shell.termview.TerminalView

/**
 * 会话客户端适配器。
 *
 * 目的：把 `terminal-core` 的 [TerminalSessionClient] 回调转接到当前挂载的终端视图与外壳状态，
 * 使会话宿主不需要了解视图细节。
 *
 * 行为：
 * - 文本变化时通知已挂载视图重绘；标题、进程号与会话结束通过回调交给宿主；
 * - 复制请求写入系统剪贴板；为安全起见不实现 OSC 52 读取（返回 null）；
 * - 日志统一走 [ShellLog]，便于按 `Shell/` 前缀过滤。
 *
 * 约束：回调均在主线程触发（由 [TerminalSession] 保证）；[viewProvider] 返回已销毁视图时
 * 必须安全忽略，不得持有视图强引用。
 */
class TerminalSessionClientAdapter(
    private val context: Context?,
    private val viewProvider: () -> TerminalView?,
    private val onTitleChanged: () -> Unit,
    private val onSessionFinished: (Int) -> Unit,
    private val onPidChanged: (Int) -> Unit,
) : TerminalSessionClient {

    private val tag: String = "TerminalSession"

    override fun onTextChanged(changedSession: TerminalSession) {
        viewProvider()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitleChanged()
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        onSessionFinished(finishedSession.exitStatus)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    /** 出于安全考虑不向程序暴露剪贴板内容，OSC 52 读取请求被忽略。 */
    override fun getClipboardText(): String? = null

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        // 粘贴由终端视图与系统输入法处理，这里不代劳。
    }

    override fun onBell(session: TerminalSession) {
        // 提示音交给界面层决定，骨架阶段不发声。
    }

    override fun onColorsChanged(session: TerminalSession) {
        viewProvider()?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // 光标闪烁由终端视图自身管理。
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        onPidChanged(pid)
    }

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) {
        ShellLog.warn(this.tag, "[$tag] $message")
    }

    override fun logWarn(tag: String, message: String) {
        ShellLog.warn(this.tag, "[$tag] $message")
    }

    override fun logInfo(tag: String, message: String) {
        ShellLog.info(this.tag, "[$tag] $message")
    }

    override fun logDebug(tag: String, message: String) {
        ShellLog.debug(this.tag, "[$tag] $message")
    }

    override fun logVerbose(tag: String, message: String) {
        ShellLog.debug(this.tag, "[$tag] $message")
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        ShellLog.warn(this.tag, "[$tag] $message", e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        ShellLog.warn(this.tag, "[$tag] ${e.message}", e)
    }
}
