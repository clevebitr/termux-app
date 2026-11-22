package com.termux.shell.terminal

import android.content.Context
import android.util.TypedValue
import com.termux.shell.log.ShellLog
import com.termux.shell.session.SessionProvider
import com.termux.shell.session.SessionId
import com.termux.shell.termview.TerminalView

/**
 * 终端视图池。
 *
 * 目的：按会话标识复用 [TerminalView] 实例，使切换标签或窗格焦点时视图不被重建，
 * 从而保留渲染器状态、滚动位置与文本选择；同时保证一个会话只挂载一个视图。
 *
 * 行为：
 * - [obtain] 首次创建视图并附加会话（首次附加会按实际尺寸启动 PTY），之后返回同一实例；
 * - [retain] 在标签或窗格关闭后释放不再需要的视图，并解除会话挂载；
 * - 视图获得焦点时回调 [onFocus]，用于同步外壳的焦点窗格。
 *
 * 约束：
 * - 必须在主线程调用；池的生命周期与窗口一致，窗口重建后视图重建但会话保留；
 * - 创建或附加失败时返回 null 并记录日志，由界面回退到占位内容，不得让异常冒泡到组合过程。
 */
class TerminalViewPool(
    private val context: Context,
    private val host: SessionProvider,
    private val onFocus: (String) -> Unit,
) {

    private val views = LinkedHashMap<String, TerminalView>()

    /**
     * 取回会话对应的终端视图。
     *
     * @return 视图实例；会话不存在或创建失败时返回 null。
     */
    fun obtain(sessionId: String): TerminalView? {
        views[sessionId]?.let { return it }
        val id = SessionId.of(sessionId)
        val session = host.terminalSession(id) ?: return null
        return try {
            val view = TerminalView(context, null)
            // TerminalView 自身不设置可聚焦，必须显式开启触摸模式焦点，否则点击无法调起输入法。
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setTerminalViewClient(TerminalViewClientAdapter(context) { views[sessionId] })
            view.setTextSize(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    DEFAULT_TEXT_SIZE_SP,
                    context.resources.displayMetrics,
                ).toInt(),
            )
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onFocus(sessionId)
                }
            }
            host.attachView(id, view)
            if (!view.attachSession(session)) {
                host.detachView(id, view)
                return null
            }
            views[sessionId] = view
            view
        } catch (e: Throwable) {
            ShellLog.warn(SUB_TAG, "终端视图创建失败：$sessionId", e)
            null
        }
    }

    /** 释放不在 [aliveSessionIds] 中的视图。 */
    fun retain(aliveSessionIds: Set<String>) {
        val iterator = views.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in aliveSessionIds) {
                host.detachView(SessionId.of(entry.key), entry.value)
                iterator.remove()
            }
        }
    }

    /** 释放全部视图。窗口销毁时调用。 */
    fun clear() {
        for ((sessionId, view) in views) {
            host.detachView(SessionId.of(sessionId), view)
        }
        views.clear()
    }

    private companion object {
        const val SUB_TAG = "TerminalViewPool"
        const val DEFAULT_TEXT_SIZE_SP = 13f
    }
}
