package com.termux.terminal;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;

/** Tests for {@link TerminalBase64}, which is used for the `OSC 52` clipboard escape. */
public class TerminalBase64Test extends TestCase {

    public void testEncode() {
        assertEquals("", TerminalBase64.encodeToString(new byte[0]));
        assertEquals("YQ==", TerminalBase64.encodeToString("a".getBytes(StandardCharsets.UTF_8)));
        assertEquals("YWI=", TerminalBase64.encodeToString("ab".getBytes(StandardCharsets.UTF_8)));
        assertEquals("YWJj", TerminalBase64.encodeToString("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("SGVsbG8sIHdvcmxk", TerminalBase64.encodeToString("Hello, world".getBytes(StandardCharsets.UTF_8)));
        // Multi-byte utf-8 characters.
        assertEquals("5L2g5aW9", TerminalBase64.encodeToString("你好".getBytes(StandardCharsets.UTF_8)));
    }

    public void testDecode() {
        assertEquals("", new String(TerminalBase64.decode(""), StandardCharsets.UTF_8));
        assertEquals("a", new String(TerminalBase64.decode("YQ=="), StandardCharsets.UTF_8));
        assertEquals("a", new String(TerminalBase64.decode("YQ"), StandardCharsets.UTF_8));
        assertEquals("ab", new String(TerminalBase64.decode("YWI="), StandardCharsets.UTF_8));
        assertEquals("abc", new String(TerminalBase64.decode("YWJj"), StandardCharsets.UTF_8));
        assertEquals("你好", new String(TerminalBase64.decode("5L2g5aW9"), StandardCharsets.UTF_8));
    }

    public void testDecodeIgnoresWhitespace() {
        assertEquals("Hello, world", new String(TerminalBase64.decode("SGVsbG8s\r\nIHdvcmxk"), StandardCharsets.UTF_8));
        assertEquals("Hello, world", new String(TerminalBase64.decode(" SGVsbG8s IHdvcmxk "), StandardCharsets.UTF_8));
    }

    public void testDecodeRejectsInvalidInput() {
        assertDecodeFails("SGVsbG8*");
        assertDecodeFails("SGVsbG8=a"); // Data after padding is invalid.
        assertDecodeFails("Y"); // A single dangling character cannot be decoded.
        assertDecodeFails(null);
    }

    public void testRoundTrip() {
        for (int length = 0; length < 100; length++) {
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) data[i] = (byte) (i * 31 + 7);
            assertEquals(length, TerminalBase64.decode(TerminalBase64.encodeToString(data)).length);
            assertEquals(new String(data, StandardCharsets.ISO_8859_1),
                new String(TerminalBase64.decode(TerminalBase64.encodeToString(data)), StandardCharsets.ISO_8859_1));
        }
    }

    private static void assertDecodeFails(String encoded) {
        try {
            TerminalBase64.decode(encoded);
            fail("Expected IllegalArgumentException for " + encoded);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

}
