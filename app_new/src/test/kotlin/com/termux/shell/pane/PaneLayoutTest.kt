package com.termux.shell.pane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PaneLayout] 的结构操作与焦点迁移测试。 */
class PaneLayoutTest {

    @Test
    fun splitCreatesSiblingAndReportsNewLeaf() {
        val root = PaneLeaf("pane-1")
        val result = PaneLayout.split(root, "pane-1", SplitOrientation.HORIZONTAL)
        assertNotNull(result)
        val split = result!!.root as PaneSplit
        assertEquals(SplitOrientation.HORIZONTAL, split.orientation)
        assertEquals("pane-2", result.newLeafId)
        assertEquals(2, PaneLayout.leaves(split).size)
    }

    @Test
    fun splitUnknownLeafReturnsNull() {
        assertNull(PaneLayout.split(PaneLeaf("pane-1"), "pane-9", SplitOrientation.HORIZONTAL))
    }

    @Test
    fun closePromotesSiblingAndReportsFocus() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.VERTICAL)!!.root
        val closed = PaneLayout.close(split, "pane-2")
        assertNotNull(closed)
        assertEquals("pane-1", closed!!.root.id)
        assertEquals("pane-1", closed.focusLeafId)
    }

    @Test
    fun lastLeafCannotBeClosed() {
        assertNull(PaneLayout.close(PaneLeaf("pane-1"), "pane-1"))
    }

    @Test
    fun ratioIsClampedToBounds() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.VERTICAL)!!.root
        val splitId = split.id

        val low = PaneLayout.setRatio(split, splitId, 0f) as PaneSplit
        assertEquals(PaneSplit.MIN_RATIO, low.ratio, 0.0001f)

        val high = PaneLayout.setRatio(split, splitId, 1f) as PaneSplit
        assertEquals(PaneSplit.MAX_RATIO, high.ratio, 0.0001f)
    }

    @Test
    fun focusNavigationCyclesThroughLeaves() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.HORIZONTAL)!!.root
        val leaves = PaneLayout.leaves(split)
        assertEquals("pane-1", leaves[0].id)
        assertEquals("pane-2", leaves[1].id)

        assertEquals("pane-1", PaneLayout.focusRelative(leaves, "pane-2", 1))
        assertEquals("pane-2", PaneLayout.focusRelative(leaves, "pane-1", -1))
        assertNull(PaneLayout.focusRelative(listOf(leaves[0]), "pane-1", 1))
    }

    @Test
    fun maxNodeNumberDrivesIdentifierAllocation() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.HORIZONTAL)!!.root
        assertTrue(PaneLayout.maxNodeNumber(split) >= 2)

        val nested = PaneLayout.split(split, "pane-2", SplitOrientation.VERTICAL)!!.root
        val ids = PaneLayout.leaves(nested).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(3, PaneLayout.leaves(nested).size)
    }

    @Test
    fun focusInDirectionMovesAcrossHorizontalSplit() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.HORIZONTAL)!!.root

        assertEquals("pane-2", PaneLayout.focusInDirection(split, "pane-1", PaneDirection.RIGHT))
        assertEquals("pane-1", PaneLayout.focusInDirection(split, "pane-2", PaneDirection.LEFT))
        assertNull(PaneLayout.focusInDirection(split, "pane-1", PaneDirection.LEFT))
        assertNull(PaneLayout.focusInDirection(split, "pane-1", PaneDirection.UP))
        assertNull(PaneLayout.focusInDirection(split, "pane-1", PaneDirection.DOWN))
    }

    @Test
    fun focusInDirectionMovesAcrossVerticalSplit() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.VERTICAL)!!.root

        assertEquals("pane-2", PaneLayout.focusInDirection(split, "pane-1", PaneDirection.DOWN))
        assertEquals("pane-1", PaneLayout.focusInDirection(split, "pane-2", PaneDirection.UP))
        assertNull(PaneLayout.focusInDirection(split, "pane-1", PaneDirection.RIGHT))
    }

    @Test
    fun focusInDirectionPicksNeighbourInGrid() {
        val horizontal = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.HORIZONTAL)!!.root
        val withTopRight = PaneLayout.split(horizontal, "pane-2", SplitOrientation.VERTICAL)!!.root
        val grid = PaneLayout.split(withTopRight, "pane-1", SplitOrientation.VERTICAL)!!.root

        // 2x2 网格：左上 pane-1、右上 pane-2、左下 pane-4、右下 pane-3。
        assertEquals("pane-2", PaneLayout.focusInDirection(grid, "pane-1", PaneDirection.RIGHT))
        assertEquals("pane-4", PaneLayout.focusInDirection(grid, "pane-1", PaneDirection.DOWN))
        assertEquals("pane-3", PaneLayout.focusInDirection(grid, "pane-2", PaneDirection.DOWN))
        assertEquals("pane-2", PaneLayout.focusInDirection(grid, "pane-3", PaneDirection.UP))
        assertEquals("pane-1", PaneLayout.focusInDirection(grid, "pane-2", PaneDirection.LEFT))
        assertEquals("pane-3", PaneLayout.focusInDirection(grid, "pane-4", PaneDirection.RIGHT))
    }

    @Test
    fun focusInDirectionReturnsNullForUnknownLeaf() {
        assertNull(PaneLayout.focusInDirection(PaneLeaf("pane-1"), "pane-9", PaneDirection.RIGHT))
    }

    @Test
    fun replaceLeavesTreeUnchangedOutsideTarget() {
        val split = PaneLayout.split(PaneLeaf("pane-1"), "pane-1", SplitOrientation.HORIZONTAL)!!.root
        val replaced = PaneLayout.replace(split, "pane-1") { node ->
            (node as PaneLeaf).copy(sessionId = "session-1")
        }!!
        val leaf = PaneLayout.findLeaf(replaced, "pane-1")!!
        assertEquals("session-1", leaf.sessionId)
        assertTrue(replaced is PaneSplit)
    }
}
