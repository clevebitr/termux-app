package com.termux.shared.termux.shell.command.environment;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.environment.AndroidShellEnvironment;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.ShellCommandShellEnvironment;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.shell.TermuxShellUtils;

import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * Environment for Termux.
 */
public class TermuxShellEnvironment extends AndroidShellEnvironment {

    private static final String LOG_TAG = "TermuxShellEnvironment";

    /** Environment variable for the termux {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH}. */
    public static final String ENV_PREFIX = "PREFIX";

    /** Environment variable that an interactive `bash` runs before printing each prompt. */
    public static final String ENV_PROMPT_COMMAND = "PROMPT_COMMAND";

    /**
     * Environment variable that tells the shell integration scripts that the command history is synced
     * by the app, so that they can apply the equivalent options of their shell, since the
     * {@link #ENV_PROMPT_COMMAND} variable is only used by `bash`.
     */
    public static final String ENV_TERMUX_SHELL_HISTORY_SYNC = "TERMUX_SHELL_HISTORY_SYNC";

    /**
     * The {@link #ENV_PROMPT_COMMAND} value that keeps the command history across sessions.
     *
     * `history -a` appends the commands that were added since the last call to the history file and
     * `history -n` reads the commands that other sessions appended to it, so both are needed to share
     * the history between the sessions that are running at the same time. Appending after every prompt
     * instead of only on exit is what keeps the history when the app kills the shell with `SIGKILL`,
     * which is what `TermuxService.onDestroy()` does, so that `bash` never gets to write the history
     * file itself. `history` is a builtin, so no process is started for it.
     */
    public static final String PROMPT_COMMAND_HISTORY_SYNC = "history -a; history -n";

    public TermuxShellEnvironment() {
        super();
        shellCommandShellEnvironment = new TermuxShellCommandShellEnvironment();
    }


    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void init(@NonNull Context currentPackageContext) {
        TermuxAppShellEnvironment.setTermuxAppEnvironment(currentPackageContext);
    }

    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void writeEnvironmentToFile(@NonNull Context currentPackageContext) {
        HashMap<String, String> environmentMap = new TermuxShellEnvironment().getEnvironment(currentPackageContext, false);
        String environmentString = ShellEnvironmentUtils.convertEnvironmentToDotEnvFile(environmentMap);

        // Write environment string to temp file and then move to final location since otherwise
        // writing may happen while file is being sourced/read
        Error error = FileUtils.writeTextToFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
            Charset.defaultCharset(), environmentString, false);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
            return;
        }

        error = FileUtils.moveRegularFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH, TermuxConstants.TERMUX_ENV_FILE_PATH, true);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
        }
    }

    /** Get shell environment for Termux. */
    @NonNull
    @Override
    public HashMap<String, String> getEnvironment(@NonNull Context currentPackageContext, boolean isFailSafe) {

        // Termux environment builds upon the Android environment
        HashMap<String, String> environment = super.getEnvironment(currentPackageContext, isFailSafe);

        HashMap<String, String> termuxAppEnvironment = TermuxAppShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxAppEnvironment != null)
            environment.putAll(termuxAppEnvironment);

        HashMap<String, String> termuxApiAppEnvironment = TermuxAPIShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxApiAppEnvironment != null)
            environment.putAll(termuxApiAppEnvironment);

        environment.put(ENV_HOME, TermuxConstants.TERMUX_HOME_DIR_PATH);
        environment.put(ENV_PREFIX, TermuxConstants.TERMUX_PREFIX_DIR_PATH);

        // The command history of bash is only written to `HISTFILE` when the shell exits, and the app
        // kills the shells with `SIGKILL` when it is closed, so the history of the session would be
        // lost. An interactive bash runs the commands of `PROMPT_COMMAND` before each prompt, so the
        // history is appended to the file after every command instead, which also lets a session see
        // the commands of the other sessions. The variable is set in the environment instead of in
        // `~/.bashrc`, so that the `~/.bashrc` of the user is not modified, and it is not overridden if
        // it is already set, since the user may have exported their own `PROMPT_COMMAND`.
        if (!environment.containsKey(ENV_PROMPT_COMMAND) && shouldSyncShellHistory()) {
            environment.put(ENV_PROMPT_COMMAND, PROMPT_COMMAND_HISTORY_SYNC);
            environment.put(ENV_TERMUX_SHELL_HISTORY_SYNC, "1");
        }

        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that system binaries can be used
        if (!isFailSafe) {
            environment.put(ENV_TMPDIR, TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
            if (TermuxBootstrap.isAppPackageVariantAPTAndroid5()) {
                // Termux in android 5/6 era shipped busybox binaries in applets directory
                environment.put(ENV_PATH, TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/applets");
                environment.put(ENV_LD_LIBRARY_PATH, TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            } else {
                // Termux binaries on Android 7+ rely on DT_RUNPATH, so LD_LIBRARY_PATH should be unset by default
                environment.put(ENV_PATH, TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
                environment.remove(ENV_LD_LIBRARY_PATH);
            }
        }

        return environment;
    }


    @NonNull
    @Override
    public String getDefaultWorkingDirectoryPath() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    /**
     * @return Whether the command history of the shell sessions should be synced, which is the
     *         {@link TermuxPropertyConstants#KEY_SHELL_HISTORY_SYNC} property and is enabled by default.
     *         The properties are {@code null} for an app that did not initialize them, in which case the
     *         default of the property is used.
     */
    private static boolean shouldSyncShellHistory() {
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties == null) return true;

        try {
            return properties.shouldSyncShellHistory();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the \"shell-history-sync\" property", e);
            return true;
        }
    }

    @NonNull
    @Override
    public String getDefaultBinPath() {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    }

    @NonNull
    @Override
    public String[] setupShellCommandArguments(@NonNull String executable, String[] arguments) {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments);
    }

}
