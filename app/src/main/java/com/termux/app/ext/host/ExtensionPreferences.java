package com.termux.app.ext.host;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.termux.app.ext.api.Extension;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stored enabled flags of the extensions.
 *
 * <p>Kept separate from {@link ExtensionHost} so that the settings page can read and write the flags
 * without starting or stopping anything; the host resolves the flags and applies them when the activity
 * resumes. This class only describes and stores preferences, it never decides whether a feature runs.</p>
 */
public final class ExtensionPreferences {

    private ExtensionPreferences() {
    }

    /** @return The stored flags of the given extensions, keyed by id; unset extensions are absent. */
    @NonNull
    public static Map<String, Boolean> storedFlags(@NonNull Context context,
                                                   @NonNull List<Extension> extensions) {
        SharedPreferences preferences =
            context.getSharedPreferences(ExtensionManager.PREFERENCES_NAME, Context.MODE_PRIVATE);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Extension extension : extensions) {
            String key = ExtensionManager.KEY_ENABLED_PREFIX + extension.id();
            if (preferences.contains(key)) {
                flags.put(extension.id(), preferences.getBoolean(key, false));
            }
        }
        return flags;
    }

    /** Store the user's choice for one extension. Extensions that cannot be disabled reject it. */
    public static boolean setEnabled(@NonNull Context context, @NonNull String id, boolean enabled,
                                     boolean userDisableable) {
        if (!userDisableable && !enabled) return false;
        context.getSharedPreferences(ExtensionManager.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ExtensionManager.KEY_ENABLED_PREFIX + id, enabled)
            .apply();
        return true;
    }

    /** @return The specs of the given extensions, in registration order. */
    @NonNull
    public static List<ExtensionSpec> specs(@NonNull List<Extension> extensions) {
        List<ExtensionSpec> specs = new ArrayList<>(extensions.size());
        for (Extension extension : extensions) {
            specs.add(new ExtensionSpec(extension.id(), extension.requires(),
                extension.isUserDisableable(), extension.isEnabledByDefault()));
        }
        return specs;
    }

    /** @return The ids of the extensions that depend on {@code id}, directly or transitively. */
    @NonNull
    public static List<String> dependentsOf(@NonNull List<Extension> extensions, @NonNull String id) {
        return ExtensionGraph.dependentsOf(specs(extensions), id);
    }

    /**
     * Hide the settings entries that belong to extensions which are not running.
     *
     * <p>Called after a settings page is inflated, so that disabling an extension removes its entry instead
     * of showing a page that does nothing. Hiding is a hint for the user rather than the enforcement of the
     * state: the page of an extension also refuses to work while its extension is disabled, since the entry
     * is not the only path to the page.</p>
     */
    public static void applyPreferenceVisibility(@NonNull Context context,
                                                 @NonNull PreferenceScreen screen) {
        ExtensionHost host = ExtensionHost.get(context);
        for (String id : host.disabledIds()) {
            Extension extension = host.extension(id);
            if (extension == null) continue;
            for (String key : extension.preferenceKeys()) {
                Preference preference = screen.findPreference(key);
                if (preference != null) preference.setVisible(false);
            }
        }
    }

    /**
     * @return The resolved state of the given extensions, dependencies first. Kept for the host and for a
     *         caller that resolves a list other than the registered one; feature code reads
     *         {@link ExtensionHost} instead.
     */
    @NonNull
    public static List<ExtensionStatus> resolve(@NonNull Context context,
                                                @NonNull List<Extension> extensions) {
        return ExtensionGraph.resolve(specs(extensions), storedFlags(context, extensions));
    }
}
