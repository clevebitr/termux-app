package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.PreferenceAccentUtils;
import com.termux.app.fragments.settings.SettingsSearchNavigation;

/**
 * The settings of the terminal context menu and of the floating text selection toolbar, which decide
 * which actions are shown, in which order and with which style.
 *
 * The preferences write the same `terminal-menu-*` and `selection-toolbar-items` properties to the
 * `termux.properties` file as the user can set in the file itself, so both can be mixed freely. The
 * changes are applied to the running activity when the settings screen is closed.
 */
@Keep
public class TerminalMenuPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalMenuPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_terminal_menu_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link TerminalMenuPreferencesFragment}.
 *
 * The menu configuration is reloaded whenever the properties are reloaded, so the activity does not have
 * to be recreated for the changes to take effect.
 */
class TerminalMenuPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static TerminalMenuPreferencesDataStore mInstance;

    private TerminalMenuPreferencesDataStore(Context context) {
        super(context, false);
    }

    public static synchronized TerminalMenuPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalMenuPreferencesDataStore(context);
        }
        return mInstance;
    }

}
