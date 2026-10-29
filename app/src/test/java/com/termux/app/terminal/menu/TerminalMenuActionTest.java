package com.termux.app.terminal.menu;

import com.termux.R;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the resource of the titles of the actions of the terminal context menu, since the title of the
 * action that kills the process contains the pid of the session, which is not known when the settings
 * screen lists the actions, where the raw format argument would otherwise be shown as `%d`.
 *
 * The titles themselves are not looked up, since that needs a context, which a plain unit test has none.
 */
public class TerminalMenuActionTest {

    @Test
    public void testTitleWithoutSessionUsesTheLabelWithoutPid() {
        Assert.assertEquals(R.string.termux_terminal_menu_kill_process_label,
            TerminalMenuAction.KILL_PROCESS.getTitleRes(null));
    }

    @Test
    public void testOtherTitlesDoNotDependOnTheSession() {
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            if (action == TerminalMenuAction.KILL_PROCESS) continue;
            Assert.assertEquals(action.getTitleRes(), action.getTitleRes(null));
        }
    }
}
