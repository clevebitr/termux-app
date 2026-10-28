package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The result of a command that finished, as reported by the shell integration with
 * `OSC 133 ; D ; <exit code> ; <command>`.
 *
 * The command text is an extension of the standard `OSC 133 ; D ; <exit code>` sequence, which the
 * bundled shell integration scripts of the fork send as an additional `;` separated parameter. Other
 * terminals ignore the additional parameter, and shells without the fork scripts simply do not send
 * it, in which case {@link #getCommand()} is {@code null} and the terminal can fall back to reading
 * the command line from its buffer with {@link TerminalBuffer#getCommandTextForSequence(int)}.
 */
public final class TerminalCommandResult {

    private final int mExitCode;
    @Nullable
    private final String mCommand;
    private final long mDurationMs;
    private final int mCommandSequence;

    public TerminalCommandResult(int exitCode, @Nullable String command, long durationMs, int commandSequence) {
        mExitCode = exitCode;
        mCommand = command;
        mDurationMs = durationMs;
        mCommandSequence = commandSequence;
    }

    /** @return The exit code of the command, or 0 if it was not reported. */
    public int getExitCode() {
        return mExitCode;
    }

    /** @return The command line that was executed, or {@code null} if the shell did not report it. */
    @Nullable
    public String getCommand() {
        return mCommand;
    }

    /**
     * @return How long the command ran in milliseconds, or -1 if the shell integration only reports
     *         the prompt and the exit code marks but not the command start mark, like bash before
     *         version 4.4.
     */
    public long getDurationMs() {
        return mDurationMs;
    }

    /**
     * @return The sequence number of the command, which can be passed to
     *         {@link TerminalBuffer#getCommandTextForSequence(int)} to read the command line from the
     *         buffer and to {@code TerminalView#scrollToCommandSequence(int)} to jump to it, or 0 if
     *         the command was not started with a command start mark.
     */
    public int getCommandSequence() {
        return mCommandSequence;
    }

    @NonNull
    @Override
    public String toString() {
        return "TerminalCommandResult{exitCode=" + mExitCode + ", command=" + mCommand
            + ", durationMs=" + mDurationMs + ", sequence=" + mCommandSequence + "}";
    }
}
