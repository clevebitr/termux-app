package com.termux.shell.commands

/**
 * 命令面板中的一条命令。
 *
 * 目的：把「展示信息」与「执行动作」组合为一个不可变对象，使面板只负责筛选、排序与渲染，
 * 不持有窗口状态。
 *
 * 约束：[action] 在面板关闭后仍可能被调用，因此必须能够重复执行，且不得持有已销毁的视图。
 */
class ShellCommand(
    val id: String,
    val title: String,
    val category: String,
    val shortcut: String?,
    val enabled: Boolean,
    private val action: () -> Unit,
) {

    /** 执行命令。调用方负责线程与生命周期约束：仅允许在界面存活的条件下调用。 */
    fun execute() {
        action()
    }
}
