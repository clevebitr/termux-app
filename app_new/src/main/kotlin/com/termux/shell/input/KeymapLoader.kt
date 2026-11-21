package com.termux.shell.input

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * 键位表加载器。
 *
 * 目的：把「内置默认键位」「磁盘上的用户键位表」「写入默认模板」三种来源集中在一处，
 * 使窗口控制器只需取用解析完成的 [KeymapTable]。
 *
 * 行为：
 * - 文件不存在时写出带说明的默认模板，并返回内置默认表；
 * - 文件存在时以文件内容为准，内置默认表不参与合并，删除某行即等于取消该绑定；
 * - 文件中全部行均无法解析时回退到内置默认表，避免用户误改导致快捷键整体失效。
 *
 * 约束：[load] 会访问磁盘，须在初始化阶段或后台线程调用。
 */
object KeymapLoader {

    private const val HEADER = "# Termux Shell 键位表\n" +
        "# 语法：修饰键+按键 = 动作，修饰键取 ctrl、alt、shift、meta，可组合。\n" +
        "# 以 # 开头的行与空行被忽略；删除某行即取消该绑定。\n" +
        "# 关闭 shell.properties 中的 keymap-enabled 后本文件整体失效。\n"

    /**
     * 内置默认键位。顺序即模板文件中的顺序，分组通过注释行体现。
     */
    val defaultLines: List<String> = listOf(
        "# 标签页",
        "ctrl+shift+t = ${ShellAction.NEW_TAB}",
        "ctrl+shift+w = ${ShellAction.CLOSE_TAB}",
        "ctrl+tab = ${ShellAction.NEXT_TAB}",
        "ctrl+shift+tab = ${ShellAction.PREVIOUS_TAB}",
        "alt+shift+left = ${ShellAction.MOVE_TAB_LEFT}",
        "alt+shift+right = ${ShellAction.MOVE_TAB_RIGHT}",
        "",
        "# 窗格",
        "alt+shift+d = ${ShellAction.SPLIT_RIGHT}",
        "alt+shift+e = ${ShellAction.SPLIT_DOWN}",
        "alt+shift+w = ${ShellAction.CLOSE_PANE}",
        "alt+right = ${ShellAction.FOCUS_NEXT_PANE}",
        "alt+left = ${ShellAction.FOCUS_PREVIOUS_PANE}",
        "# 方向聚焦使用 Ctrl+Alt+方向键，避开终端中 Alt+方向键的按词移动。",
        "ctrl+alt+left = ${ShellAction.FOCUS_PANE_LEFT}",
        "ctrl+alt+right = ${ShellAction.FOCUS_PANE_RIGHT}",
        "ctrl+alt+up = ${ShellAction.FOCUS_PANE_UP}",
        "ctrl+alt+down = ${ShellAction.FOCUS_PANE_DOWN}",
        "",
        "# 窗口与界面",
        "ctrl+shift+n = ${ShellAction.NEW_WINDOW}",
        "ctrl+shift+p = ${ShellAction.COMMAND_PALETTE}",
        "ctrl+shift+b = ${ShellAction.TOGGLE_TAB_BAR}",
    )

    /**
     * 加载键位表。文件缺失、读取失败或解析结果为空时返回内置默认表。
     */
    fun load(file: File?): KeymapTable {
        if (file == null) {
            return KeymapTable.parse(defaultLines)
        }
        if (!file.isFile) {
            writeDefaultTemplate(file)
            return KeymapTable.parse(defaultLines)
        }
        val lines = readLines(file) ?: return KeymapTable.parse(defaultLines)
        val table = KeymapTable.parse(lines)
        return if (table.isEmpty) KeymapTable.parse(defaultLines) else table
    }

    private fun readLines(file: File): List<String>? {
        val lines = ArrayList<String>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines += line
                    line = reader.readLine()
                }
            }
            return lines
        } catch (e: IOException) {
            return null
        }
    }

    private fun writeDefaultTemplate(file: File) {
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            return
        }
        try {
            OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
                writer.write(HEADER)
                for (line in defaultLines) {
                    writer.write(line)
                    writer.write("\n")
                }
            }
        } catch (e: IOException) {
            // 写入失败时按内置默认表运行；下次启动会重试写入。
        }
    }
}
