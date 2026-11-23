package com.termux.shell.config

import com.termux.shell.tabs.TabWorkspace

/**
 * 窗口布局的持久化契约。
 *
 * 目的：把「标签与窗格结构在哪里、以什么格式保存」与窗口生命周期解耦，使窗口在进程被回收后
 * 仍能恢复上次的结构，同时让状态持有者与单元测试不直接接触文件系统。
 *
 * 行为：
 * - [read] 返回上次保存的工作区；文件缺失、内容损坏或格式版本不匹配时返回 null，
 *   由调用方回退到单标签初始布局；
 * - [write] 以原子替换写入当前工作区，失败时磁盘上的旧内容保持不变。
 *
 * 约束：两个方法都会访问磁盘，必须在后台线程或启动阶段调用；不抛出异常，失败通过返回值表达。
 */
interface LayoutStore {

    /** 读取上次保存的工作区；不可用时返回 null。 */
    fun read(): TabWorkspace?

    /** 保存工作区。 */
    fun write(workspace: TabWorkspace): Boolean
}
