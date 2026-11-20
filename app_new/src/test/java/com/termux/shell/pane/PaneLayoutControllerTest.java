package com.termux.shell.pane;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link PaneLayoutController} 的结构操作与焦点迁移测试。
 */
public final class PaneLayoutControllerTest {

    @Test
    public void splitCreatesSiblingAndMovesFocus() {
        PaneLayoutController controller = new PaneLayoutController();
        PaneLeaf first = controller.getFocusedLeaf();

        PaneLeaf created = controller.split(first, PaneSplit.ORIENTATION_HORIZONTAL);
        assertNotNull(created);
        assertEquals(2, controller.getLeafCount());
        assertEquals(created, controller.getFocusedLeaf());
        assertEquals(PaneSplit.ORIENTATION_HORIZONTAL,
            ((PaneSplit) controller.getRoot()).getOrientation());
    }

    @Test
    public void splitStopsAtLeafLimit() {
        PaneLayoutController controller = new PaneLayoutController();
        while (controller.getLeafCount() < PaneLayoutController.MAX_LEAF_COUNT) {
            assertNotNull(controller.split(controller.getFocusedLeaf(), PaneSplit.ORIENTATION_VERTICAL));
        }
        assertNull(controller.split(controller.getFocusedLeaf(), PaneSplit.ORIENTATION_VERTICAL));
    }

    @Test
    public void closingFocusedLeafPromotesSiblingAndMovesFocus() {
        PaneLayoutController controller = new PaneLayoutController();
        PaneLeaf first = controller.getFocusedLeaf();
        PaneLeaf second = controller.split(first, PaneSplit.ORIENTATION_HORIZONTAL);
        assertNotNull(second);

        assertTrue(controller.close(second));
        assertEquals(1, controller.getLeafCount());
        assertEquals(first, controller.getRoot());
        assertEquals(first, controller.getFocusedLeaf());
    }

    @Test
    public void lastLeafCannotBeClosed() {
        PaneLayoutController controller = new PaneLayoutController();
        assertFalse(controller.close(controller.getFocusedLeaf()));
    }

    @Test
    public void ratioIsClampedToBounds() {
        PaneLayoutController controller = new PaneLayoutController();
        PaneLeaf first = controller.getFocusedLeaf();
        controller.split(first, PaneSplit.ORIENTATION_VERTICAL);
        PaneSplit root = (PaneSplit) controller.getRoot();

        assertTrue(controller.setRatio(root, 0.0f));
        assertEquals(PaneLayoutController.MIN_RATIO, root.getRatio(), 0.0001f);

        assertTrue(controller.setRatio(root, 1.0f));
        assertEquals(PaneLayoutController.MAX_RATIO, root.getRatio(), 0.0001f);

        assertFalse(controller.setRatio(root, PaneLayoutController.MAX_RATIO));
    }

    @Test
    public void focusNavigationCyclesThroughLeaves() {
        PaneLayoutController controller = new PaneLayoutController();
        PaneLeaf first = controller.getFocusedLeaf();
        PaneLeaf second = controller.split(first, PaneSplit.ORIENTATION_HORIZONTAL);
        assertNotNull(second);

        assertTrue(controller.focusNext());
        assertEquals(first, controller.getFocusedLeaf());
        assertTrue(controller.focusPrevious());
        assertEquals(second, controller.getFocusedLeaf());
    }

    @Test
    public void focusNavigationIsNoOpWithSingleLeaf() {
        PaneLayoutController controller = new PaneLayoutController();
        assertFalse(controller.focusNext());
        assertFalse(controller.focusPrevious());
    }

    @Test
    public void restoringTreeResetsFocusToFirstLeaf() {
        PaneLayoutController source = new PaneLayoutController();
        source.split(source.getFocusedLeaf(), PaneSplit.ORIENTATION_VERTICAL);

        PaneLayoutController restored = new PaneLayoutController();
        restored.restore(source.getRoot());
        assertEquals(2, restored.getLeafCount());
        assertEquals(restored.collectLeaves().get(0), restored.getFocusedLeaf());
    }
}
