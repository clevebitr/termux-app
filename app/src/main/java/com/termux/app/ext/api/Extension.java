package com.termux.app.ext.api;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Collections;
import java.util.List;

/**
 * A fork feature that can be enabled and disabled by the user.
 *
 * <p>Each feature of the fork lives in its own package under {@code com.termux.app.ext} and implements this
 * interface. The host creates the extension with its no-argument constructor, calls {@link #onEnable} when
 * the feature becomes enabled and {@link #onDisable} when it is disabled or the activity is destroyed, so
 * that an extension must acquire everything it needs in {@code onEnable} and release it in
 * {@code onDisable}.</p>
 *
 * <p>An extension may only use this package, the public API of the read-only library modules and its own
 * package. It must not reach into {@code TermuxActivity} fields or other extensions, otherwise the feature
 * can no longer be disabled cleanly.</p>
 */
public interface Extension {

    /** The stable identifier of the extension, used as the settings key and in dependency declarations. */
    @NonNull
    String id();

    /** The title that the extension list shows. */
    @StringRes
    int titleRes();

    /** The one-line summary that the extension list shows. */
    @StringRes
    int summaryRes();

    /**
     * The identifiers of the extensions that this extension needs.
     *
     * <p>The host enables an extension only when every dependency is enabled, and disabling a dependency
     * also disables the dependents after the user confirms the cascade.</p>
     */
    @NonNull
    default List<String> requires() {
        return Collections.emptyList();
    }

    /**
     * Whether the user may disable the extension.
     *
     * <p>Extensions that provide a part of the shell itself, like the session or terminal view wiring,
     * return {@code false} so that the app cannot be put into a state where it cannot show a terminal.</p>
     */
    default boolean isUserDisableable() {
        return true;
    }

    /**
     * The settings preference keys that this extension provides.
     *
     * <p>The settings screen hides a key while its extension is disabled, so that disabling a feature
     * removes its entry instead of leaving a page that does nothing. Keys are the {@code app:key} values
     * of the preference XML. The key has to be declared on the root settings page, because the visibility
     * pass runs there; a guard test fails when a declared key does not exist in that XML.</p>
     */
    @NonNull
    default List<String> preferenceKeys() {
        return Collections.emptyList();
    }

    /**
     * The regions of the activity layout that this extension takes over while it runs.
     *
     * <p>A declared slot is owned by the extension: while it is disabled the host leaves the region to its
     * default, and no other extension may claim the same view. The declaration also documents which layout
     * region belongs to a feature, so the host does not wire it a second time.</p>
     */
    @NonNull
    default List<ExtensionViewSlot> slots() {
        return Collections.emptyList();
    }

    /**
     * The settings pages that this extension provides.
     *
     * <p>The settings search reads the pages from this list, so that the entries of a disabled extension
     * stop being findable without the search index knowing the extension by name. A page must also refuse to
     * show its actions while its extension is disabled, since hiding the entry is not enough: the page can be
     * opened from a search result that was built before the extension was disabled.</p>
     */
    @NonNull
    default List<ExtensionSettingsPage> settingsPages() {
        return Collections.emptyList();
    }

    /** Whether the extension is enabled before the user changes anything. */
    default boolean isEnabledByDefault() {
        return false;
    }

    /** Called when the extension becomes enabled. */
    void onEnable(@NonNull ExtensionContext context);

    /**
     * Called when the activity that hosts the extension becomes visible.
     *
     * <p>An extension that refreshes something periodically starts its timer here instead of in
     * {@link #onEnable}, which runs before the first frame, so that the work does not delay the frame and
     * does not run while the activity is not visible.</p>
     */
    default void onStart() {
    }

    /** Called when the activity that hosts the extension is no longer visible. Must stop periodic work. */
    default void onStop() {
    }

    /**
     * Called when the configuration that the extension reads may have changed, like after the properties of
     * the app were reloaded or the colors of the terminal changed.
     */
    default void onReload() {
    }

    /** Called when the extension is disabled or the host is destroyed. Must release every resource. */
    void onDisable();
}
