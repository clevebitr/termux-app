package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.host.ExtensionHost;
import com.termux.app.ext.host.ExtensionState;
import com.termux.app.ext.host.ExtensionStatus;

import java.util.List;

/**
 * The settings page that lists the extensions of the fork and lets the user enable or disable them.
 *
 * <p>The page only reads and writes the stored flags; the host applies them when the settings screen
 * closes and the terminal activity resumes, so that an extension that owns views is started and stopped on
 * a stable point of the activity lifecycle.</p>
 *
 * <p>Disabling an extension that other extensions depend on asks for confirmation first and names the
 * dependents, because those would stop as well.</p>
 */
public class ExtensionsPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        Context context = requireContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);
        populate(screen);
    }

    /** Fill the given screen with the current state of the extensions. */
    private void populate(@NonNull PreferenceScreen screen) {
        Context context = requireContext();
        screen.removeAll();
        ExtensionHost host = ExtensionHost.get(context);
        List<Extension> installed = host.installed();
        if (installed.isEmpty()) {
            Preference empty = new Preference(context);
            empty.setSelectable(false);
            empty.setSummary(R.string.termux_extensions_empty_summary);
            screen.addPreference(empty);
            return;
        }

        PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle(R.string.termux_extensions_category_installed);
        screen.addPreference(category);

        for (ExtensionStatus status : host.statuses()) {
            Extension extension = findById(installed, status.id);
            if (extension == null) continue;
            category.addPreference(createSwitch(context, extension, status, host));
        }
    }

    @NonNull
    private SwitchPreferenceCompat createSwitch(@NonNull Context context,
                                                @NonNull Extension extension,
                                                @NonNull ExtensionStatus status,
                                                @NonNull ExtensionHost host) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(context);
        preference.setKey("extension-" + extension.id());
        preference.setTitle(extension.titleRes());
        preference.setSummary(summaryFor(extension, status));
        preference.setPersistent(false);
        preference.setChecked(status.state.isRunning());
        if (!extension.isUserDisableable() || status.state == ExtensionState.DEPENDENCY_CYCLE) {
            preference.setEnabled(false);
        }
        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            boolean enable = Boolean.TRUE.equals(newValue);
            if (!enable) {
                List<String> dependents = host.dependentsOf(extension.id());
                if (!dependents.isEmpty()) {
                    confirmCascade(context, extension, dependents, host);
                    return false;
                }
            }
            host.setEnabled(extension.id(), enable);
            reload();
            return true;
        });
        return preference;
    }

    private void confirmCascade(@NonNull Context context, @NonNull Extension extension,
                                @NonNull List<String> dependents, @NonNull ExtensionHost host) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_extensions_disable_title)
            .setMessage(getString(R.string.termux_extensions_disable_message,
                getString(extension.titleRes()), dependents.size()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.termux_extensions_disable_confirm, (dialog, which) -> {
                host.setEnabled(extension.id(), false);
                reload();
            })
            .show();
    }

    private void reload() {
        if (isAdded()) {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen != null) populate(screen);
        }
    }

    @NonNull
    private CharSequence summaryFor(@NonNull Extension extension, @NonNull ExtensionStatus status) {
        int stateRes;
        switch (status.state) {
            case ENABLED:
                stateRes = R.string.termux_extensions_state_enabled;
                break;
            case FORCED_DISABLED:
                stateRes = R.string.termux_extensions_state_failed;
                break;
            case MISSING_DEPENDENCY:
                stateRes = R.string.termux_extensions_state_missing_dependency;
                break;
            case DEPENDENCY_CYCLE:
                stateRes = R.string.termux_extensions_state_cycle;
                break;
            case DISABLED:
            default:
                stateRes = R.string.termux_extensions_state_disabled;
                break;
        }
        String summary = getString(extension.summaryRes());
        String state = getString(stateRes);
        if (!extension.isUserDisableable()) {
            state = getString(R.string.termux_extensions_state_core);
        }
        return summary + "\n" + state;
    }

    @Nullable
    private static Extension findById(@NonNull List<Extension> installed, @NonNull String id) {
        for (Extension extension : installed) {
            if (extension.id().equals(id)) return extension;
        }
        return null;
    }
}
