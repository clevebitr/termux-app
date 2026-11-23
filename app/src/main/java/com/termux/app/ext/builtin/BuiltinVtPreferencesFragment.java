package com.termux.app.ext.builtin;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
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
 * 目的 — 集中展示只能开关行为、不能移除实现的核心终端能力。
 *
 * <p>行为 — 页面上的开关写入 `termux.properties`：OSC 52 剪贴板策略、滚动位置保持、鼠标悬停上报与终端内边距。
 * 这些行为由只读模块实现，应用只能改变其取值，因此本页不提供「停用」能力，只提供关闭行为。</p>
 *
 * <p>约束 — 页面必须由 {@link BuiltinVtExtension} 声明并保持不可禁用；不得把可关闭的 fork 功能放进本页，
 * 那些功能属于各自的扩展。</p>
 */
@Keep
public class BuiltinVtPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        // The extension cannot be disabled by the user, so this branch is not reachable today; it keeps the page
        // uniform with the pages of the disableable extensions, so that making the extension disableable later
        // cannot leave a page that edits the properties of a capability the host no longer provides.
        if (!ExtensionHost.get(context).isRunning(BuiltinVtExtension.ID)) {
            showDisabledNotice(context);
            return;
        }

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(BuiltinVtPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_builtin_vt_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);
    }

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
}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the terminal behaviours on this page, which are read whenever
 * the terminal view is created or reloaded, so the activity does not have to be recreated for a change to take
 * effect.
 */
class BuiltinVtPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static BuiltinVtPreferencesDataStore mInstance;

    private BuiltinVtPreferencesDataStore(Context context) {
        super(context, false);
    }

    static synchronized BuiltinVtPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BuiltinVtPreferencesDataStore(context);
        }
        return mInstance;
    }
}
