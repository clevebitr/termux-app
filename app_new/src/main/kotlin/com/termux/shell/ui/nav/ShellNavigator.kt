package com.termux.shell.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 外壳导航状态的持有者。
 *
 * 目的：把「当前在哪个层级、有没有覆盖层」从窗口状态持有者中分离，
 * 使页面显隐与返回键分派只有一处真源，并便于独立测试。
 *
 * 行为：全部改动经 [ShellNavigation] 的纯函数完成，本类型只负责替换快照；
 * [back] 返回是否消费了本次返回键。
 *
 * 约束：只在主线程调用；不持有 Android 上下文与资源。
 */
class ShellNavigator(initial: ShellNavigation = ShellNavigation()) {

    /** 当前导航状态。 */
    var state: ShellNavigation by mutableStateOf(initial)
        private set

    /** 打开命令面板；开关关闭时不生效。 */
    fun openPalette(enabled: Boolean) {
        state = state.openPalette(enabled)
    }

    /** 关闭命令面板。 */
    fun closePalette() {
        state = state.closePalette()
    }

    /** 打开设置页。 */
    fun openSettings() {
        state = state.openSettings()
    }

    /** 关闭设置页。 */
    fun closeSettings() {
        state = state.closeSettings()
    }

    /** 请求打开会话抽屉。 */
    fun openSessionDrawer() {
        state = state.openSessionDrawer()
    }

    /** 请求关闭会话抽屉。 */
    fun closeSessionDrawer() {
        state = state.closeSessionDrawer()
    }

    /** 抽屉已由手势或返回键关闭时回写。 */
    fun onSessionDrawerClosed() {
        state = state.onSessionDrawerClosed()
    }

    /**
     * 返回键处置。
     *
     * @return 是否消费本次返回键；false 表示已无处置对象，由窗口结束活动。
     */
    fun back(): Boolean {
        val next = state.back() ?: return false
        state = next
        return true
    }
}
