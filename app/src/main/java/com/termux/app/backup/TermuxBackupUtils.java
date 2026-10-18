package com.termux.app.backup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * The utils for the `termux-backup` and `termux-restore` scripts that are bundled with the app, which
 * allow the user to back up and restore the `$PREFIX` and `$HOME` directories, like before
 * uninstalling the app when switching to a build that is signed with a different key.
 *
 * The scripts are bundled as raw resources so that they stay in sync with the app, and are extracted
 * to the `$PREFIX/tmp` directory before they are run in a new terminal session, so that the user can
 * see their output. They can also be installed in the `$PREFIX/bin` directory, so that they can be
 * run as commands from the terminal at any time.
 */
public class TermuxBackupUtils {

    /** The name of the script that creates a backup, without the extension. */
    public static final String SCRIPT_NAME_BACKUP = "termux-backup";
    /** The name of the script that restores a backup, without the extension. */
    public static final String SCRIPT_NAME_RESTORE = "termux-restore";

    /** The raw resource that contains the backup script. */
    public static final int SCRIPT_RES_ID_BACKUP = R.raw.termux_backup;
    /** The raw resource that contains the restore script. */
    public static final int SCRIPT_RES_ID_RESTORE = R.raw.termux_restore;

    private static final String LOG_TAG = "TermuxBackupUtils";

    /** Get the path of the script in the `$PREFIX/tmp` directory that it is extracted to. */
    @NonNull
    public static String getExtractedScriptPath(@NonNull String scriptName) {
        return TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/" + scriptName;
    }

    /** Get the path of the script in the `$PREFIX/bin` directory that it can be installed to. */
    @NonNull
    public static String getInstalledScriptPath(@NonNull String scriptName) {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + scriptName;
    }

    /**
     * Read a bundled script from the raw resources and extract it to the `$PREFIX/tmp` directory.
     *
     * @param context The {@link Context} for operations.
     * @param scriptResId The raw resource id of the script, like {@link #SCRIPT_RES_ID_BACKUP}.
     * @param scriptName The name of the script, like {@link #SCRIPT_NAME_BACKUP}.
     * @return Returns the extracted {@link File}, otherwise {@code null} if it failed to be extracted.
     */
    @Nullable
    public static File extractScript(@NonNull Context context, int scriptResId, @NonNull String scriptName) {
        String scriptContents;
        try (InputStream inputStream = context.getResources().openRawResource(scriptResId)) {
            scriptContents = readStringFromInputStream(inputStream);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the \"" + scriptName + "\" script from the app resources", e);
            return null;
        }

        if (scriptContents == null || scriptContents.isEmpty()) {
            Logger.logError(LOG_TAG, "The \"" + scriptName + "\" script read from the app resources is empty");
            return null;
        }

        File scriptFile = new File(getExtractedScriptPath(scriptName));

        Error error = FileUtils.writeTextToFile("script", scriptFile.getAbsolutePath(),
            Charset.defaultCharset(), scriptContents, false);
        if (error != null) {
            Logger.logError(LOG_TAG, "Failed to extract the \"" + scriptName + "\" script to \"" + scriptFile.getAbsolutePath() + "\": " + error.getMessage());
            return null;
        }

        // The script is run with `bash`, so its executable permissions are not required for it to
        // run, but they are set anyway so that it can also be run directly.
        if (!scriptFile.setExecutable(true, true) || !scriptFile.setReadable(true, true) || !scriptFile.setWritable(true, true)) {
            Logger.logWarn(LOG_TAG, "Failed to set the permissions of the \"" + scriptFile.getAbsolutePath() + "\" script");
        }

        Logger.logVerbose(LOG_TAG, "Extracted the \"" + scriptName + "\" script to \"" + scriptFile.getAbsolutePath() + "\"");
        return scriptFile;
    }

    /**
     * Extract a bundled script to the `$PREFIX/bin` directory, so that it can be run as a command
     * from the terminal at any time, like `termux-backup`.
     *
     * @param context The {@link Context} for operations.
     * @param scriptResId The raw resource id of the script, like {@link #SCRIPT_RES_ID_BACKUP}.
     * @param scriptName The name of the script, like {@link #SCRIPT_NAME_BACKUP}.
     * @return Returns {@code true} if the script was successfully installed, otherwise {@code false}.
     */
    public static boolean installScriptAsCommand(@NonNull Context context, int scriptResId, @NonNull String scriptName) {
        File extractedScriptFile = extractScript(context, scriptResId, scriptName);
        if (extractedScriptFile == null) return false;

        File installedScriptFile = new File(getInstalledScriptPath(scriptName));
        if (installedScriptFile.exists() && !installedScriptFile.delete()) {
            Logger.logError(LOG_TAG, "Failed to delete the existing \"" + installedScriptFile.getAbsolutePath() + "\" script");
            return false;
        }

        Error error = FileUtils.copyFile("script", extractedScriptFile.getAbsolutePath(),
            installedScriptFile.getAbsolutePath(), false);
        if (error != null) {
            Logger.logError(LOG_TAG, "Failed to install the \"" + scriptName + "\" script to \"" + installedScriptFile.getAbsolutePath() + "\": " + error.getMessage());
            return false;
        }

        if (!installedScriptFile.setExecutable(true, true) || !installedScriptFile.setReadable(true, true) || !installedScriptFile.setWritable(true, true)) {
            Logger.logError(LOG_TAG, "Failed to set the permissions of the \"" + installedScriptFile.getAbsolutePath() + "\" script");
            return false;
        }

        Logger.logVerbose(LOG_TAG, "Installed the \"" + scriptName + "\" script to \"" + installedScriptFile.getAbsolutePath() + "\"");
        return true;
    }

    /**
     * Run a script in a new terminal session, so that the user can see its output and interact with
     * it, like to confirm the restore of a backup.
     *
     * @param context The {@link Context} for operations.
     * @param scriptFile The {@link File} of the script to run, like the one returned by
     *                   {@link #extractScript(Context, int, String)}.
     * @param arguments The arguments to pass to the script.
     * @return Returns {@code true} if the command was successfully sent to the {@link TermuxService},
     *         otherwise {@code false}.
     */
    public static boolean runScriptInNewTerminalSession(@NonNull Context context, @NonNull File scriptFile,
                                                        @Nullable String... arguments) {
        String bashPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";

        int argumentsCount = arguments == null ? 0 : arguments.length;
        String[] commandArguments = new String[argumentsCount + 1];
        commandArguments[0] = scriptFile.getAbsolutePath();
        if (argumentsCount > 0)
            System.arraycopy(arguments, 0, commandArguments, 1, argumentsCount);

        // Create the execution intent with the action TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, the
        // same way the RunCommandService does, but without going through it so that the
        // `allow-external-apps` policy check is not applied to the app itself.
        Intent executionIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE,
            new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(bashPath).build());
        executionIntent.setClass(context, TermuxService.class);
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, commandArguments);
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, ExecutionCommand.Runner.TERMINAL_SESSION.getName());
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, scriptFile.getName());
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, scriptFile.getName());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(executionIntent);
            } else {
                context.startService(executionIntent);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to run the \"" + scriptFile.getAbsolutePath() + "\" script", e);
            return false;
        }

        return true;
    }

    /** Read the contents of an {@link InputStream} into a {@link String}. */
    @Nullable
    private static String readStringFromInputStream(@NonNull InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int readBytes;
        while ((readBytes = inputStream.read(buffer)) >= 0) {
            byteArrayOutputStream.write(buffer, 0, readBytes);
        }

        return byteArrayOutputStream.toString(Charset.defaultCharset().name());
    }

}
