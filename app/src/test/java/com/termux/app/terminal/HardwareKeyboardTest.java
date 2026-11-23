package com.termux.app.terminal;

import android.content.res.Configuration;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the configuration part of the hardware keyboard check that `ime-input-type=hardware` uses.
 *
 * <p>The check decides whether the terminal reports {@code TYPE_NULL}, so a wrong answer either keeps the IME
 * taking the physical keys or reports an untyped editor while only the software keyboard is available.</p>
 */
public class HardwareKeyboardTest {

    @Test
    public void testConfigurationWithoutAKeyboardIsNotAttached() {
        Assert.assertFalse(HardwareKeyboard.isAttached(Configuration.KEYBOARD_NOKEYS));
    }

    @Test
    public void testConfigurationWithAKeyboardIsAttached() {
        Assert.assertTrue(HardwareKeyboard.isAttached(Configuration.KEYBOARD_QWERTY));
        Assert.assertTrue(HardwareKeyboard.isAttached(Configuration.KEYBOARD_12KEY));
        Assert.assertTrue(HardwareKeyboard.isAttached(Configuration.KEYBOARD_NOKEYS + 1));
    }
}
