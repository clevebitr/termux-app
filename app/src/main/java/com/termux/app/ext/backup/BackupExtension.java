package com.termux.app.ext.backup;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;

import java.util.Collections;
import java.util.List;

/**
 * The environment backup and restore feature of the fork.
 *
 * <p>The feature is a settings page plus two helper scripts; it owns no views and no listeners, so
 * enabling it only declares that its settings entry may be shown, and disabling it hides the entry. The
 * scripts stay installed in {@code $PREFIX/bin}, since deleting user installed commands when a settings
 * switch is flipped would be surprising; the page explains how to uninstall them.</p>
 */
public final class BackupExtension implements Extension {

    /** The stable id of the extension, which the settings page and the feature gates use. */
    public static final String ID = "backup";

    /** The preference key of the settings entry that this extension owns. */
    public static final String PREFERENCE_KEY = ID;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_backup_preferences_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_backup_preferences_summary;
    }

    @NonNull
    @Override
    public List<String> preferenceKeys() {
        return Collections.singletonList(PREFERENCE_KEY);
    }

    @NonNull
    @Override
    public List<ExtensionSettingsPage> settingsPages() {
        return Collections.singletonList(new ExtensionSettingsPage(
            R.string.termux_backup_preferences_title, R.xml.termux_backup_preferences,
            BackupPreferencesFragment.class.getName()));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        context.logInfo("Backup entry enabled");
    }

    @Override
    public void onDisable() {
        // Nothing to release: the extension owns no views or listeners.
    }
}
