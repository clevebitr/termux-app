package com.termux.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalCommandResult;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalNotification;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link TerminalCommandResult} of the shell integration into a notification card for a
 * command that exited with a non-zero exit code.
 *
 * The card shows the exit code, the command line, how long the command ran and in which working
 * directory and session it ran, and clicking the card jumps to the command in the transcript.
 *
 * The cards of commands that return a non-zero exit code by design, like `grep` when it does not find
 * a match, can be filtered out with the `notification-exit-code-ignore-commands` property, so that the
 * user only gets a card for commands that are likely to be a real error.
 */
public final class ExitCodeNotifier {

    private static final String LOG_TAG = "ExitCodeNotifier";

    /**
     * The strings that are commonly the end of a shell prompt. The command line of a buffer row is
     * everything after the first one of them that is found near the start of the row.
     */
    private static final String[] PROMPT_ENDINGS = {
        "\u276f ", // Heavy right angle quote, used by starship and others.
        "\u279c ", // Heavy round-tipped rightwards arrow, used by oh-my-zsh.
        "\u03bb ", // Lambda, used by the default zsh theme of termux.
        "\u00bb ", // Right-pointing double angle quotation mark.
        "$ ",
        "# ",
        "% ",
        "> ",
    };

    /**
     * The longest a prompt is expected to be, so that a `$ ` inside a command is not treated as one.
     * Package private for the tests.
     */
    static final int MAX_PROMPT_LENGTH = 120;
    /** The longest command line that is shown in a card. Package private for the tests. */
    static final int MAX_COMMAND_LENGTH = 300;

    @NonNull
    private final TermuxActivity mActivity;
    @Nullable
    private final TerminalNotificationManager mManager;

    public ExitCodeNotifier(@NonNull TermuxActivity activity, @Nullable TerminalNotificationManager manager) {
        mActivity = activity;
        mManager = manager;
    }

    /**
     * Show a card for a command that finished, if the cards of failed commands are enabled and the
     * command is not filtered out by the configuration.
     *
     * @param session The session that the command ran in.
     * @param result The result of the command, as reported by the shell integration.
     * @return Whether a card was shown, so that the caller does not show its own notification for it.
     */
    public boolean onCommandFinished(@NonNull TerminalSession session, @NonNull TerminalCommandResult result) {
        if (mManager == null) return false;

        NotificationConfig config = mManager.getConfig();
        if (!config.enabled || !config.exitCodeEnabled) return false;
        // Only commands that failed are shown, a successful command is reported by the prompt itself.
        if (result.getExitCode() == 0) return false;

        String command = getCommandText(session, result);
        if (command != null && isIgnoredCommand(command, config)) {
            Logger.logVerbose(LOG_TAG, "Not showing a card for the ignored command \"" + command + "\"");
            return false;
        }

        if (config.exitCodeMinDurationSeconds > 0) {
            long durationMs = result.getDurationMs();
            if (durationMs < 0 || durationMs < config.exitCodeMinDurationSeconds * 1000L) {
                Logger.logVerbose(LOG_TAG, "Not showing a card for a command that ran for " + durationMs
                    + "ms, the minimum is " + config.exitCodeMinDurationSeconds + "s");
                return false;
            }
        }

        TerminalNotification notification = new TerminalNotification.Builder(
            TerminalNotification.Kind.FAILURE, TerminalNotification.Source.EXIT_CODE)
            .setTitle(mActivity.getString(R.string.msg_command_failed, result.getExitCode()))
            .setBody(command)
            .setMeta(getMeta(session, result, config))
            .build();

        int commandSequence = result.getCommandSequence();
        Runnable onClickAction = commandSequence != 0 ? () -> mActivity.jumpToCommand(session, commandSequence) : null;

        return mManager.notify(notification, onClickAction);
    }

    /**
     * @return The command line of the command, from the extended `OSC 133 ; D` parameter if the shell
     *         integration reported it, or from the buffer otherwise, or {@code null} if neither has it.
     */
    @Nullable
    private String getCommandText(@NonNull TerminalSession session, @NonNull TerminalCommandResult result) {
        String command = result.getCommand();
        if (command != null && !command.trim().isEmpty()) return sanitizeCommand(command);

        // The shell integration of the upstream app, and shells that only send the exit code, do not
        // report the command line, so it is read from the row that the command is anchored on. The row
        // also contains the prompt, which is stripped with a heuristic, since the standard
        // `OSC 133 ; B` prompt end mark is not reported by the bundled scripts.
        if (result.getCommandSequence() == 0) return null;

        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) return null;

        String rowText = emulator.getScreen().getCommandTextForSequence(result.getCommandSequence());
        if (rowText == null || rowText.trim().isEmpty()) return null;

        return sanitizeCommand(stripPrompt(rowText));
    }

    /** @return The text of {@code rowText} after the shell prompt, or the text itself if there is none. */
    @NonNull
    static String stripPrompt(@NonNull String rowText) {
        int promptEnd = -1;
        for (String ending : PROMPT_ENDINGS) {
            int index = rowText.indexOf(ending);
            if (index < 0 || index > MAX_PROMPT_LENGTH) continue;
            if (promptEnd < 0 || index < promptEnd) promptEnd = index;
        }

        if (promptEnd < 0) return rowText;

        return rowText.substring(promptEnd + 2);
    }

    /** @return The command line with its control characters collapsed, trimmed and length limited. */
    @NonNull
    static String sanitizeCommand(@NonNull String command) {
        // Multi line commands are sent with their newlines collapsed by the shell integration, but a
        // command line that is read from the buffer can still contain other control characters.
        StringBuilder builder = new StringBuilder(command.length());
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            builder.append(Character.isISOControl(c) ? ' ' : c);
        }

        String sanitized = builder.toString().replaceAll(" +", " ").trim();
        if (sanitized.length() > MAX_COMMAND_LENGTH)
            sanitized = sanitized.substring(0, MAX_COMMAND_LENGTH) + "...";

        return sanitized;
    }

    /** @return Whether the command is one of the commands of `notification-exit-code-ignore-commands`. */
    static boolean isIgnoredCommand(@NonNull String command, @NonNull NotificationConfig config) {
        if (config.exitCodeIgnoreCommands.isEmpty()) return false;

        // The command name is the first word of the command line, without the directory it is in, so
        // that `/system/bin/grep` is matched by `grep`. A leading `sudo` or `env` is skipped, since the
        // command that actually runs is the one that the user cares about.
        String[] words = command.trim().split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (word.equals("sudo") || word.equals("env") || word.equals("command") || word.equals("exec"))
                continue;

            int separatorIndex = Math.max(word.lastIndexOf('/'), word.lastIndexOf('\\'));
            String commandName = separatorIndex >= 0 ? word.substring(separatorIndex + 1) : word;
            return config.exitCodeIgnoreCommands.contains(commandName.toLowerCase(Locale.ROOT));
        }

        return false;
    }

    /**
     * @return The details line of the card, with the working directory, the duration and the session
     *         name, or {@code null} if none of them is enabled or known.
     */
    @Nullable
    private String getMeta(@NonNull TerminalSession session, @NonNull TerminalCommandResult result,
                           @NonNull NotificationConfig config) {
        List<String> parts = new ArrayList<>();

        if (config.exitCodeShowCwd) {
            String cwd = getShortenedCwd(session);
            if (cwd != null && !cwd.isEmpty()) parts.add(cwd);
        }

        long durationMs = result.getDurationMs();
        if (durationMs >= 0) parts.add(formatDuration(durationMs));

        if (config.exitCodeShowSession && session.mSessionName != null && !session.mSessionName.trim().isEmpty())
            parts.add(session.mSessionName.trim());

        if (parts.isEmpty()) return null;

        String separator = " " + mActivity.getString(R.string.notification_meta_separator) + " ";
        StringBuilder meta = new StringBuilder();
        for (String part : parts) {
            if (meta.length() > 0) meta.append(separator);
            meta.append(part);
        }

        return meta.toString();
    }

    /** @return The working directory of the session, with the home directory shortened to `~`. */
    @Nullable
    private static String getShortenedCwd(@NonNull TerminalSession session) {
        String cwd = session.getCwd();
        if (cwd == null) return null;

        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        if (cwd.equals(home)) return "~";
        if (cwd.startsWith(home + "/")) return "~" + cwd.substring(home.length());

        return cwd;
    }

    /** @return The duration in a short form like `12.3s` or `2m05s`. */
    @NonNull
    static String formatDuration(long durationMs) {
        if (durationMs < 10000) {
            // Show one decimal for short commands, like `0.4s` and `9.9s`.
            return String.format(Locale.ROOT, "%.1fs", durationMs / 1000.0);
        }

        long seconds = Math.round(durationMs / 1000.0);
        if (seconds < 60) return seconds + "s";

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60)
            return String.format(Locale.ROOT, "%dm%02ds", minutes, remainingSeconds);

        long hours = minutes / 60;
        return String.format(Locale.ROOT, "%dh%02dm", hours, minutes % 60);
    }
}
