package com.termux.app.terminal;

import android.text.InputType;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the mapping of the `ime-input-type` property to the input type that the terminal reports to the IME.
 *
 * <p>The value decides whether an IME treats the terminal as a plain terminal, as a text editor or as a password
 * field, so a wrong mapping either hides the IME toolbar or makes the IME offer completions for the terminal.</p>
 */
public class ImeInputTypeTest {

    private static final int DEFAULT_INPUT_TYPE = InputType.TYPE_NULL;

    @Test
    public void testUnknownValueFallsBackToAuto() {
        Assert.assertEquals(ImeInputType.AUTO, ImeInputType.parse(null));
        Assert.assertEquals(ImeInputType.AUTO, ImeInputType.parse(""));
        Assert.assertEquals(ImeInputType.AUTO, ImeInputType.parse("unknown"));
        Assert.assertEquals(ImeInputType.AUTO, ImeInputType.parse(" AUTO "));
    }

    @Test
    public void testKnownValuesAreParsed() {
        Assert.assertEquals(ImeInputType.TEXT, ImeInputType.parse("text"));
        Assert.assertEquals(ImeInputType.PASSWORD, ImeInputType.parse("Password"));
        Assert.assertEquals(ImeInputType.NONE, ImeInputType.parse("none"));
        Assert.assertEquals(ImeInputType.HARDWARE, ImeInputType.parse("Hardware"));
    }

    @Test
    public void testAutoKeepsTheTypeOfTheView() {
        int viewType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        Assert.assertEquals(viewType, ImeInputType.AUTO.inputType(viewType));
    }

    @Test
    public void testTextReportsANormalTextFieldWithoutSuggestions() {
        int inputType = ImeInputType.TEXT.inputType(DEFAULT_INPUT_TYPE);

        Assert.assertEquals(InputType.TYPE_CLASS_TEXT,
            inputType & InputType.TYPE_MASK_CLASS);
        Assert.assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL,
            inputType & InputType.TYPE_MASK_VARIATION);
        Assert.assertTrue((inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
    }

    @Test
    public void testPasswordUsesTheVisiblePasswordVariation() {
        int inputType = ImeInputType.PASSWORD.inputType(DEFAULT_INPUT_TYPE);

        Assert.assertEquals(InputType.TYPE_CLASS_TEXT,
            inputType & InputType.TYPE_MASK_CLASS);
        Assert.assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            inputType & InputType.TYPE_MASK_VARIATION);
    }

    @Test
    public void testNoneReportsTypeNull() {
        Assert.assertEquals(InputType.TYPE_NULL,
            ImeInputType.NONE.inputType(InputType.TYPE_CLASS_TEXT));
    }

    @Test
    public void testHardwareReportsTypeNullOnlyWhileTheSoftKeyboardIsHidden() {
        int viewType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

        Assert.assertEquals(InputType.TYPE_NULL, ImeInputType.HARDWARE.inputType(viewType, true, false));
        // Without the state of the keyboards the mode must not change the reported type.
        Assert.assertEquals(viewType, ImeInputType.HARDWARE.inputType(viewType));
    }

    @Test
    public void testHardwarePresentsATextEditorWhileTheSoftKeyboardIsVisible() {
        int inputType = ImeInputType.HARDWARE.inputType(InputType.TYPE_NULL, true, true);

        Assert.assertEquals(InputType.TYPE_CLASS_TEXT, inputType & InputType.TYPE_MASK_CLASS);
        Assert.assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL, inputType & InputType.TYPE_MASK_VARIATION);
        Assert.assertTrue((inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
    }

    @Test
    public void testHardwareKeepsTheViewTypeWithoutAHardwareKeyboard() {
        int viewType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

        // Only the software keyboard is available, so the terminal keeps the type that the view computed and the
        // IME keeps its CJK workaround.
        Assert.assertEquals(viewType, ImeInputType.HARDWARE.inputType(viewType, false, true));
        Assert.assertEquals(viewType, ImeInputType.HARDWARE.inputType(viewType, false, false));
    }

    @Test
    public void testOtherModesIgnoreTheKeyboardStates() {
        int viewType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

        Assert.assertEquals(viewType, ImeInputType.AUTO.inputType(viewType, true, false));
        Assert.assertEquals(InputType.TYPE_NULL, ImeInputType.NONE.inputType(viewType, true, true));
        Assert.assertEquals(ImeInputType.TEXT.inputType(viewType),
            ImeInputType.TEXT.inputType(viewType, true, false));
    }
}
