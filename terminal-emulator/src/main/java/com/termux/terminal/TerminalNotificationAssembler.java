package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the `OSC 99`, `OSC 9` and `OSC 777` desktop notification escape codes into
 * {@link TerminalNotification}s.
 *
 * The `OSC 99` protocol is chunked: a notification is sent as one or more escape codes that share the
 * `i` (id) metadata key, where all but the last one have `d=0` (not done). The payloads of a chunked
 * notification are concatenated before they are interpreted, which is what the senders do, like the
 * `kitten notify` utility of kitty: the payload is base64 encoded first and the encoded string is then
 * split into chunks of at most 4096 characters.
 *
 * See https://sw.kovidgoyal.net/kitty/desktop-notifications/ for the protocol.
 */
final class TerminalNotificationAssembler {

    /** The result of an escape code, which is applied by {@link TerminalEmulator}. */
    static final class Result {
        /** The notification to show, or {@code null}. */
        @Nullable
        final TerminalNotification notification;
        /** The id of the notification to close, from `p=close`, or {@code null}. */
        @Nullable
        final String closeId;
        /** The id to answer the `p=alive` query for, or {@code null}. */
        @Nullable
        final String aliveRequestId;
        /** The escape code to write back to the program, like the response to the `p=?` query. */
        @Nullable
        final String response;

        private Result(@Nullable TerminalNotification notification, @Nullable String closeId,
                       @Nullable String aliveRequestId, @Nullable String response) {
            this.notification = notification;
            this.closeId = closeId;
            this.aliveRequestId = aliveRequestId;
            this.response = response;
        }

        static Result notification(@NonNull TerminalNotification notification) {
            return new Result(notification, null, null, null);
        }

        static Result close(@Nullable String id) {
            return new Result(null, id, null, null);
        }

        static Result aliveQuery(@Nullable String requestId) {
            return new Result(null, null, requestId, null);
        }

        static Result response(@Nullable String response) {
            return new Result(null, null, null, response);
        }
    }

    /** The metadata and the payload of the chunks of a notification that is not done yet. */
    private static final class Pending {
        final StringBuilder title = new StringBuilder();
        final StringBuilder body = new StringBuilder();
        final StringBuilder buttons = new StringBuilder();
        final StringBuilder icon = new StringBuilder();
        final List<String> types = new ArrayList<>();
        @Nullable
        String appName;
        int urgency = -1;
        long expiryMs = TerminalNotification.EXPIRY_DEFAULT;
        boolean reportClose;
        boolean reportActivation;
        @NonNull
        TerminalNotification.Occasion occasion = TerminalNotification.Occasion.ALWAYS;
        /** Whether the payloads are base64 encoded, from `e=1`. */
        boolean encoded;

        int length() {
            return title.length() + body.length() + buttons.length() + icon.length();
        }
    }

    /** The maximum number of notifications that may be assembled at the same time. */
    private static final int MAX_PENDING = 8;
    /** The maximum number of characters of the payloads of a single notification. */
    private static final int MAX_PENDING_LENGTH = 128 * 1024;
    /** The maximum number of characters of the base64 encoded icon of a notification. */
    private static final int MAX_ICON_LENGTH = 96 * 1024;

    /** The separator of the labels of the buttons, which is the unicode line separator, U+2028. */
    private static final String BUTTONS_SEPARATOR = "\u2028";

    /** Provides whether the icons of the notifications are decoded, which the client of a session decides. */
    interface IconsEnabledSupplier {
        boolean areIconsEnabled();
    }

    /**
     * Whether the `p=icon` payloads are decoded, which is {@code false} when the client disabled them, so
     * that the base64 data is not decoded and not turned into a bitmap that is never shown.
     */
    @NonNull
    private final IconsEnabledSupplier mIconsEnabled;

    /** The notifications that are not done yet, keyed by their id, or by `""` if they have none. */
    @NonNull
    private final Map<String, Pending> mPending = new LinkedHashMap<>();

    /** Create an assembler that decodes the icons of the notifications. */
    TerminalNotificationAssembler() {
        this(() -> true);
    }

    /**
     * @param iconsEnabled Provides whether the `p=icon` payloads are decoded, which is asked for every
     *                     notification, so that a change of the configuration applies immediately.
     */
    TerminalNotificationAssembler(@NonNull IconsEnabledSupplier iconsEnabled) {
        mIconsEnabled = iconsEnabled;
    }

    /**
     * Parse an `OSC 99 ; <metadata> ; <payload>` escape code.
     *
     * @param parameter The text after the `99;` of the escape code.
     * @return The result to apply, or {@code null} if the escape code is ignored.
     */
    @Nullable
    Result handleOsc99(@NonNull String parameter) {
        // The protocol requires both semicolons to always be present, even when the payload is empty, but
        // a program that forgets the second one is tolerated, since the metadata is unaffected by it.
        int separatorIndex = parameter.indexOf(';');
        String metadata = separatorIndex < 0 ? parameter : parameter.substring(0, separatorIndex);
        String payload = separatorIndex < 0 ? "" : parameter.substring(separatorIndex + 1);

        String id = null;
        String payloadType = "title";
        boolean done = true;
        boolean encoded = false;
        int urgency = -1;
        long expiryMs = TerminalNotification.EXPIRY_DEFAULT;
        boolean reportClose = false;
        boolean reportActivation = false;
        boolean focus = true;
        TerminalNotification.Occasion occasion = TerminalNotification.Occasion.ALWAYS;
        String appName = null;
        List<String> types = new ArrayList<>();

        for (String part : metadata.split(":")) {
            if (part.isEmpty()) continue;
            int equalsIndex = part.indexOf('=');
            if (equalsIndex <= 0) continue;

            String key = part.substring(0, equalsIndex);
            String value = part.substring(equalsIndex + 1);
            if (key.length() != 1) continue;

            switch (key.charAt(0)) {
                case 'i':
                    id = TerminalNotificationProtocol.sanitizeId(value);
                    break;
                case 'p':
                    payloadType = value.trim();
                    break;
                case 'd':
                    done = !"0".equals(value);
                    break;
                case 'e':
                    encoded = "1".equals(value);
                    break;
                case 'u':
                    urgency = parseInt(value, -1);
                    break;
                case 'w':
                    // `-1` uses the expiry of the user configuration, `0` never expires and a positive
                    // value is the number of milliseconds after which the notification is closed.
                    expiryMs = parseLong(value, TerminalNotification.EXPIRY_DEFAULT);
                    break;
                case 'c':
                    reportClose = "1".equals(value);
                    break;
                case 'a':
                    for (String action : value.split(",")) {
                        if ("report".equals(action)) reportActivation = true;
                        else if ("-report".equals(action)) reportActivation = false;
                        else if ("focus".equals(action)) focus = true;
                        else if ("-focus".equals(action)) focus = false;
                    }
                    break;
                case 'o':
                    occasion = parseOccasion(value);
                    break;
                case 'f':
                    appName = decodeText(value);
                    break;
                case 't': {
                    String type = decodeText(value);
                    if (type != null && !type.trim().isEmpty()) types.add(type.trim());
                    break;
                }
                case 's': // The sound name, the cards never play a sound of their own.
                case 'n': // The name of an icon of the system icon theme.
                case 'g': // The cache key of icon data that was sent earlier.
                default:
                    break;
            }
        }

        // The query, the close request and the list of living notifications are not notifications
        // themselves, they are answered right away.
        switch (payloadType) {
            case "?":
                return Result.response(TerminalNotificationProtocol.buildQueryResponse(id));
            case "close":
                // Without an id the request must be a no-op.
                if (id == null) return null;
                mPending.remove(id);
                return Result.close(id);
            case "alive":
                return Result.aliveQuery(id);
            default:
                break;
        }

        if (!"title".equals(payloadType) && !"body".equals(payloadType)
            && !"buttons".equals(payloadType) && !"icon".equals(payloadType)) {
            // Unknown payload types must be ignored, to allow the protocol to be extended.
            return null;
        }

        String pendingKey = id != null ? id : "";
        Pending pending = mPending.get(pendingKey);
        if (pending == null) {
            if (mPending.size() >= MAX_PENDING) return null;
            pending = new Pending();
            mPending.put(pendingKey, pending);
        }

        if (appName != null) pending.appName = appName;
        for (String type : types) {
            if (!pending.types.contains(type)) pending.types.add(type);
        }
        if (urgency >= 0) pending.urgency = urgency;
        if (expiryMs != TerminalNotification.EXPIRY_DEFAULT) pending.expiryMs = expiryMs;
        if (reportClose) pending.reportClose = true;
        if (reportActivation) pending.reportActivation = true;
        if (!focus) {
            // The `focus` action only decides whether the window is focused when the notification is
            // clicked, which the cards do not do, so it is only used to keep the metadata complete.
        }
        if (occasion != TerminalNotification.Occasion.ALWAYS) pending.occasion = occasion;
        pending.encoded = pending.encoded || encoded;

        switch (payloadType) {
            case "title":
                pending.title.append(payload);
                break;
            case "body":
                pending.body.append(payload);
                break;
            case "buttons":
                pending.buttons.append(payload);
                break;
            case "icon":
                // A disabled client never shows the icon, so its payload is not even collected, which also
                // keeps the size limit of the pending notification for the text that is shown.
                if (mIconsEnabled.areIconsEnabled()) pending.icon.append(payload);
                break;
            default:
                break;
        }

        if (pending.length() > MAX_PENDING_LENGTH) {
            mPending.remove(pendingKey);
            return null;
        }

        if (!done) return null;

        mPending.remove(pendingKey);
        return Result.notification(build(id, pending, mIconsEnabled.areIconsEnabled()));
    }

    /**
     * Parse an `OSC 9 ; <message>` escape code of iTerm2, which only has a body and no title.
     *
     * The ConEmu progress reports, which use the same escape code with a `4` as the first parameter, are
     * not notifications and are ignored.
     *
     * @param parameter The text after the `9;` of the escape code.
     * @return The result to apply, or {@code null} if the escape code is ignored.
     */
    @Nullable
    Result handleOsc9(@NonNull String parameter) {
        if (parameter.isEmpty()) return null;
        if (parameter.equals("4") || parameter.startsWith("4;")) return null;

        return Result.notification(new TerminalNotification.Builder(
            TerminalNotification.Kind.INFO, TerminalNotification.Source.OSC)
            .setBody(sanitizeText(parameter))
            .build());
    }

    /**
     * Parse an `OSC 777 ; notify ; <title> ; <body>` escape code of urxvt.
     *
     * @param parameter The text after the `777;` of the escape code.
     * @return The result to apply, or {@code null} if the escape code is ignored.
     */
    @Nullable
    Result handleOsc777(@NonNull String parameter) {
        int commandSeparatorIndex = parameter.indexOf(';');
        if (commandSeparatorIndex < 0) return null;

        String command = parameter.substring(0, commandSeparatorIndex).trim();
        if (!"notify".equals(command)) return null;

        String rest = parameter.substring(commandSeparatorIndex + 1);
        int titleSeparatorIndex = rest.indexOf(';');
        String title = titleSeparatorIndex >= 0 ? rest.substring(0, titleSeparatorIndex) : rest;
        String body = titleSeparatorIndex >= 0 ? rest.substring(titleSeparatorIndex + 1) : null;

        TerminalNotification notification = new TerminalNotification.Builder(
            TerminalNotification.Kind.INFO, TerminalNotification.Source.OSC)
            .setTitle(sanitizeText(title))
            .setBody(sanitizeText(body))
            .build();

        if (notification.isEmpty()) return null;

        return Result.notification(notification);
    }

    /** Build the notification of the assembled {@code pending} chunks. */
    @NonNull
    private static TerminalNotification build(@Nullable String id, @NonNull Pending pending, boolean iconsEnabled) {
        String title = decodeText(pending.title, pending.encoded);
        String body = decodeText(pending.body, pending.encoded);
        String buttons = decodeText(pending.buttons, pending.encoded);

        TerminalNotification.Builder builder = new TerminalNotification.Builder(
            getKind(pending.urgency), TerminalNotification.Source.OSC)
            .setId(id)
            .setTitle(sanitizeText(title))
            .setBody(sanitizeText(body))
            .setExpiryMs(pending.expiryMs)
            .setReportClose(pending.reportClose)
            .setReportActivation(pending.reportActivation)
            .setAppName(pending.appName)
            .setOccasion(pending.occasion)
            .setIcon(iconsEnabled ? decodeIcon(pending.icon, pending.encoded) : null);

        for (String type : pending.types) builder.addType(type);

        if (buttons != null) {
            for (String label : buttons.split(BUTTONS_SEPARATOR)) builder.addButton(sanitizeText(label));
        }

        return builder.build();
    }

    /** @return The kind of a notification with the urgency {@code urgency} of the `u` metadata key. */
    @NonNull
    private static TerminalNotification.Kind getKind(int urgency) {
        // The urgency is only used to pick the accent color of the card, 2 is red like a failed command.
        if (urgency == 2) return TerminalNotification.Kind.FAILURE;
        if (urgency == 1) return TerminalNotification.Kind.INFO;
        return TerminalNotification.Kind.INFO;
    }

    /** @return The occasion of the `o` metadata key. */
    @NonNull
    private static TerminalNotification.Occasion parseOccasion(@NonNull String value) {
        switch (value.trim()) {
            case "unfocused":
                return TerminalNotification.Occasion.UNFOCUSED;
            case "invisible":
                return TerminalNotification.Occasion.INVISIBLE;
            default:
                return TerminalNotification.Occasion.ALWAYS;
        }
    }

    /**
     * @return The text of {@code payload}, which is decoded from base64 if {@code encoded} is set.
     */
    @Nullable
    private static String decodeText(@NonNull CharSequence payload, boolean encoded) {
        if (payload.length() == 0) return null;
        if (!encoded) return payload.toString();

        try {
            return decodeText(payload.toString());
        } catch (Exception e) {
            // A payload that is not valid base64 is used as is, so that a program that forgets to set
            // `e=1` still shows something instead of nothing.
            return payload.toString();
        }
    }

    /** @return The base64 decoded text of {@code value}, or {@code null} if it is empty or invalid. */
    @Nullable
    private static String decodeText(@NonNull String value) {
        if (value.isEmpty()) return null;
        try {
            return new String(TerminalBase64.decode(value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return The icon of the notification, or {@code null} if it cannot be decoded. */
    @Nullable
    private static Bitmap decodeIcon(@NonNull CharSequence payload, boolean encoded) {
        if (payload.length() == 0 || payload.length() > MAX_ICON_LENGTH) return null;

        try {
            byte[] bytes = encoded
                ? TerminalBase64.decode(payload.toString())
                : payload.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes == null || bytes.length == 0) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return The text with the control characters that would break the layout of a card replaced by
     *         spaces, while the newlines and the tabs are kept, or {@code null} if nothing is left.
     */
    @Nullable
    private static String sanitizeText(@Nullable String text) {
        if (text == null) return null;

        StringBuilder sanitized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) sanitized.append(c);
        }

        String result = sanitized.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static int parseInt(@NonNull String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(@NonNull String value, long defaultValue) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
