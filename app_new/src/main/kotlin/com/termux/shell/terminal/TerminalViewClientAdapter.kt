package com.termux.shell.terminal

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.shell.log.ShellLog
import com.termux.shell.terminal.TerminalSession
import com.termux.shell.termview.TerminalView
import com.termux.shell.termview.TerminalViewClient

/**
 * 终端视图客户端适配器。
 *
 * 目的：向 [TerminalView] 提供外壳的输入与交互策略。骨架阶段只实现「可输入」所需的最小集合：
 * 单击唤起输入法、保留回滚位置、不使用会话滑动切换；其余快捷键与鼠标语义留给输入层里程碑。
 *
 * 约束：
 * - [onScale] 当前不改变字号，返回 1 表示不缩放；字号调整在设置页接入后实现；
 * - [onKeyDown]/[onKeyUp] 返回 false，把按键交给终端键序编码；
 * - [onLongPress] 返回 false，让终端视图继续处理文本选择。
 */
class TerminalViewClientAdapter(
    private val context: Context,
    private val viewProvider: () -> TerminalView?,
) : TerminalViewClient {

    private val tag: String = "TerminalView"

    override fun onScale(scale: Float): Float = 1f

    override fun onSingleTapUp(e: MotionEvent) {
        val view = viewProvider() ?: return
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun isGboardCjkWorkaroundEnabled(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun shouldSwitchSessionOnSwipe(): Boolean = false

    override fun onSwitchSessionSwipe(forward: Boolean) = Unit

    override fun shouldTouchDragSendMouseDrag(): Boolean = false

    override fun shouldKeepScrollPositionOnOutput(): Boolean = true

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() = Unit

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
