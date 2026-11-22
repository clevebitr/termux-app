package com.termux.app.commands;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One entry of the command palette.
 *
 * <p>A command is an immutable title plus the action to run when it is chosen, so that the palette only
 * filters and renders and every entry point that offers the same action can reuse the same object. The
 * action must be safe to run after the palette dialog has been dismissed.</p>
 */
public final class ShellCommand {

    /** The stable identifier, used for the keys and for the tests. */
    @NonNull
    public final String id;
    /** The title that the palette shows. */
    @NonNull
    public final CharSequence title;
    /** The group title that the palette shows above the command, like "Tab" or "Terminal". */
    @NonNull
    public final String category;
    /** The shortcut that opens the command directly, or {@code null} when it has none. */
    @Nullable
    public final String shortcut;
    /** Whether the command can be run in the current state. */
    public final boolean enabled;

    @NonNull
    private final Runnable mAction;

    public ShellCommand(@NonNull String id, @NonNull CharSequence title, @NonNull String category,
                        @Nullable String shortcut, boolean enabled, @NonNull Runnable action) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.shortcut = shortcut;
        this.enabled = enabled;
        this.mAction = action;
    }

    /** Run the command. */
    public void execute() {
        mAction.run();
    }

    /** The text that the palette shows and searches, which includes the shortcut. */
    @NonNull
    @Override
    public String toString() {
        return shortcut == null ? title.toString() : title + "    " + shortcut;
    }
}
