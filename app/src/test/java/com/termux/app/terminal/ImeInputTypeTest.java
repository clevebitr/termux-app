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
}
