package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The interface for communication between {@link TerminalSession} and its client. It is used to
 * send callbacks to the client when {@link TerminalSession} changes or for sending other
 * back data to the client like logs.
 */
public interface TerminalSessionClient {

    void onTextChanged(@NonNull TerminalSession changedSession);

    void onTitleChanged(@NonNull TerminalSession changedSession);

    void onSessionFinished(@NonNull TerminalSession finishedSession);

    void onCopyTextToClipboard(@NonNull TerminalSession session, String text);

    /**
     * Returns the text of the clipboard for `OSC 52 ; Pc ; ?` requests of the terminal program, or
     * {@code null} if reading the clipboard is not allowed, in which case the request is ignored.
     *
     * Reading the clipboard is opt-in, since a program, like a remote shell, could otherwise read
     * everything the user has copied. The default implementation returns {@code null} so that
     * clients that do not implement it, like the ones of other termux plugins, keep the clipboard
     * write-only.
     */
    default String getClipboardText() {
        return null;
    }

    void onPasteTextFromClipboard(@Nullable TerminalSession session);

    void onBell(@NonNull TerminalSession session);

    /**
     * Called when a command finished, as reported by the shell integration with
     * `OSC 133 ; D ; <exit code>`.
     *
     * This variant is called instead of {@link #onCommandFinished(TerminalSession, int)} and carries
     * the command line that was executed and how long it ran, which the bundled shell integration
     * scripts of the fork report as an additional parameter, so that a client can show them in a
     * notification. The command line is {@code null} and the duration is -1 for shells that do not
     * report them, in which case {@link TerminalBuffer#getCommandTextForSequence(int)} can be used to
     * read the command line from the buffer.
     *
     * The default implementation forwards to {@link #onCommandFinished(TerminalSession, int)}, so that
     * clients of other termux plugins do not need to be updated.
     */
    default void onCommandFinished(@NonNull TerminalSession session, @NonNull TerminalCommandResult result) {
        onCommandFinished(session, result.getExitCode());
    }

    /**
     * Called when a command finished, as reported by the shell integration with
     * `OSC 133 ; D ; <exit code>`.
     *
     * The default implementation does nothing, so that clients of other termux plugins do not need
     * to be updated.
     */
    default void onCommandFinished(@NonNull TerminalSession session, int exitCode) {
    }

    /**
     * Called when a terminal program requested a notification with `OSC 99`, `OSC 9` or `OSC 777`.
     *
     * The default implementation does nothing, so that clients of other termux plugins do not need to be
     * updated.
     */
    default void onTerminalNotification(@NonNull TerminalSession session, @NonNull TerminalNotification notification) {
    }

    /**
     * Returns whether the icon data that a program sends with `OSC 99 ; p=icon` is decoded, so that the
     * client can tell the session not to spend time and memory on an icon that it never shows.
     *
     * The icons are not decoded by default, since they are only used by clients that have a setting for
     * them, like the notification cards of the termux app.
     */
    default boolean shouldDecodeOscNotificationIcons() {
        return false;
    }

    /**
     * Called when a terminal program requested to close a notification with
     * `OSC 99 ; i=<id> : p=close ;`, so that the client can close its card.
     *
     * The default implementation does nothing.
     */
    default void onOsc99Close(@NonNull TerminalSession session, @Nullable String id) {
    }

    /**
     * Called when a terminal program asked which notifications are still shown with
     * `OSC 99 ; i=<id> : p=alive ;`. The client must answer with
     * {@link TerminalNotificationProtocol#buildAliveResponse(String, java.util.Collection)} and write it
     * to the session.
     *
     * The default implementation does nothing, in which case the program gets no answer.
     */
    default void onOsc99AliveQuery(@NonNull TerminalSession session, @Nullable String id) {
    }

    void onColorsChanged(@NonNull TerminalSession session);

    void onTerminalCursorStateChange(boolean state);

    void setTerminalShellPid(@NonNull TerminalSession session, int pid);



    Integer getTerminalCursorStyle();



    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
