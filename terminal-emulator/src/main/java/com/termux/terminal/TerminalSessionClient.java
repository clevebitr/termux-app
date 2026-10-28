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
