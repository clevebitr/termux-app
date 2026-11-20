package com.termux.shell.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TabWorkspace] 的标签顺序、当前标签与上限行为测试。 */
class TabWorkspaceTest {

    @Test
    fun newTabAppendsAndSelectsNewTab() {
        val workspace = TabWorkspace.initial("one")
        assertEquals("tab-1", workspace.activeTab.id)
        assertEquals(1, workspace.size)

        val next = workspace.newTab("two")
        assertNotNull(next)
        assertEquals(2, next!!.size)
        assertEquals(1, next.activeIndex)
        assertEquals("tab-2", next.activeTab.id)

        // 原实例保持不变，说明状态确实不可变。
        assertEquals(1, workspace.size)
    }

    @Test
    fun closingTabBeforeActiveKeepsSameActiveTab() {
        val workspace = TabWorkspace.initial("one").newTab("two")!!.newTab("three")!!
            .select(1)!!

        val closed = workspace.closeTab(0)
        assertNotNull(closed)
        assertEquals("two", closed!!.activeTab.title)
        assertEquals(0, closed.activeIndex)
    }

    @Test
    fun closingActiveTabSelectsRightNeighbourThenLeft() {
        val workspace = TabWorkspace.initial("one").newTab("two")!!.select(0)!!

        val closed = workspace.closeTab(0)
        assertNotNull(closed)
        assertEquals("two", closed!!.activeTab.title)

        // 仅剩一个标签，关闭请求被拒绝。
        assertNull(closed.closeTab(0))
        assertEquals(1, closed.size)
    }

    @Test
    fun closingActiveLastTabSelectsLeftNeighbour() {
        val workspace = TabWorkspace.initial("one").newTab("two")!!
        val closed = workspace.closeTab(1)
        assertNotNull(closed)
        assertEquals("one", closed!!.activeTab.title)
        assertEquals(0, closed.activeIndex)
    }

    @Test
    fun moveKeepsActiveTabSelected() {
        val workspace = TabWorkspace.initial("one").newTab("two")!!.newTab("three")!!

        val moved = workspace.moveTab(2, 0)
        assertNotNull(moved)
        assertEquals("three", moved!!.tabs[0].title)
        assertEquals(0, moved.activeIndex)
        assertEquals("three", moved.activeTab.title)
    }

    @Test
    fun selectRelativeWrapsAround() {
        val workspace = TabWorkspace.initial("one").newTab("two")!!.newTab("three")!!
        assertEquals(2, workspace.activeIndex)

        val forward = workspace.selectRelative(1)
        assertEquals(0, forward!!.activeIndex)
        val backward = forward.selectRelative(-1)
        assertEquals(2, backward!!.activeIndex)
    }

    @Test
    fun tabCountIsCapped() {
        var workspace = TabWorkspace.initial("tab0")
        for (index in 1 until TabWorkspace.MAX_TAB_COUNT) {
            workspace = workspace.newTab("tab$index")!!
        }
        assertEquals(TabWorkspace.MAX_TAB_COUNT, workspace.size)
        assertNull(workspace.newTab("overflow"))
    }

    @Test
    fun restoreResumesIdentifierNumbering() {
        val source = TabWorkspace.initial("one").newTab("two")!!
        val restored = TabWorkspace.restore(source.tabs, 1)

        assertEquals(2, restored.size)
        assertEquals(1, restored.activeIndex)

        val appended = restored.newTab("three")
        assertNotNull(appended)
        assertEquals("tab-3", appended!!.activeTab.id)
    }

    @Test
    fun restoreClampsActiveIndex() {
        val source = TabWorkspace.initial("one")
        val restored = TabWorkspace.restore(source.tabs, 9)
        assertEquals(0, restored.activeIndex)
    }

    @Test
    fun splitFocusedAddsLeafAndMovesFocus() {
        val workspace = TabWorkspace.initial("one")
        val (split, newLeaf) = workspace.splitFocused(com.termux.shell.pane.SplitOrientation.HORIZONTAL)

        assertNotNull(newLeaf)
        assertEquals(2, split.activeTab.leaves.size)
        assertEquals(newLeaf!!.id, split.activeTab.focusedLeaf.id)
    }

    @Test
    fun splitStopsAtLeafLimit() {
        var workspace = TabWorkspace.initial("one")
        repeat(TabWorkspace.MAX_LEAF_COUNT - 1) {
            workspace = workspace.splitFocused(com.termux.shell.pane.SplitOrientation.VERTICAL).first
        }
        assertEquals(TabWorkspace.MAX_LEAF_COUNT, workspace.activeTab.leaves.size)
        val (unchanged, leaf) = workspace.splitFocused(com.termux.shell.pane.SplitOrientation.VERTICAL)
        assertNull(leaf)
        assertEquals(TabWorkspace.MAX_LEAF_COUNT, unchanged.activeTab.leaves.size)
    }

    @Test
    fun closeFocusedPanePromotesSibling() {
        val workspace = TabWorkspace.initial("one")
        val (split, newLeaf) = workspace.splitFocused(com.termux.shell.pane.SplitOrientation.HORIZONTAL)
        assertNotNull(newLeaf)

        val closed = split.closeFocusedPane()
        assertNotNull(closed)
        assertEquals(1, closed!!.activeTab.leaves.size)
        assertEquals("pane-1", closed.activeTab.focusedLeaf.id)

        assertNull(closed.closeFocusedPane())
    }

    @Test
    fun focusRelativeCyclesLeaves() {
        val (split, _) = TabWorkspace.initial("one")
            .splitFocused(com.termux.shell.pane.SplitOrientation.HORIZONTAL)
        val focused = split.activeTab.focusedLeaf.id

        val next = split.focusRelative(1)
        assertNotNull(next)
        assertTrue(next!!.activeTab.focusedLeaf.id != focused)
        val back = next.focusRelative(-1)
        assertEquals(focused, back!!.activeTab.focusedLeaf.id)
    }
}
