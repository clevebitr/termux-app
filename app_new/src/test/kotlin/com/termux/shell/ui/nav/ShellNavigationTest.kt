package com.termux.shell.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ShellNavigation] 的层级压栈、覆盖层互斥与返回键分派测试。 */
class ShellNavigationTest {

    @Test
    fun initialStateShowsMainOnly() {
        val navigation = ShellNavigation()

        assertEquals(ShellLayer.MAIN, navigation.current)
        assertFalse(navigation.settingsVisible)
        assertFalse(navigation.paletteVisible)
        assertFalse(navigation.sessionDrawerVisible)
        assertFalse(navigation.canGoBack)
        assertNull(navigation.back())
    }

    @Test
    fun openSettingsPushesLayerAndKeepsMain() {
        val navigation = ShellNavigation().openSettings()

        assertTrue(navigation.settingsVisible)
        assertEquals(listOf(ShellLayer.MAIN, ShellLayer.SETTINGS), navigation.layers)
        assertTrue(navigation.canGoBack)
        assertEquals(navigation, navigation.openSettings())
    }

    @Test
    fun openingSettingsClearsOverlay() {
        val navigation = ShellNavigation()
            .openPalette(enabled = true)
            .openSettings()

        assertEquals(ShellOverlay.NONE, navigation.overlay)
        assertFalse(navigation.paletteVisible)
    }

    @Test
    fun paletteAndDrawerReplaceEachOther() {
        val palette = ShellNavigation().openSessionDrawer().openPalette(enabled = true)
        assertSame(ShellOverlay.PALETTE, palette.overlay)

        val drawer = palette.openSessionDrawer()
        assertEquals(ShellOverlay.SESSION_DRAWER, drawer.overlay)
        assertFalse(drawer.paletteVisible)
    }

    @Test
    fun paletteIsIgnoredWhenDisabled() {
        val navigation = ShellNavigation().openPalette(enabled = false)

        assertEquals(ShellOverlay.NONE, navigation.overlay)
        assertFalse(navigation.paletteVisible)
    }

    @Test
    fun drawerRequestIsIgnoredOnSettingsLayer() {
        val navigation = ShellNavigation().openSettings().openSessionDrawer()

        assertTrue(navigation.settingsVisible)
        assertFalse(navigation.sessionDrawerVisible)
    }

    @Test
    fun backClosesOverlayBeforeLeavingSettings() {
        val withPalette = ShellNavigation().openSettings().openPalette(enabled = true)

        val afterPalette = requireNotNull(withPalette.back())
        assertEquals(ShellOverlay.NONE, afterPalette.overlay)
        assertTrue(afterPalette.settingsVisible)

        val afterSettings = requireNotNull(afterPalette.back())
        assertEquals(ShellLayer.MAIN, afterSettings.current)
        assertFalse(afterSettings.canGoBack)
    }

    @Test
    fun drawerClosedCallbackOnlyAffectsDrawerOverlay() {
        val drawer = ShellNavigation().openSessionDrawer().onSessionDrawerClosed()
        assertFalse(drawer.sessionDrawerVisible)

        val palette = ShellNavigation().openPalette(enabled = true)
        assertEquals(palette.overlay, palette.onSessionDrawerClosed().overlay)
    }

    @Test
    fun closeSettingsIsNoOpOnMainLayer() {
        val navigation = ShellNavigation()

        assertEquals(ShellLayer.MAIN, navigation.closeSettings().current)
    }
}
