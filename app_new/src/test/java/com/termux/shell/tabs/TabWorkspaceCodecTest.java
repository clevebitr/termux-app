package com.termux.shell.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.termux.shell.pane.PaneLayoutController;
import com.termux.shell.pane.PaneLeaf;
import com.termux.shell.pane.PaneSplit;
import com.termux.shell.session.SessionId;

import org.junit.Test;

/**
 * {@link TabWorkspaceCodec} 的编码与解码测试。
 */
public final class TabWorkspaceCodecTest {

    @Test
    public void roundTripPreservesTabsPanesAndActiveIndex() {
        TabWorkspace workspace = new TabWorkspace();
        WorkspaceTab first = workspace.newTab("one");
        WorkspaceTab second = workspace.newTab("two");

        PaneLayoutController panes = first.getPanes();
        PaneLeaf newLeaf = panes.split(panes.getFocusedLeaf(), PaneSplit.ORIENTATION_VERTICAL);
        assertNotNull(newLeaf);
        PaneSplit root = (PaneSplit) panes.getRoot();
        panes.setRatio(root, 0.3f);
        PaneLeaf firstLeaf = (PaneLeaf) root.getFirst();
        firstLeaf.setSessionId(SessionId.of("session-1"));
        firstLeaf.setScrollRow(42);

        workspace.select(0);
        String encoded = TabWorkspaceCodec.encode(workspace);
        assertEquals(2, TabWorkspaceCodec.readVersion(encoded));

        TabWorkspaceCodec.DecodedWorkspace decoded = TabWorkspaceCodec.decode(encoded);
        assertNotNull(decoded);
        assertEquals(0, decoded.getActiveIndex());
        assertEquals(2, decoded.getTabs().size());
        assertEquals("one", decoded.getTabs().get(0).getTitle());
        assertEquals("tab-2", decoded.getTabs().get(1).getId());

        PaneLayoutController restoredPanes = decoded.getTabs().get(0).getPanes();
        assertEquals(2, restoredPanes.getLeafCount());
        PaneSplit restoredRoot = (PaneSplit) restoredPanes.getRoot();
        assertEquals(PaneSplit.ORIENTATION_VERTICAL, restoredRoot.getOrientation());
        assertEquals(0.3f, restoredRoot.getRatio(), 0.0001f);
        PaneLeaf restoredLeaf = (PaneLeaf) restoredRoot.getFirst();
        assertEquals("session-1", restoredLeaf.getSessionId().getValue());
        assertEquals(42, restoredLeaf.getScrollRow());
    }

    @Test
    public void decodeRejectsMalformedDocuments() {
        assertNull(TabWorkspaceCodec.decode(null));
        assertNull(TabWorkspaceCodec.decode(""));
        assertNull(TabWorkspaceCodec.decode("{}"));
        assertNull(TabWorkspaceCodec.decode("{\"version\":1,\"tabs\":[]}"));
        assertNull(TabWorkspaceCodec.decode("{\"version\":2,\"tabs\":[]}"));
        assertEquals(-1, TabWorkspaceCodec.readVersion("not json"));
    }

    @Test
    public void decodeRejectsTabWithoutPaneTree() {
        String document = "{\"version\":2,\"activeTab\":0,\"tabs\":[{\"id\":\"tab-1\",\"title\":\"one\"}]}";
        assertNull(TabWorkspaceCodec.decode(document));
    }

    @Test
    public void decodeRejectsTooManyTabs() {
        StringBuilder tabs = new StringBuilder();
        for (int i = 0; i < TabWorkspace.MAX_TAB_COUNT + 1; i++) {
            if (i > 0) {
                tabs.append(',');
            }
            tabs.append("{\"id\":\"tab-").append(i + 1).append("\",\"title\":\"t\",\"root\":")
                .append("{\"type\":\"leaf\",\"id\":\"pane-1\"}}");
        }
        String document = "{\"version\":2,\"activeTab\":0,\"tabs\":[" + tabs + "]}";
        assertNull(TabWorkspaceCodec.decode(document));
    }
}
