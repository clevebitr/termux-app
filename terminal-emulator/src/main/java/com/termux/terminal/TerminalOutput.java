package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

/** A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}. */
public abstract class TerminalOutput {

    /** Write a string using the UTF-8 encoding to the terminal client. */
    public final void write(String data) {
        if (data == null) return;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    /** Write bytes to the terminal client. */
    public abstract void write(byte[] data, int offset, int count);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void titleChanged(String oldTitle, String newTitle);

    /** Notify the terminal client that text should be copied to clipboard. */
    public abstract void onCopyTextToClipboard(String text);

    /**
     * Returns the text of the clipboard that a terminal program requested with `OSC 52 ; Pc ; ?`,
     * or {@code null} if the client does not allow reading the clipboard, in which case the request
     * is ignored. Reading the clipboard is opt-in, since a program, like a remote shell, could
     * otherwise read everything the user has copied.
     */
    public String getClipboardText() {
        return null;
    }

    /** Notify the terminal client that text should be pasted from clipboard. */
    public abstract void onPasteTextFromClipboard();

    /** Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received. */
    public abstract void onBell();

    /**
     * Notify the terminal client that a command finished, as reported by the shell integration with
     * `OSC 133 ; D ; <exit code>`.
     *
     * The default implementation forwards to {@link #onCommandFinished(int)}, so that clients that do
     * not need the command line and the duration of the command do not have to be updated.
     */
    public void onCommandFinished(@NonNull TerminalCommandResult result) {
        onCommandFinished(result.getExitCode());
    }

    /**
     * Notify the terminal client that a command finished, as reported by the shell integration with
     * `OSC 133 ; D ; <exit code>`.
     */
    public void onCommandFinished(int exitCode) {
    }

    public abstract void onColorsChanged();

    /**
     * Notify the terminal client that a terminal program requested a notification with `OSC 99`,
     * `OSC 9` or `OSC 777`.
     */
    public void onTerminalNotification(@NonNull TerminalNotification notification) {
    }

    /**
     * Notify the terminal client that a terminal program requested to close a notification with
     * `OSC 99 ; i=<id> : p=close ;`.
     */
    public void onOsc99Close(@Nullable String id) {
    }

    /**
     * Notify the terminal client that a terminal program asked which notifications are still shown with
     * `OSC 99 ; i=<id> : p=alive ;`, so that it can answer with the ids of the notifications that it
     * still shows.
     */
    public void onOsc99AliveQuery(@Nullable String id) {
    }

}
