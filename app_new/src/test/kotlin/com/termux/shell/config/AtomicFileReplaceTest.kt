package com.termux.shell.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [AtomicFileReplace] 的写入、替换与失败保护测试。 */
class AtomicFileReplaceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesNewFileWithoutLeavingTemporaryFile() {
        val target = File(temporaryFolder.root, "shell.properties")

        assertTrue(AtomicFileReplace.writeText(target, "theme=dark\n"))

        assertEquals("theme=dark\n", target.readText())
        assertFalse(File(temporaryFolder.root, "shell.properties.tmp").exists())
    }

    @Test
    fun replacesExistingContent() {
        val target = File(temporaryFolder.root, "shell.properties")
        target.writeText("theme=light\nlayout-mode=auto\n")

        assertTrue(AtomicFileReplace.writeText(target, "theme=dark\n"))

        // 旧内容不得残留：重命名替换是整文件覆盖，而不是在原文件后追加。
        assertEquals("theme=dark\n", target.readText())
        assertFalse(File(temporaryFolder.root, "shell.properties.tmp").exists())
    }

    @Test
    fun createsMissingParentDirectory() {
        val target = File(File(temporaryFolder.root, "home/.termux"), "shell.properties")

        assertTrue(AtomicFileReplace.writeText(target, "fullscreen=false\n"))

        assertEquals("fullscreen=false\n", target.readText())
    }

    @Test
    fun reportsFailureAndKeepsOldContentWhenTargetIsDirectory() {
        val target = File(temporaryFolder.root, "occupied")
        target.mkdirs()

        assertFalse(AtomicFileReplace.writeText(target, "anything"))

        // 目标位置被目录占用时保留原状，且不留下临时文件。
        assertTrue(target.isDirectory)
        assertFalse(File(temporaryFolder.root, "occupied.tmp").exists())
    }
}
