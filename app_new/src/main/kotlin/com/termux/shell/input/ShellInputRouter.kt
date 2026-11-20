package com.termux.shell.input

import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * 外壳输入路由默认实现。
 *
 * 目的：在按键进入终端键序编码之前，先判定外壳级快捷键；未命中的按键原样交回窗口继续分发。
 *
 * 行为：
 * - 仅处理 `ACTION_DOWN`；按键抬起不参与快捷键判定；
 * - 键位表为空、快捷键被禁用或按键未绑定时返回 false，由窗口继续分发；
 * - 命中绑定时调用 [Host.execute]，无论该动作是否可执行都视为已消费，以免同一按键同时触发终端输入。
 *
 * 约束：拖放与指针事件的完整语义在输入里程碑实现，本实现仅返回未消费。
 */
class ShellInputRouter : InputRouter {

    /** 动作宿主。返回值表示动作是否实际生效，仅用于日志；路由结果由绑定是否命中决定。 */
    fun interface Host {
        fun execute(action: String): Boolean
    }

    var keymap: KeymapTable = KeymapTable.EMPTY

    /**
     * 外壳快捷键开关。关闭后全部按键交给终端处理，与旧模块的
     * `disable-hardware-keyboard-shortcuts` 语义一致。
     */
    var shellShortcutsEnabled: Boolean = true

    var host: Host? = null

    override fun onKeyDown(event: KeyEvent): Boolean {
        if (!shellShortcutsEnabled) {
            return false
        }
        val actionHost = host ?: return false
        if (keymap.isEmpty) {
            return false
        }
        val action = keymap.findAction(event.keyCode, event.metaState) ?: return false
        actionHost.execute(action)
        return true
    }

    override fun onKeyUp(event: KeyEvent): Boolean = false

    override fun onGenericMotion(event: MotionEvent): Boolean = false

    override fun onDrop(event: DragEvent): Boolean = false
}
