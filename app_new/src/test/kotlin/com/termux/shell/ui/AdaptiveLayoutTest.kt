package com.termux.shell.ui

import com.termux.shell.config.SettingsSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** [AdaptiveLayout] 的档位选择测试。 */
class AdaptiveLayoutTest {

    @Test
    fun autoFollowsWindowWidth() {
        assertEquals(WindowWidthClass.COMPACT, AdaptiveLayout.widthClassFor(SettingsSpec.LAYOUT_AUTO, 400))
        assertEquals(WindowWidthClass.MEDIUM, AdaptiveLayout.widthClassFor(SettingsSpec.LAYOUT_AUTO, 700))
        assertEquals(WindowWidthClass.EXPANDED, AdaptiveLayout.widthClassFor(SettingsSpec.LAYOUT_AUTO, 1200))
    }

    @Test
    fun phoneForcesCompactEvenOnWideWindow() {
        assertEquals(WindowWidthClass.COMPACT, AdaptiveLayout.widthClassFor(SettingsSpec.LAYOUT_PHONE, 1200))
    }

    @Test
    fun tabletForcesExpandedEvenOnNarrowWindow() {
        assertEquals(WindowWidthClass.EXPANDED, AdaptiveLayout.widthClassFor(SettingsSpec.LAYOUT_TABLET, 400))
    }

    @Test
    fun unknownValueFallsBackToAuto() {
        assertEquals(WindowWidthClass.COMPACT, AdaptiveLayout.widthClassFor(null, 500))
        assertEquals(WindowWidthClass.EXPANDED, AdaptiveLayout.widthClassFor("desktop", 900))
    }
}
