package com.termux.shell.config

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 文件内容的原子替换。
 *
 * 目的：把「临时文件 + 重命名」这一写盘模式集中到一处，避免设置文件与会话配置各写一遍，
 * 并保证写入过程中断时磁盘上始终存在一份完整内容。
 *
 * 行为：
 * - 先写入同目录的临时文件并调用 `FileDescriptor.sync()` 落盘；
 * - 再用 [File.renameTo] 覆盖目标文件。Android 与 Linux 的 `rename(2)` 是原子替换，
 *   因此不存在「目标文件已被删除、新文件尚未就位」的窗口；
 * - 任一步失败时删除临时文件并返回 false，目标文件保持原内容（首次创建时保持不存在）。
 *
 * 约束：本类型只做文件操作，不抛异常、不记录日志、不依赖 Android 运行时；
 * 同一目标文件的并发写入需要调用方自行串行化。
 */
internal object AtomicFileReplace {

    /**
     * 以 [content] 原子替换 [target]。
     *
     * @return 是否替换成功。失败时目标文件不变。
     */
    fun write(target: File, content: ByteArray): Boolean {
        val parent = target.absoluteFile.parentFile ?: return false
        if (!parent.isDirectory && !parent.mkdirs()) {
            return false
        }
        // 临时文件必须与目标同目录，跨目录重命名会退化为复制并失去原子性。
        val temp = File(parent, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(content)
                output.flush()
                output.fd.sync()
            }
        } catch (e: IOException) {
            temp.delete()
            return false
        }
        if (temp.renameTo(target)) {
            return true
        }
        // 目标文件已存在时部分文件系统不允许重命名覆盖，此时保留原文件并放弃本次写入。
        temp.delete()
        return false
    }

    /** 以 UTF-8 文本原子替换 [target]。 */
    fun writeText(target: File, text: String): Boolean =
        write(target, text.toByteArray(StandardCharsets.UTF_8))
}
