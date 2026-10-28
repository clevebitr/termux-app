package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A notification that the terminal shows to the user, either because a terminal program requested it
 * with an escape code (like `OSC 99`, `OSC 9` or `OSC 777`), or because the terminal generated it
 * itself for an event, like a command that failed with a non-zero exit code as reported by the shell
 * integration with `OSC 133 ; D ; <exit code>`.
 *
 * The class only holds the data of the notification and is free of android views, so that the
 * escape sequence parsers and the code that builds notifications for events can be unit tested
 * without an emulator or a device.
 *
 * See https://sw.kovidgoyal.net/kitty/desktop-notifications/ for the `OSC 99` protocol.
 */
public final class TerminalNotification {

    /**
     * The kind of a notification, which is used to pick the accent color of its card.
     *
     * For `OSC 99` notifications the kind is derived from the `u` (urgency) metadata key, and for
     * failed and successful commands from their exit code.
     */
    public enum Kind {
        /** A command failed or a program reported a critical problem. */
        FAILURE,
        /** A command or a program reported success. */
        SUCCESS,
        /** A notification that the user should look at but that is not a failure, like an exited session. */
        WARNING,
        /** An informational notification. */
        INFO
    }

    /** Where a notification came from. */
    public enum Source {
        /** A command finished, as reported by the shell integration with `OSC 133 ; D`. */
        EXIT_CODE,
        /** An event of the app itself, like a session that exited or a bell that was received. */
        APP_EVENT,
        /** A terminal program requested the notification with an escape code. */
        OSC
    }

    /** The value of {@link #getExpiryMs()} to let the user configuration decide when the card is closed. */
    public static final long EXPIRY_DEFAULT = -1;
    /** The value of {@link #getExpiryMs()} to not close the card automatically. */
    public static final long EXPIRY_NEVER = 0;

    /** An identifier for the notification, used to update or close it later, or {@code null}. */
    @Nullable
    private final String mId;
    @NonNull
    private final Kind mKind;
    @NonNull
    private final Source mSource;
    @Nullable
    private final String mTitle;
    @Nullable
    private final String mBody;
    /** An additional line with details like the working directory, the session name or the duration. */
    @Nullable
    private final String mMeta;
    /** The time the notification was created, from {@link System#currentTimeMillis()}. */
    private final long mTimestampMs;
    /** How long the card is shown for, one of {@link #EXPIRY_DEFAULT}, {@link #EXPIRY_NEVER} or milliseconds. */
    private final long mExpiryMs;
    /** Whether the terminal must send an `OSC 99` close event back to the program when the card is closed. */
    private final boolean mReportClose;
    /** The name of the application that sent the notification, from the `OSC 99` `f` metadata key. */
    @Nullable
    private final String mAppName;
    /** The types of the notification, from the `OSC 99` `t` metadata keys. */
    @NonNull
    private final List<String> mTypes;
    /** The labels of the buttons of the notification, from the `OSC 99` `p=buttons` payload. */
    @NonNull
    private final List<String> mButtons;

    private TerminalNotification(@NonNull Builder builder) {
        mId = builder.mId;
        mKind = builder.mKind;
        mSource = builder.mSource;
        mTitle = builder.mTitle;
        mBody = builder.mBody;
        mMeta = builder.mMeta;
        mTimestampMs = builder.mTimestampMs;
        mExpiryMs = builder.mExpiryMs;
        mReportClose = builder.mReportClose;
        mAppName = builder.mAppName;
        mTypes = Collections.unmodifiableList(new ArrayList<>(builder.mTypes));
        mButtons = Collections.unmodifiableList(new ArrayList<>(builder.mButtons));
    }

    /** @return The identifier of the notification, or {@code null} if it cannot be updated or closed by id. */
    @Nullable
    public String getId() {
        return mId;
    }

    @NonNull
    public Kind getKind() {
        return mKind;
    }

    @NonNull
    public Source getSource() {
        return mSource;
    }

    /** @return The title of the notification, or {@code null} if it does not have one. */
    @Nullable
    public String getTitle() {
        return mTitle;
    }

    /** @return The body of the notification, or {@code null} if it does not have one. */
    @Nullable
    public String getBody() {
        return mBody;
    }

    /** @return The details line of the notification, or {@code null} if it does not have one. */
    @Nullable
    public String getMeta() {
        return mMeta;
    }

    public long getTimestampMs() {
        return mTimestampMs;
    }

    public long getExpiryMs() {
        return mExpiryMs;
    }

    public boolean shouldReportClose() {
        return mReportClose;
    }

    /** @return The name of the application that sent the notification, or {@code null} if unknown. */
    @Nullable
    public String getAppName() {
        return mAppName;
    }

    /** @return The types of the notification, which may be empty. */
    @NonNull
    public List<String> getTypes() {
        return mTypes;
    }

    /** @return The labels of the buttons of the notification, which may be empty. */
    @NonNull
    public List<String> getButtons() {
        return mButtons;
    }

    /** @return Whether the notification has no title, body and details line, so that it must be ignored. */
    public boolean isEmpty() {
        return isEmpty(mTitle) && isEmpty(mBody) && isEmpty(mMeta);
    }

    private static boolean isEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "TerminalNotification{id=" + mId + ", kind=" + mKind + ", source=" + mSource
            + ", title=" + mTitle + ", body=" + mBody + ", meta=" + mMeta + "}";
    }

    /** Builder for {@link TerminalNotification}. */
    public static final class Builder {

        @Nullable
        private String mId;
        @NonNull
        private Kind mKind = Kind.INFO;
        @NonNull
        private Source mSource = Source.APP_EVENT;
        @Nullable
        private String mTitle;
        @Nullable
        private String mBody;
        @Nullable
        private String mMeta;
        private long mTimestampMs = System.currentTimeMillis();
        private long mExpiryMs = EXPIRY_DEFAULT;
        private boolean mReportClose = false;
        @Nullable
        private String mAppName;
        @NonNull
        private final List<String> mTypes = new ArrayList<>();
        @NonNull
        private final List<String> mButtons = new ArrayList<>();

        public Builder(@NonNull Kind kind, @NonNull Source source) {
            mKind = kind;
            mSource = source;
        }

        public Builder setId(@Nullable String id) {
            mId = id;
            return this;
        }

        public Builder setKind(@NonNull Kind kind) {
            mKind = kind;
            return this;
        }

        public Builder setTitle(@Nullable String title) {
            mTitle = title;
            return this;
        }

        public Builder setBody(@Nullable String body) {
            mBody = body;
            return this;
        }

        public Builder setMeta(@Nullable String meta) {
            mMeta = meta;
            return this;
        }

        public Builder setTimestampMs(long timestampMs) {
            mTimestampMs = timestampMs;
            return this;
        }

        public Builder setExpiryMs(long expiryMs) {
            mExpiryMs = expiryMs;
            return this;
        }

        public Builder setReportClose(boolean reportClose) {
            mReportClose = reportClose;
            return this;
        }

        public Builder setAppName(@Nullable String appName) {
            mAppName = appName;
            return this;
        }

        public Builder addType(@Nullable String type) {
            if (type != null && !type.trim().isEmpty()) mTypes.add(type.trim());
            return this;
        }

        public Builder addButton(@Nullable String label) {
            if (label != null && !label.trim().isEmpty()) mButtons.add(label.trim());
            return this;
        }

        @NonNull
        public TerminalNotification build() {
            return new TerminalNotification(this);
        }
    }
}
