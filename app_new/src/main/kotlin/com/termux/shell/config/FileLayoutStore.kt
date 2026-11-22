package com.termux.shell.config

import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.tabs.TabWorkspaceCodec
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 基于 JSON 文件的窗口布局存储。
 *
 * 目的：把标签、窗格树与当前标签写入 `filesDir/layout/window-layout.json`，使进程被回收后
 * 重新打开的窗口恢复上次结构，而不是回到单标签空白布局。
 *
 * 行为：
 * - 写入复用 [TabWorkspaceCodec] 的带版本文档，并通过 [AtomicFileReplace] 原子替换；
 * - 读取失败、内容损坏或版本不匹配时返回 null，调用方回退默认布局；
 * - 写入失败时保留磁盘上的旧布局。
 *
 * 约束：
 * - 多个窗口共享同一文件，采用「最后写入者生效」，因此恢复结果对应最近退到后台的窗口；
 * - 同一文件的并发写入由调用方串行化（窗口状态持有者只在自己的写协程中调用）。
 */
class FileLayoutStore(private val file: File) : LayoutStore {

    override fun read(): TabWorkspace? {
        if (!file.isFile) {
            return null
        }
        val document = try {
            file.readText(StandardCharsets.UTF_8)
        } catch (e: IOException) {
            return null
        }
        val decoded = TabWorkspaceCodec.decode(document) ?: return null
        return TabWorkspace.restore(decoded.tabs, decoded.activeIndex)
    }

    override fun write(workspace: TabWorkspace): Boolean =
        AtomicFileReplace.writeText(file, TabWorkspaceCodec.encode(workspace))
}
