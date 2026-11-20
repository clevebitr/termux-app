package com.termux.shell.tabs

import com.termux.shell.pane.PaneLayout
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.PaneSplit
import com.termux.shell.pane.SplitOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** [TabWorkspaceCodec] 的编码与解码测试。 */
class TabWorkspaceCodecTest {

    @Test
    fun roundTripPreservesTabsPanesAndActiveIndex() {
        val (split, newLeaf) = TabWorkspace.initial("one")
            .splitFocused(SplitOrientation.VERTICAL)
        assertNotNull(newLeaf)
        val firstLeaf = split.activeTab.leaves.first()
        val withSession = split.updateActiveTab { tab ->
            val root = PaneLayout.replace(tab.root, firstLeaf.id) { node ->
                (node as PaneLeaf).copy(sessionId = "session-1", scrollRow = 42)
            }!!
            tab.copy(root = root)
        }
        val withRatio = withSession.setRatio((withSession.activeTab.root as PaneSplit).id, 0.3f)!!
        val twoTabs = withRatio.newTab("two")!!.select(0)!!

        val encoded = TabWorkspaceCodec.encode(twoTabs)
        assertEquals(2, TabWorkspaceCodec.readVersion(encoded))

        val decoded = TabWorkspaceCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(0, decoded!!.activeIndex)
        assertEquals(2, decoded.tabs.size)
        assertEquals("one", decoded.tabs[0].title)
        assertEquals("tab-2", decoded.tabs[1].id)

        val restoredRoot = decoded.tabs[0].root as PaneSplit
        assertEquals(SplitOrientation.VERTICAL, restoredRoot.orientation)
        assertEquals(0.3f, restoredRoot.ratio, 0.0001f)
        val restoredLeaf = restoredRoot.first as PaneLeaf
        assertEquals("session-1", restoredLeaf.sessionId)
        assertEquals(42, restoredLeaf.scrollRow)
    }

    @Test
    fun decodeRejectsMalformedDocuments() {
        assertNull(TabWorkspaceCodec.decode(null))
        assertNull(TabWorkspaceCodec.decode(""))
        assertNull(TabWorkspaceCodec.decode("{}"))
        assertNull(TabWorkspaceCodec.decode("""{"version":1,"tabs":[]}"""))
        assertNull(TabWorkspaceCodec.decode("""{"version":2,"tabs":[]}"""))
        assertEquals(-1, TabWorkspaceCodec.readVersion("not json"))
    }

    @Test
    fun decodeRejectsTabWithoutPaneTree() {
        val document = """{"version":2,"activeTab":0,"tabs":[{"id":"tab-1","title":"one"}]}"""
        assertNull(TabWorkspaceCodec.decode(document))
    }

    @Test
    fun decodeRejectsTooManyTabs() {
        val tabs = (1..TabWorkspace.MAX_TAB_COUNT + 1).joinToString(",") { index ->
            """{"id":"tab-$index","title":"t","root":{"type":"leaf","id":"pane-1"}}"""
        }
        assertNull(TabWorkspaceCodec.decode("""{"version":2,"activeTab":0,"tabs":[$tabs]}"""))
    }
}
