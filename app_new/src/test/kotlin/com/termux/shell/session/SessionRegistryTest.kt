package com.termux.shell.session

import com.termux.shell.pane.SplitOrientation
import com.termux.shell.tabs.TabWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SessionRegistry] 的派生、查询与记录更新测试。 */
class SessionRegistryTest {

    private fun boundWorkspace(sessionId: String): TabWorkspace =
        TabWorkspace.initial("Shell 1").updateActiveTab { tab ->
            val leaf = tab.leaves.first()
            tab.copy(root = com.termux.shell.pane.PaneLeaf(leaf.id, sessionId = sessionId))
        }

    private fun workspaceWithTwoTabs(): TabWorkspace {
        val first = TabWorkspace.initial("Shell 1")
        val second = first.newTab("Shell 2")
            ?: error("第二个标签创建失败")
        return second
    }

    @Test
    fun derivesOnePendingRecordPerLeaf() {
        val workspace = workspaceWithTwoTabs()
        val registry = SessionRegistry.fromWorkspace(workspace)

        assertEquals(2, registry.size)
        assertTrue(registry.records.all { it.state == SessionState.PENDING })
        assertTrue(registry.records.all { it.isAttached })
        assertEquals(listOf("tab-1", "tab-2"), registry.records.map { it.tabId })
        assertTrue(registry.records.all { it.id.startsWith(SessionRegistry.PENDING_PREFIX) })
        assertTrue(registry.detached().isEmpty())
    }

    @Test
    fun boundSessionBecomesRunningRecord() {
        val workspace = TabWorkspace.initial("Shell 1").let { initial ->
            initial.updateActiveTab { tab ->
                val leaf = tab.leaves.first()
                tab.copy(root = com.termux.shell.pane.PaneLeaf(leaf.id, sessionId = "session-a"))
            }
        }
        val registry = SessionRegistry.fromWorkspace(workspace)

        val record = registry.records.single()
        assertEquals("session-a", record.id)
        assertEquals(SessionState.RUNNING, record.state)
        assertFalse(record.isPending)
        assertEquals(record, registry.find("session-a"))
        assertNull(registry.find("missing"))
    }

    @Test
    fun descriptorLookupFillsTitleAndRunningState() {
        val workspace = boundWorkspace("session-a")
        val registry = SessionRegistry.fromWorkspace(workspace) { id ->
            SessionDescriptor(
                id = SessionId.of(id),
                title = "bash",
                workingDirectory = "/data",
                pid = 42,
                running = true,
            )
        }

        val record = registry.records.single()
        assertEquals("bash", record.title)
        assertEquals("/data", record.workingDirectory)
        assertEquals(SessionState.RUNNING, record.state)
    }

    @Test
    fun missingDescriptorMarksSessionExited() {
        val workspace = boundWorkspace("session-a")
        val registry = SessionRegistry.fromWorkspace(workspace) { null }

        assertEquals(SessionState.EXITED, registry.records.single().state)
    }

    @Test
    fun stoppedDescriptorMarksSessionExited() {
        val workspace = boundWorkspace("session-a")
        val registry = SessionRegistry.fromWorkspace(workspace) { id ->
            SessionDescriptor(SessionId.of(id), "vim", null, 42, running = false)
        }

        val record = registry.records.single()
        assertEquals("vim", record.title)
        assertEquals(SessionState.EXITED, record.state)
    }

    @Test
    fun splitsProduceOrderedRecords() {
        val workspace = TabWorkspace.initial("Shell 1")
        val (split, newLeaf) = workspace.splitFocused(SplitOrientation.VERTICAL)
        val registry = SessionRegistry.fromWorkspace(split)

        assertEquals(2, registry.size)
        assertEquals(newLeaf?.id, registry.records[1].leafId)
        assertEquals(2, registry.byTab().getValue("tab-1").size)
    }

    @Test
    fun upsertReplacesRecordWithSameId() {
        val registry = SessionRegistry.fromWorkspace(TabWorkspace.initial("Shell 1"))
        val original = registry.records.single()
        val detached = original.copy(leafId = null, tabId = null, state = SessionState.RUNNING, title = "bash")

        val updated = registry.upsert(detached)

        assertEquals(1, updated.size)
        assertEquals("bash", updated.find(original.id)?.title)
        assertTrue(updated.attached().isEmpty())
        assertEquals(1, updated.detached().size)
    }

    @Test
    fun replaceIgnoresUnknownRecord() {
        val registry = SessionRegistry.fromWorkspace(TabWorkspace.initial("Shell 1"))
        val stranger = SessionRecord(
            id = "session-x",
            leafId = null,
            tabId = null,
            tabTitle = "",
            title = "x",
            workingDirectory = null,
            state = SessionState.EXITED,
        )

        assertSame(registry, registry.replace(stranger))
        assertEquals(registry.size, registry.remove(stranger.id).size)
    }

    @Test
    fun groupedRecordsKeepTabOrder() {
        val workspace = workspaceWithTwoTabs()
        val registry = SessionRegistry.fromWorkspace(workspace)

        val grouped = registry.byTab()
        assertEquals(listOf("tab-1", "tab-2"), grouped.keys.toList())
        assertTrue(grouped.values.all { it.size == 1 })
        assertTrue(SessionRegistry.empty().byTab().isEmpty())
    }
}
