package com.termux.shell.config

import com.termux.shell.pane.SplitOrientation
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.tabs.TabWorkspaceCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [FileLayoutStore] 的保存、恢复与损坏回退测试。 */
class FileLayoutStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(): Pair<FileLayoutStore, File> {
        val file = File(File(temporaryFolder.root, "layout"), "window-layout.json")
        return FileLayoutStore(file) to file
    }

    @Test
    fun restoresTabsAndSplitStructure() {
        val (layoutStore, _) = store()
        val workspace = TabWorkspace.initial("one").let { initial ->
            val split = initial.splitFocused(SplitOrientation.VERTICAL).first
            split.newTab("two") ?: error("第二个标签创建失败")
        }

        assertTrue(layoutStore.write(workspace))

        val restored = layoutStore.read()
        assertEquals(TabWorkspaceCodec.encode(workspace), TabWorkspaceCodec.encode(restored!!))
    }

    @Test
    fun missingFileReturnsNull() {
        val (layoutStore, _) = store()

        assertNull(layoutStore.read())
    }

    @Test
    fun corruptedFileReturnsNull() {
        val (layoutStore, file) = store()
        file.parentFile.mkdirs()
        file.writeText("{not json")

        assertNull(layoutStore.read())
    }

    @Test
    fun unsupportedVersionReturnsNull() {
        val (layoutStore, file) = store()
        file.parentFile.mkdirs()
        file.writeText("""{"version":99,"activeTab":0,"tabs":[]}""")

        assertNull(layoutStore.read())
    }

    @Test
    fun rewriteReplacesPreviousLayout() {
        val (layoutStore, _) = store()
        val first = TabWorkspace.initial("one")
        val second = first.newTab("two") ?: error("第二个标签创建失败")

        assertTrue(layoutStore.write(first))
        assertTrue(layoutStore.write(second))

        assertEquals(2, layoutStore.read()?.size)
    }

    @Test
    fun writeFailsWhenTargetIsOccupiedByDirectory() {
        val file = File(File(temporaryFolder.root, "layout"), "window-layout.json")
        file.mkdirs()

        assertFalse(FileLayoutStore(file).write(TabWorkspace.initial("one")))
    }
}
