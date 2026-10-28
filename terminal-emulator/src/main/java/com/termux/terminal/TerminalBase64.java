package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * A minimal Base64 implementation used for the `OSC 52` clipboard escape.
 *
 * {@code android.util.Base64} could be used instead, but its static methods return `null` in local
 * JVM unit tests, since the terminal-emulator module runs them with `unitTests.returnDefaultValues`
 * against the stubbed android jar. That would make the `OSC 52` handling untestable and would make
 * the terminal throw a `NullPointerException` in tests only. `java.util.Base64` cannot be used
 * either, since it is only available on Android 8.0+ and the minSdk of termux is lower than that.
 *
 * The alphabet and the padding are the standard ones. Encoding never inserts line breaks, while
 * decoding ignores whitespace and accepts missing padding, like `android.util.Base64` does.
 */
final class TerminalBase64 {

    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    /** Lookup table for decoding, with -1 for invalid characters, -2 for the padding and -3 for whitespace. */
    private static final int[] LOOKUP = new int[128];

    static {
        Arrays.fill(LOOKUP, -1);
        for (int i = 0; i < ALPHABET.length; i++) LOOKUP[ALPHABET[i]] = i;
        LOOKUP['='] = -2;
        LOOKUP['\n'] = -3;
        LOOKUP['\r'] = -3;
        LOOKUP['\t'] = -3;
        LOOKUP[' '] = -3;
    }

    private TerminalBase64() {
    }

    /** Encode {@code data} to a Base64 string without line breaks. */
    static String encodeToString(byte[] data) {
        if (data == null || data.length == 0) return "";

        StringBuilder builder = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i + 2 < data.length) {
            int chunk = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8) | (data[i + 2] & 0xff);
            builder.append(ALPHABET[(chunk >>> 18) & 0x3f])
                .append(ALPHABET[(chunk >>> 12) & 0x3f])
                .append(ALPHABET[(chunk >>> 6) & 0x3f])
                .append(ALPHABET[chunk & 0x3f]);
            i += 3;
        }

        int remaining = data.length - i;
        if (remaining == 1) {
            int chunk = (data[i] & 0xff) << 16;
            builder.append(ALPHABET[(chunk >>> 18) & 0x3f])
                .append(ALPHABET[(chunk >>> 12) & 0x3f])
                .append("==");
        } else if (remaining == 2) {
            int chunk = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8);
            builder.append(ALPHABET[(chunk >>> 18) & 0x3f])
                .append(ALPHABET[(chunk >>> 12) & 0x3f])
                .append(ALPHABET[(chunk >>> 6) & 0x3f])
                .append('=');
        }
        return builder.toString();
    }

    /**
     * Decode the Base64 string {@code encoded}, ignoring whitespace.
     *
     * @throws IllegalArgumentException if the string contains invalid characters or a dangling
     *                                  character after the padding.
     */
    static byte[] decode(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("The base64 string is null");

        ByteArrayOutputStream output = new ByteArrayOutputStream(encoded.length() * 3 / 4);
        int buffer = 0;
        int bits = 0;
        boolean paddingSeen = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int value = c < LOOKUP.length ? LOOKUP[c] : -1;
            if (value == -3) continue;
            if (value == -2) {
                paddingSeen = true;
                continue;
            }
            if (value < 0) throw new IllegalArgumentException("Invalid base64 character: " + c);
            if (paddingSeen) throw new IllegalArgumentException("Invalid base64 data after padding");

            buffer = (buffer << 6) | value;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output.write((buffer >>> bits) & 0xff);
            }
        }

        // A single dangling character cannot encode any byte.
        if (bits >= 6) throw new IllegalArgumentException("Invalid base64 length");

        return output.toByteArray();
    }

}
