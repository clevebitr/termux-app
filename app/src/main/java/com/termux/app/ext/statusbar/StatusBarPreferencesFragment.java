package com.termux.app.ext.statusbar;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.ext.host.ExtensionHost;
import com.termux.app.fragments.settings.PreferenceAccentUtils;
import com.termux.app.fragments.settings.SettingsSearchNavigation;
import com.termux.app.fragments.settings.termux.PreferenceResetUtils;
import com.termux.app.fragments.settings.termux.TermuxPropertyPreferenceDataStore;

/**
 * The settings of the status bar, which is shown above or below the terminal and displays
 * information like the time, the battery level, the memory and cpu usage, the network traffic and
 * the ip and proxy of the device.
 *
 * The preferences write the same `status-bar-*` properties to the `termux.properties` file as the
 * user can set in the file itself, so both can be mixed freely. The changes are applied to the
 * running activity when the settings screen is closed.
 */
@Keep
public class StatusBarPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        // The page is reachable from a search result or a restored back stack even while the extension is
        // disabled, so it refuses to edit the properties instead of relying on the hidden entry alone.
        if (!ExtensionHost.get(context).isRunning(StatusBarExtension.ID)) {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            Preference notice = new Preference(context);
            notice.setTitle(R.string.termux_extensions_page_disabled_title);
            notice.setSummary(R.string.termux_extensions_page_disabled_summary);
            notice.setSelectable(false);
            screen.addPreference(notice);
            setPreferenceScreen(screen);
            PreferenceAccentUtils.applyAccent(this);
            return;
        }

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(StatusBarPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_status_bar_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link StatusBarPreferencesFragment}.
 *
 * The status bar is rebuilt whenever the properties are reloaded, so the activity does not have to
 * be recreated for the changes to take effect.
 */
class StatusBarPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static StatusBarPreferencesDataStore mInstance;

    private StatusBarPreferencesDataStore(Context context) {
        super(context, false);
    }

    public static synchronized StatusBarPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new StatusBarPreferencesDataStore(context);
        }
        return mInstance;
    }

}
