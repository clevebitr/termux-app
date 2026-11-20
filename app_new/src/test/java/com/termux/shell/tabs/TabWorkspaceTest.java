package com.termux.shell.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link TabWorkspace} 的标签顺序、当前标签与上限行为测试。
 */
public final class TabWorkspaceTest {

    @Test
    public void newTabAppendsAndSelects() {
        TabWorkspace workspace = new TabWorkspace();
        WorkspaceTab first = workspace.newTab("one");
        assertNotNull(first);
        assertEquals("tab-1", first.getId());
        assertEquals(0, workspace.getActiveIndex());

        WorkspaceTab second = workspace.newTab("two");
        assertNotNull(second);
        assertEquals(1, workspace.getActiveIndex());
        assertEquals(2, workspace.size());
    }

    @Test
    public void closingTabBeforeActiveKeepsSameActiveTab() {
        TabWorkspace workspace = new TabWorkspace();
        workspace.newTab("one");
        workspace.newTab("two");
        workspace.newTab("three");
        assertTrue(workspace.select(1));

        assertTrue(workspace.close(0));
        assertEquals("two", workspace.getActiveTab().getTitle());
        assertEquals(0, workspace.getActiveIndex());
    }

    @Test
    public void closingActiveTabSelectsRightNeighbourThenLeft() {
        TabWorkspace workspace = new TabWorkspace();
        workspace.newTab("one");
        workspace.newTab("two");
        assertTrue(workspace.select(0));

        assertTrue(workspace.close(0));
        assertEquals("two", workspace.getActiveTab().getTitle());

        // 仅剩一个标签，关闭请求被拒绝。
        assertFalse(workspace.close(0));
        assertEquals(1, workspace.size());
    }

    @Test
    public void closingActiveLastTabSelectsLeftNeighbour() {
        TabWorkspace workspace = new TabWorkspace();
        workspace.newTab("one");
        workspace.newTab("two");

        assertTrue(workspace.close(1));
        assertEquals("one", workspace.getActiveTab().getTitle());
        assertEquals(0, workspace.getActiveIndex());
    }

    @Test
    public void moveKeepsActiveTabSelection() {
        TabWorkspace workspace = new TabWorkspace();
        workspace.newTab("one");
        workspace.newTab("two");
        workspace.newTab("three");
        assertTrue(workspace.move(2, 0));

        assertEquals("three", workspace.getTabs().get(0).getTitle());
        assertEquals(0, workspace.getActiveIndex());
        assertEquals("three", workspace.getActiveTab().getTitle());
    }

    @Test
    public void selectRelativeWrapsAround() {
        TabWorkspace workspace = new TabWorkspace();
        workspace.newTab("one");
        workspace.newTab("two");
        workspace.newTab("three");

        assertTrue(workspace.selectRelative(1));
        assertEquals(0, workspace.getActiveIndex());
        assertTrue(workspace.selectRelative(-1));
        assertEquals(2, workspace.getActiveIndex());
    }

    @Test
    public void tabCountIsCapped() {
        TabWorkspace workspace = new TabWorkspace();
        for (int i = 0; i < TabWorkspace.MAX_TAB_COUNT; i++) {
            assertNotNull(workspace.newTab("tab" + i));
        }
        assertNull(workspace.newTab("overflow"));
        assertEquals(TabWorkspace.MAX_TAB_COUNT, workspace.size());
    }

    @Test
    public void restoreResumesIdentifierNumbering() {
        TabWorkspace source = new TabWorkspace();
        source.newTab("one");
        source.newTab("two");

        TabWorkspace restored = new TabWorkspace();
        restored.restore(source.getTabs(), 1);
        assertEquals(2, restored.size());
        assertEquals(1, restored.getActiveIndex());

        WorkspaceTab appended = restored.newTab("three");
        assertNotNull(appended);
        assertEquals("tab-3", appended.getId());
    }

    @Test
    public void restoreClampsActiveIndex() {
        TabWorkspace source = new TabWorkspace();
        source.newTab("one");

        TabWorkspace restored = new TabWorkspace();
        restored.restore(source.getTabs(), 9);
        assertEquals(0, restored.getActiveIndex());
    }
}
