package com.termux.terminal;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Tests the parsing of the `OSC 99`, `OSC 9` and `OSC 777` desktop notification escape codes, since the
 * chunking, the base64 payloads and the metadata keys are easy to get wrong and hard to see in the app.
 *
 * The tests call the assembler directly instead of feeding the escape codes to a terminal, so that the
 * result of every escape code can be checked, including the ones that are answered by the terminal, like
 * the support query.
 */
public class TerminalNotificationAssemblerTest extends TestCase {

    private TerminalNotificationAssembler mAssembler;

    @Override
    protected void setUp() {
        mAssembler = new TerminalNotificationAssembler();
    }

    private TerminalNotification parseNotification(String parameter) {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99(parameter);
        assertNotNull("The escape code must produce a result", result);
        assertNotNull("The escape code must produce a notification", result.notification);
        return result.notification;
    }

    public void testSimpleTitle() {
        TerminalNotification notification = parseNotification(";Hello world");
        assertEquals("Hello world", notification.getTitle());
        assertNull(notification.getBody());
        assertEquals(TerminalNotification.Source.OSC, notification.getSource());
        assertEquals(TerminalNotification.Kind.INFO, notification.getKind());
    }

    public void testTitleAndBodyInOneEscapeCode() {
        TerminalNotification notification = parseNotification("i=1:p=body;The body");
        assertEquals("The body", notification.getBody());
    }

    public void testChunkedNotificationIsAssembled() {
        // Only the last chunk produces a notification, the first one is held back.
        assertNull(mAssembler.handleOsc99("i=42:d=0;Hello "));
        assertNull(mAssembler.handleOsc99("i=42:d=0:p=body;and "));
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99("i=42:d=1:p=body;welcome");
        assertNotNull(result);
        assertEquals("Hello", result.notification.getTitle());
        assertEquals("and welcome", result.notification.getBody());
        assertEquals("42", result.notification.getId());
    }

    public void testChunksWithTheSameIdAreIndependentOfOtherIds() {
        assertNull(mAssembler.handleOsc99("i=a:d=0;first "));
        assertNull(mAssembler.handleOsc99("i=b:d=0;second "));
        TerminalNotification a = mAssembler.handleOsc99("i=a:done=1;done").notification;
        TerminalNotification b = mAssembler.handleOsc99("i=b;done").notification;
        assertEquals("first done", a.getTitle());
        assertEquals("second done", b.getTitle());
    }

    public void testBase64Payload() {
        // "Tschüss" base64 encoded, since it is not ASCII.
        TerminalNotification notification = parseNotification("e=1;VHNjaMO8c3M=");
        assertEquals("Tschüss", notification.getTitle());
    }

    public void testButtonsAreSplitOnTheLineSeparator() {
        TerminalNotification notification = parseNotification("p=buttons;Yes\u2028No\u2028Maybe");
        assertEquals(Arrays.asList("Yes", "No", "Maybe"), notification.getButtons());
    }

    public void testMetadata() {
        TerminalNotification notification = parseNotification(
            "i=7:u=2:c=1:a=report:o=unfocused:w=0;Watch out");

        assertEquals(TerminalNotification.Kind.FAILURE, notification.getKind());
        assertTrue(notification.shouldReportClose());
        assertTrue(notification.shouldReportActivation());
        assertEquals(TerminalNotification.Occasion.UNFOCUSED, notification.getOccasion());
        assertEquals(TerminalNotification.EXPIRY_NEVER, notification.getExpiryMs());
    }

    public void testUrgencyTwoIsFailureAndOneIsInfo() {
        assertEquals(TerminalNotification.Kind.FAILURE, parseNotification("i=1:u=2;x").getKind());
        assertEquals(TerminalNotification.Kind.INFO, parseNotification("i=2:u=1;x").getKind());
        assertEquals(TerminalNotification.Kind.INFO, parseNotification("i=3:u=0;x").getKind());
    }

    public void testAppNameAndTypesAreDecodedFromBase64() {
        // "nvim" and "build" base64 encoded.
        TerminalNotification notification = parseNotification("i=1:f=bnZpbQ==:t=YnVpbGQ=;done");
        assertEquals("nvim", notification.getAppName());
        assertEquals(Arrays.asList("build"), notification.getTypes());
    }

    public void testExpiryIsUsedFromTheMetadata() {
        assertEquals(5000, parseNotification("i=1:w=5000;x").getExpiryMs());
        assertEquals(TerminalNotification.EXPIRY_DEFAULT, parseNotification("i=2;x").getExpiryMs());
    }

    public void testCloseRequest() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99("i=abc:p=close;");
        assertNotNull(result);
        assertEquals("abc", result.closeId);
        assertNull(result.notification);
    }

    public void testCloseRequestWithoutIdIsANoOp() {
        assertNull(mAssembler.handleOsc99("p=close;"));
    }

    public void testSupportQueryIsAnswered() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99("i=q1:p=?;");
        assertNotNull(result);
        assertNotNull(result.response);
        assertTrue(result.response.startsWith("\033]99;i=q1:p=?;"));
        assertTrue(result.response.contains("a=focus,report"));
        assertTrue(result.response.contains("p=title,body,close,?,alive,buttons,icon"));
        assertTrue(result.response.endsWith("\033\\"));
    }

    public void testSupportQueryWithoutIdUsesZero() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99("p=?;");
        assertNotNull(result.response);
        assertTrue(result.response.startsWith("\033]99;i=0:p=?;"));
    }

    public void testAliveQueryIsPassedToTheClient() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99("i=q2:p=alive;");
        assertNotNull(result);
        assertEquals("q2", result.aliveRequestId);
        assertNull(result.notification);
    }

    public void testUnknownPayloadTypeIsIgnored() {
        assertNull(mAssembler.handleOsc99("i=1:p=unknown;data"));
    }

    public void testEscapeCodeWithoutAPayloadSeparatorIsTolerated() {
        // The protocol requires both semicolons, but a close request that forgets the second one is
        // still understood, since the metadata is unaffected by it.
        assertEquals("1", mAssembler.handleOsc99("i=1:p=close").closeId);

        // A chunk that is not done is still held back, so nothing is returned for it.
        assertNull(mAssembler.handleOsc99("i=1:d=0;chunk"));
    }

    public void testControlCharactersAreRemovedButNewlinesAreKept() {
        TerminalNotification notification = parseNotification("i=1;line1\nline2\u0007end");
        assertEquals("line1\nline2end", notification.getTitle());
    }

    public void testIdIsSanitized() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc99("i=../evil\u0007id1:p=close;");
        // The `/` and the BEL are not allowed in an id and are removed.
        assertEquals("..evilid1", result.closeId);
    }

    public void testOsc9Body() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc9("Build finished");
        assertNotNull(result.notification);
        assertEquals("Build finished", result.notification.getBody());
        assertNull(result.notification.getTitle());
    }

    public void testOsc9ConEmuProgressIsIgnored() {
        assertNull(mAssembler.handleOsc9("4;1;50"));
        assertNull(mAssembler.handleOsc9("4"));
    }

    public void testOsc9EmptyMessageIsIgnored() {
        assertNull(mAssembler.handleOsc9(""));
    }

    public void testOsc777() {
        TerminalNotificationAssembler.Result result = mAssembler.handleOsc777("notify;The title;The body");
        assertNotNull(result.notification);
        assertEquals("The title", result.notification.getTitle());
        assertEquals("The body", result.notification.getBody());
    }

    public void testOsc777WithoutBody() {
        TerminalNotification notification = mAssembler.handleOsc777("notify;Only a title").notification;
        assertEquals("Only a title", notification.getTitle());
        assertNull(notification.getBody());
    }

    public void testOsc777WithOtherCommandIsIgnored() {
        assertNull(mAssembler.handleOsc777("something;else"));
        assertNull(mAssembler.handleOsc777("notify"));
    }

    public void testEmptyNotificationIsEmpty() {
        assertTrue(parseNotification("i=1;").isEmpty());
        assertFalse(parseNotification("i=1;x").isEmpty());
    }

    /**
     * The icon payload of a client that does not show icons must not be collected, since a long one would
     * otherwise count against the size limit of the pending notification and even drop the text around it.
     */
    public void testIconPayloadIsNotCollectedWhenIconsAreDisabled() {
        StringBuilder icon = new StringBuilder();
        for (int i = 0; i < 200 * 1024; i++) icon.append('A');

        TerminalNotificationAssembler enabled = new TerminalNotificationAssembler(() -> true);
        TerminalNotificationAssembler disabled = new TerminalNotificationAssembler(() -> false);

        // The icon chunk is not done, so no notification is produced either way.
        assertNull(enabled.handleOsc99("i=1:d=0:p=title;Build "));
        assertNull(disabled.handleOsc99("i=1:d=0:p=title;Build "));
        assertNull(enabled.handleOsc99("i=1:d=0:p=icon;" + icon));
        assertNull(disabled.handleOsc99("i=1:d=0:p=icon;" + icon));

        // With the icons enabled the payload passed the size limit, so the pending notification was dropped
        // and the title is lost. A disabled client keeps the title, since it never collected the icon.
        assertNull(enabled.handleOsc99("i=1;").notification.getTitle());
        assertEquals("Build", disabled.handleOsc99("i=1;").notification.getTitle());
    }
}
