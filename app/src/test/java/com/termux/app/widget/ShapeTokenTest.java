package com.termux.app.widget;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards that the shape tokens of the theme and the defaults of the Java code stay equal.
 *
 * <p>{@link TerminalDialogStyles#DIALOG_CORNER_DP} and {@link TerminalDialogStyles#ROW_CORNER_DP} are compile time
 * constants, because the configuration classes of the terminal menu and the notifications need a default value
 * without a {@code Context}, while the dialogs of the app read the radius from {@code res/values/dimens.xml} so
 * that the theme and the code draw the same shape. Changing one without the other would make the terminal menu and
 * the Material 3 dialogs drift apart, which this test turns into a test failure.</p>
 */
public class ShapeTokenTest {

    private static final Path DIMENS = Paths.get("src/main/res/values/dimens.xml");

    @Test
    public void dialogCornerRadiusMatchesDimen() throws IOException {
        Assert.assertEquals(TerminalDialogStyles.DIALOG_CORNER_DP, readDp("termux_dialog_corner_radius"));
    }

    @Test
    public void rowCornerRadiusMatchesDimen() throws IOException {
        Assert.assertEquals(TerminalDialogStyles.ROW_CORNER_DP, readDp("termux_row_corner_radius"));
    }

    /** @return The {@code dp} value of the dimen {@code name}, like {@code 16} for {@code 16dp}. */
    private static int readDp(String name) throws IOException {
        String content = new String(Files.readAllBytes(DIMENS), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(
            "<dimen name=\"" + name + "\">\\s*(\\d+)dp\\s*</dimen>").matcher(content);
        Assert.assertTrue("dimen " + name + " is missing from " + DIMENS, matcher.find());
        return Integer.parseInt(matcher.group(1));
    }
}
