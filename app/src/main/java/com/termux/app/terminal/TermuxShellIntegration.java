package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Install and uninstall the shell integration that makes the shell report the start of prompts
 * (`OSC 133;A`), the start of commands (`OSC 133;C`) and the exit code of finished commands
 * (`OSC 133;D;<code>`), which the terminal uses to jump between commands, to draw a command mark on
 * the right edge and to show when a command failed.
 *
 * The scripts are written to `~/.termux/shell-integration.{bash,zsh}` and sourced from a block with
 * markers that is appended to `~/.bashrc` and, if it exists, `~/.zshrc`. The block can be removed
 * again with {@link #uninstall(Context)}, which leaves the rest of the file untouched.
 */
public final class TermuxShellIntegration {

    private static final String LOG_TAG = "TermuxShellIntegration";

    public static final String BASH_SCRIPT_FILE_NAME = "shell-integration.bash";
    public static final String ZSH_SCRIPT_FILE_NAME = "shell-integration.zsh";

    public static final String BASH_RC_FILE_NAME = ".bashrc";
    public static final String ZSH_RC_FILE_NAME = ".zshrc";

    /** The first line of the block that is added to the rc file, which is also used to detect if the integration is installed. */
    static final String MARKER_BEGIN = "# >>> termux-shell-integration >>>";
    /** The last line of the block that is added to the rc file. */
    static final String MARKER_END = "# <<< termux-shell-integration <<<";

    /**
     * The version of the installed scripts, which is written into them so that an outdated install
     * can be detected. Version 2 added the command line to the `OSC 133 ; D` mark, which the
     * notification cards of failed commands show.
     */
    static final int SCRIPT_VERSION = 2;

    /** The marker in the scripts that stores {@link #SCRIPT_VERSION}. */
    static final String VERSION_MARKER = "__TERMUX_OSC133_VERSION=";

    private TermuxShellIntegration() {
    }

    /** Returns the path of the script file in the `~/.termux` directory. */
    @NonNull
    public static File getScriptFile(@NonNull String scriptFileName) {
        return new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH, scriptFileName);
    }

    /** Returns the path of the rc file in the home directory, like `~/.bashrc`. */
    @NonNull
    private static File getRcFile(@NonNull String rcFileName) {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, rcFileName);
    }

    /** Returns whether the shell integration is installed in one of the rc files. */
    public static boolean isInstalled(@NonNull Context context) {
        return containsBlock(readTextFile(getRcFile(BASH_RC_FILE_NAME))) ||
            containsBlock(readTextFile(getRcFile(ZSH_RC_FILE_NAME)));
    }

    /**
     * Returns whether the installed scripts are older than the ones of the app, which is the case
     * after an app update that changed them, like when the command line was added to the `OSC 133;D`
     * mark. Reinstalling the integration writes the new scripts.
     */
    public static boolean isInstalledVersionOutdated() {
        String script = readTextFile(getScriptFile(BASH_SCRIPT_FILE_NAME));
        if (script == null || script.isEmpty()) return false;
        return !script.contains(VERSION_MARKER + SCRIPT_VERSION);
    }

    /**
     * Install the shell integration by extracting the scripts and adding the sourcing block to the
     * rc files. The block is not added again if it already exists.
     *
     * @return Whether the integration was installed. Changes only take effect for new shell
     * sessions, or after sourcing the rc file again.
     */
    public static boolean install(@NonNull Context context) {
        File configDir = new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);
        if (!configDir.isDirectory() && !configDir.mkdirs()) {
            Logger.logError(LOG_TAG, "Failed to create the \"" + configDir.getAbsolutePath() + "\" directory");
            return false;
        }

        if (!extractScript(context, R.raw.termux_shell_integration_bash, BASH_SCRIPT_FILE_NAME))
            return false;
        if (!extractScript(context, R.raw.termux_shell_integration_zsh, ZSH_SCRIPT_FILE_NAME))
            return false;

        // The bash script is always installed, since bash is the default shell of termux. The zsh
        // script is only sourced if the user already has a zsh rc file, so that one is not created
        // for users that do not use zsh.
        if (!addBlock(getRcFile(BASH_RC_FILE_NAME), BASH_SCRIPT_FILE_NAME)) return false;

        File zshRcFile = getRcFile(ZSH_RC_FILE_NAME);
        if (zshRcFile.exists() && !addBlock(zshRcFile, ZSH_SCRIPT_FILE_NAME)) return false;

        Logger.logInfo(LOG_TAG, "Installed the shell integration");
        return true;
    }

    /**
     * Remove the sourcing block from the rc files and delete the scripts. The rest of the rc files
     * is left untouched.
     *
     * @return Whether the integration was uninstalled.
     */
    public static boolean uninstall(@NonNull Context context) {
        boolean success = removeBlock(getRcFile(BASH_RC_FILE_NAME));
        success = removeBlock(getRcFile(ZSH_RC_FILE_NAME)) && success;

        // Failures to delete the scripts are not fatal, since the rc files no longer reference them.
        deleteFile(getScriptFile(BASH_SCRIPT_FILE_NAME));
        deleteFile(getScriptFile(ZSH_SCRIPT_FILE_NAME));

        Logger.logInfo(LOG_TAG, "Uninstalled the shell integration");
        return success;
    }

    private static boolean containsBlock(@Nullable String contents) {
        return contents != null && contents.contains(MARKER_BEGIN);
    }

    /** Add the block that sources the script of {@code scriptFileName} to {@code rcFile}. */
    private static boolean addBlock(@NonNull File rcFile, @NonNull String scriptFileName) {
        String contents = readTextFile(rcFile);
        if (contents == null) contents = "";
        if (contents.contains(MARKER_BEGIN)) return true;

        if (!contents.isEmpty() && !contents.endsWith("\n")) contents += "\n";
        contents += MARKER_BEGIN + "\n" +
            "# Added by Termux. Remove it with Settings -> Termux -> Terminal View -> Uninstall shell integration.\n" +
            "[ -f \"$HOME/.termux/" + scriptFileName + "\" ] && . \"$HOME/.termux/" + scriptFileName + "\"\n" +
            MARKER_END + "\n";

        if (!writeTextFile(rcFile, contents)) {
            Logger.logError(LOG_TAG, "Failed to write the shell integration block to \"" + rcFile.getAbsolutePath() + "\"");
            return false;
        }
        return true;
    }

    /** Remove the block added by {@link #addBlock(File, String)} from {@code rcFile}, if it exists. */
    private static boolean removeBlock(@NonNull File rcFile) {
        String contents = readTextFile(rcFile);
        if (contents == null || !contents.contains(MARKER_BEGIN)) return true;

        StringBuilder newContents = new StringBuilder(contents.length());
        boolean inBlock = false;
        boolean firstLine = true;
        for (String line : contents.split("\n", -1)) {
            if (line.trim().equals(MARKER_BEGIN)) {
                inBlock = true;
                continue;
            }
            if (line.trim().equals(MARKER_END)) {
                inBlock = false;
                continue;
            }
            if (inBlock) continue;
            if (!firstLine) newContents.append('\n');
            newContents.append(line);
            firstLine = false;
        }

        if (!writeTextFile(rcFile, newContents.toString())) {
            Logger.logError(LOG_TAG, "Failed to remove the shell integration block from \"" + rcFile.getAbsolutePath() + "\"");
            return false;
        }
        return true;
    }

    /** Read the raw resource {@code resId} and write it to the script file. */
    private static boolean extractScript(@NonNull Context context, int resId, @NonNull String scriptFileName) {
        File scriptFile = getScriptFile(scriptFileName);

        String scriptContents = null;
        try (InputStream inputStream = context.getResources().openRawResource(resId)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) > 0) outputStream.write(buffer, 0, read);
            scriptContents = outputStream.toString("UTF-8");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the \"" + scriptFileName + "\" script from the app resources", e);
        }

        if (scriptContents == null || scriptContents.isEmpty()) {
            Logger.logError(LOG_TAG, "The \"" + scriptFileName + "\" script read from the app resources is empty");
            return false;
        }

        if (!writeTextFile(scriptFile, scriptContents)) {
            Logger.logError(LOG_TAG, "Failed to write the \"" + scriptFileName + "\" script to \"" + scriptFile.getAbsolutePath() + "\"");
            return false;
        }
        return true;
    }

    @Nullable
    private static String readTextFile(@NonNull File file) {
        if (!file.exists() || !file.isFile()) return null;

        StringBuilder contents = new StringBuilder();
        if (FileUtils.readTextFromFile(file.getName(), file.getAbsolutePath(), Charset.defaultCharset(), contents, true) != null)
            return null;
        return contents.toString();
    }

    private static boolean writeTextFile(@NonNull File file, @NonNull String contents) {
        return FileUtils.writeTextToFile(file.getName(), file.getAbsolutePath(), Charset.defaultCharset(), contents, false) == null;
    }

    private static void deleteFile(@NonNull File file) {
        if (file.exists() && !file.delete())
            Logger.logWarn(LOG_TAG, "Failed to delete \"" + file.getAbsolutePath() + "\"");
    }

}
