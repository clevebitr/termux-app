package com.termux.app.fragments.settings.termux;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.app.backup.TermuxBackupUtils;

import java.io.File;

/**
 * The preferences that allow the user to back up and restore the `$PREFIX` and `$HOME` directories
 * with the scripts bundled with the app, like before uninstalling the app when switching to a build
 * that is signed with a different key, since uninstalling the app deletes `/data/data/com.termux/files`.
 */
@Keep
public class BackupPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.termux_backup_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        Context context = getContext();
        if (context == null) return;

        Preference backupPreference = findPreference("backup_now");
        if (backupPreference != null)
            backupPreference.setOnPreferenceClickListener(preference -> {
                startBackup(context);
                return true;
            });

        Preference restorePreference = findPreference("restore_now");
        if (restorePreference != null)
            restorePreference.setOnPreferenceClickListener(preference -> {
                showRestoreConfirmationDialog(context);
                return true;
            });

        Preference installCommandsPreference = findPreference("install_commands");
        if (installCommandsPreference != null)
            installCommandsPreference.setOnPreferenceClickListener(preference -> {
                installCommands(context);
                return true;
            });
    }

    /** Extract the backup script and run it in a new terminal session. */
    private void startBackup(Context context) {
        if (runScript(context, TermuxBackupUtils.SCRIPT_RES_ID_BACKUP, TermuxBackupUtils.SCRIPT_NAME_BACKUP))
            showToast(context, context.getString(R.string.termux_backup_started_toast));
    }

    /**
     * Extract the restore script and run it in a new terminal session. The script itself lists the
     * backups found on the device, asks the user to pick one and asks for confirmation before
     * overwriting the current environment.
     */
    private void startRestore(Context context) {
        if (runScript(context, TermuxBackupUtils.SCRIPT_RES_ID_RESTORE, TermuxBackupUtils.SCRIPT_NAME_RESTORE))
            showToast(context, context.getString(R.string.termux_restore_started_toast));
    }

    private boolean runScript(Context context, int scriptResId, String scriptName) {
        File scriptFile = TermuxBackupUtils.extractScript(context, scriptResId, scriptName);
        if (scriptFile == null) {
            showToast(context, context.getString(R.string.termux_backup_script_extract_failed_toast, scriptName));
            return false;
        }

        if (!TermuxBackupUtils.runScriptInNewTerminalSession(context, scriptFile)) {
            showToast(context, context.getString(R.string.termux_backup_script_run_failed_toast, scriptName));
            return false;
        }

        return true;
    }

    private void installCommands(Context context) {
        boolean backupInstalled = TermuxBackupUtils.installScriptAsCommand(context,
            TermuxBackupUtils.SCRIPT_RES_ID_BACKUP, TermuxBackupUtils.SCRIPT_NAME_BACKUP);
        boolean restoreInstalled = TermuxBackupUtils.installScriptAsCommand(context,
            TermuxBackupUtils.SCRIPT_RES_ID_RESTORE, TermuxBackupUtils.SCRIPT_NAME_RESTORE);

        if (backupInstalled && restoreInstalled) {
            showToast(context, context.getString(R.string.termux_backup_commands_installed_toast,
                TermuxBackupUtils.SCRIPT_NAME_BACKUP, TermuxBackupUtils.SCRIPT_NAME_RESTORE));
        } else {
            showToast(context, context.getString(R.string.termux_backup_commands_install_failed_toast));
        }
    }

    private void showRestoreConfirmationDialog(Context context) {
        new AlertDialog.Builder(context)
            .setTitle(R.string.termux_restore_confirm_title)
            .setMessage(R.string.termux_restore_confirm_message)
            .setPositiveButton(R.string.termux_restore_confirm_positive, (dialog, which) -> startRestore(context))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

}
