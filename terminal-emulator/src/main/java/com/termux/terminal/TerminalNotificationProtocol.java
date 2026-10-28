package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * Builds the escape codes of the `OSC 99` desktop notification protocol that a terminal has to send
 * back to the program that requested a notification.
 *
 * The escape codes are used by {@link TerminalNotificationAssembler} to answer the support query, and by
 * the app when the user clicks a notification card or when a card is closed.
 *
 * See https://sw.kovidgoyal.net/kitty/desktop-notifications/ for the protocol.
 */
public final class TerminalNotificationProtocol {

    /** The prefix of the escape codes, `<ESC>]99;`. */
    private static final String OSC_99_PREFIX = "\033]99;";
    /** The string terminator of the escape codes, `<ESC>\`. */
    private static final String OSC_STRING_TERMINATOR = "\033\\";
    /** The separator of the entries of a list of ids, like in the response of the `p=alive` query. */
    private static final String ALIVE_IDS_SEPARATOR = ",";

    /** The characters that an identifier of the protocol may contain. */
    private static final String ID_ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-+.";

    private TerminalNotificationProtocol() {
    }

    /**
     * @return The escape code that reports that the notification {@code id} was activated, like when the
     *         user clicked its card, or {@code null} if the notification has no id and therefore cannot
     *         be reported.
     */
    @Nullable
    public static String buildActivationReport(@Nullable String id) {
        String sanitizedId = sanitizeId(id);
        if (sanitizedId == null) return null;
        return OSC_99_PREFIX + "i=" + sanitizedId + OSC_STRING_TERMINATOR;
    }

    /**
     * @return The escape code that reports that the button {@code buttonNumber} of the notification
     *         {@code id} was clicked, where the first button is 1, or {@code null} if the notification
     *         has no id.
     */
    @Nullable
    public static String buildButtonReport(@Nullable String id, int buttonNumber) {
        String sanitizedId = sanitizeId(id);
        if (sanitizedId == null || buttonNumber < 1) return null;
        return OSC_99_PREFIX + "i=" + sanitizedId + ";" + buttonNumber + OSC_STRING_TERMINATOR;
    }

    /**
     * @return The escape code that reports that the notification {@code id} was closed, or {@code null}
     *         if the notification has no id.
     */
    @Nullable
    public static String buildCloseReport(@Nullable String id) {
        String sanitizedId = sanitizeId(id);
        if (sanitizedId == null) return null;
        return OSC_99_PREFIX + "i=" + sanitizedId + ":p=close" + OSC_STRING_TERMINATOR;
    }

    /**
     * @return The escape code that answers the `p=alive` query, with the ids of the notifications that
     *         are still shown, or {@code null} if the query had no id to answer to.
     */
    @Nullable
    public static String buildAliveResponse(@Nullable String requestId, @Nullable Collection<String> aliveIds) {
        String sanitizedId = sanitizeId(requestId);
        if (sanitizedId == null) sanitizedId = "0";

        StringBuilder response = new StringBuilder(OSC_99_PREFIX);
        response.append("i=").append(sanitizedId).append(":p=alive;");
        if (aliveIds != null) {
            boolean first = true;
            for (String aliveId : aliveIds) {
                String sanitizedAliveId = sanitizeId(aliveId);
                if (sanitizedAliveId == null) continue;
                if (!first) response.append(ALIVE_IDS_SEPARATOR);
                response.append(sanitizedAliveId);
                first = false;
            }
        }
        response.append(OSC_STRING_TERMINATOR);
        return response.toString();
    }

    /**
     * @return The escape code that answers the `p=?` support query of the notification {@code id}.
     *
     * The supported features are:
     * - `a=focus,report`: the notification can be activated and reported back.
     * - `c=1`: close events are supported.
     * - `o=always,unfocused,invisible`: all occasions are supported, although only `always` is honored by
     *   the notification cards, since they are only shown in the window of the app.
     * - `p=title,body,close,?,alive,buttons,icon`: the payload types that are supported.
     * - `s=system,silent`: the sounds that are supported, the cards only ever play the system one.
     * - `u=0,1,2`: the urgency values that are supported, only the accent color of the card changes.
     * - `w=1`: the auto expiry of notifications is supported.
     */
    @Nullable
    public static String buildQueryResponse(@Nullable String id) {
        // The protocol requires the identifier of the query to be echoed, and `0` to be used if the query
        // did not have one.
        String sanitizedId = sanitizeId(id);
        if (sanitizedId == null) sanitizedId = "0";

        return OSC_99_PREFIX + "i=" + sanitizedId + ":p=?;"
            + "a=focus,report:c=1:o=always,unfocused,invisible:"
            + "p=title,body,close,?,alive,buttons,icon:"
            + "s=system,silent:u=0,1,2:w=1"
            + OSC_STRING_TERMINATOR;
    }

    /**
     * @return The {@code id} with the characters that the protocol does not allow removed, since an id
     *         is sent back to the program in responses and must not be able to inject escape codes, or
     *         {@code null} if nothing is left.
     */
    @Nullable
    public static String sanitizeId(@Nullable String id) {
        if (id == null) return null;

        StringBuilder sanitized = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (ID_ALLOWED_CHARACTERS.indexOf(c) >= 0) sanitized.append(c);
        }

        return sanitized.length() == 0 ? null : sanitized.toString();
    }
}
