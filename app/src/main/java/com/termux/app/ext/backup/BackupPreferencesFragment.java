package com.termux.app.ext.backup;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.ext.host.ExtensionHost;
import com.termux.app.fragments.settings.PreferenceAccentUtils;
import com.termux.app.fragments.settings.termux.PreferenceResetUtils;
import com.termux.app.fragments.settings.SettingsSearchNavigation;

import java.io.File;

/**
 * The preferences that allow the user to back up and restore the `$PREFIX` and `$HOME` directories
 * with the scripts bundled with the app, like before uninstalling the app when switching to a build
 * that is signed with a different key, since uninstalling the app deletes `/data/data/com.termux/files`.
 *
 * <p>The page is a feature gate as well as a settings page: while the backup extension is disabled it shows
 * a notice instead of the actions, since the page can be reached by paths other than the entry of the
 * settings screen. The scripts that were already installed in `$PREFIX/bin` are left alone, because they are
 * commands of the user; the notice says so.</p>
 */
@Keep
public class BackupPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = requireContext();

        // The page is reachable without the entry of the settings screen, like from a search result that was
        // built before the extension was disabled or from a restored back stack, and the helper scripts stay
        // installed in $PREFIX/bin after the user disables the extension. The feature is therefore refused
        // here instead of relying on the hidden entry alone.
        if (!ExtensionHost.get(context).isRunning(BackupExtension.ID)) {
            showDisabledNotice(context);
            return;
        }

        setPreferencesFromResource(R.xml.termux_backup_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);

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

    /** Replace the page with a notice that explains why it offers nothing, instead of an empty screen. */
    private void showDisabledNotice(@NonNull Context context) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        Preference notice = new Preference(context);
        notice.setTitle(R.string.termux_extensions_page_disabled_title);
        notice.setSummary(R.string.termux_extensions_page_disabled_summary);
        notice.setSelectable(false);
        screen.addPreference(notice);
        setPreferenceScreen(screen);
        PreferenceAccentUtils.applyAccent(this);
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
