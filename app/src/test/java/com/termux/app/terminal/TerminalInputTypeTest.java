package com.termux.app.terminal;

import android.text.InputType;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the rule that decides whether the terminal is presented to the IME as a text editor.
 *
 * <p>An IME that treats the terminal as a text editor takes the key down events of the physical keyboard,
 * including Tab, and returns them later as committed text, which breaks the key order of `vim` and node TUIs. The
 * rule therefore keeps the terminal an untyped editor whenever the physical keys are the input path. Without a
 * hardware keyboard there are no physical keys to protect, so the compatibility mode may present the terminal as a
 * text editor to every IME instead of relying on the language tag that the view reads from the IME subtype.</p>
 */
public class TerminalInputTypeTest {

    private static final int VIEW_INPUT_TYPE =
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

    @Test
    public void testWithoutAHardwareKeyboardTheViewTypeIsKeptWhenTheCompatibilityModeIsDisabled() {
        // The compatibility mode is off, so the IME keeps the type that the view computed, which is the upstream
        // behaviour.
        Assert.assertEquals(VIEW_INPUT_TYPE,
            TermuxTerminalViewClient.resolveInputType(VIEW_INPUT_TYPE, false, false, false));
        Assert.assertEquals(VIEW_INPUT_TYPE,
            TermuxTerminalViewClient.resolveInputType(VIEW_INPUT_TYPE, false, true, false));
    }

    @Test
    public void testWithoutAHardwareKeyboardTheCompatibilityModeReportsATextEditor() {
        // The compatibility mode must not depend on the soft keyboard visibility: the IME only reads the editor
        // information when it creates the connection, which can happen before the keyboard is shown.
        Assert.assertEquals(InputType.TYPE_CLASS_TEXT,
            TermuxTerminalViewClient.resolveInputType(InputType.TYPE_NULL, false, false, true) & InputType.TYPE_MASK_CLASS);
        Assert.assertEquals(InputType.TYPE_CLASS_TEXT,
            TermuxTerminalViewClient.resolveInputType(VIEW_INPUT_TYPE, false, true, true) & InputType.TYPE_MASK_CLASS);
    }

    @Test
    public void testTheCompatibilityModeReportsAPlainTextEditorWithSuggestionsDisabled() {
        int inputType = TermuxTerminalViewClient.resolveInputType(InputType.TYPE_NULL, false, true, true);

        Assert.assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL, inputType & InputType.TYPE_MASK_VARIATION);
        Assert.assertTrue((inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
    }

    @Test
    public void testAHardwareKeyboardWithAHiddenSoftKeyboardReportsAnUntypedEditor() {
        // The compatibility mode must not break the key order of the physical keys.
        Assert.assertEquals(InputType.TYPE_NULL,
            TermuxTerminalViewClient.resolveInputType(VIEW_INPUT_TYPE, true, false, false));
        Assert.assertEquals(InputType.TYPE_NULL,
            TermuxTerminalViewClient.resolveInputType(VIEW_INPUT_TYPE, true, false, true));
    }

    @Test
    public void testAVisibleSoftKeyboardReportsATextEditor() {
        int inputType = TermuxTerminalViewClient.resolveInputType(VIEW_INPUT_TYPE, true, true, false);

        Assert.assertEquals(InputType.TYPE_CLASS_TEXT, inputType & InputType.TYPE_MASK_CLASS);
        Assert.assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL, inputType & InputType.TYPE_MASK_VARIATION);
        Assert.assertTrue((inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
    }

    @Test
    public void testAVisibleSoftKeyboardReportsATextEditorEvenWhenTheViewComputedTypeNull() {
        // The CJK workaround of the view may be disabled, but the IME still needs a text editor to compose text.
        Assert.assertEquals(InputType.TYPE_CLASS_TEXT,
            TermuxTerminalViewClient.resolveInputType(InputType.TYPE_NULL, true, true, false) & InputType.TYPE_MASK_CLASS);
    }
}
