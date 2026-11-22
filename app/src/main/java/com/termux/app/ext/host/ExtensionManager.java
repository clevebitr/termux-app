package com.termux.app.ext.host;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.properties.ForkProperties;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Starts and stops the extensions of the fork according to the user's choices.
 *
 * <p>The manager owns the only mapping from an extension id to its instance. It resolves the enabled state
 * with {@link ExtensionGraph}, starts the extensions that may run (dependencies first), and stops the ones
 * that may not. A failure inside one extension is logged and contained, so that a broken extension cannot
 * prevent the app from showing a terminal.</p>
 *
 * <p>Instances are created and released with the activity, since an extension may hold views. The enabled
 * flags live in a dedicated preferences file and survive activity recreation.</p>
 */
public final class ExtensionManager {

    private static final String LOG_TAG = "ExtensionManager";

    /** The preferences file that stores the enabled flags. */
    public static final String PREFERENCES_NAME = "termux-extensions";

    /** The prefix of the enabled flag of an extension. */
    public static final String KEY_ENABLED_PREFIX = "enabled.";

    @NonNull
    private final Activity activity;
    @NonNull
    private final List<Extension> installed;
    @NonNull
    private final SharedPreferences preferences;
    @NonNull
    private final Map<String, Extension> running = new LinkedHashMap<>();
    @NonNull
    private final Map<String, ExtensionState> states = new LinkedHashMap<>();
    @Nullable
    private Properties properties;

    public ExtensionManager(@NonNull Activity activity, @NonNull List<Extension> installed) {
        this.activity = activity;
        this.installed = Collections.unmodifiableList(new ArrayList<>(installed));
        this.preferences = activity.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE);
    }

    /** @return The registered extensions, in registration order. */
    @NonNull
    public List<Extension> installed() {
        return installed;
    }

    /** @return The resolved state of every registered extension, dependencies first. */
    @NonNull
    public List<ExtensionStatus> statuses() {
        return ExtensionGraph.resolve(ExtensionPreferences.specs(installed), ExtensionPreferences.storedFlags(activity, installed));
    }

    /** @return Whether the user may disable the extension. */
    public boolean isUserDisableable(@NonNull String id) {
        for (Extension extension : installed) {
            if (extension.id().equals(id)) return extension.isUserDisableable();
        }
        return false;
    }

    /** @return The ids of the extensions that would also stop when {@code id} is disabled. */
    @NonNull
    public List<String> dependentsOf(@NonNull String id) {
        return ExtensionGraph.dependentsOf(ExtensionPreferences.specs(installed), id);
    }

    /** @return Whether the extension is currently running. */
    public boolean isRunning(@NonNull String id) {
        return running.containsKey(id);
    }

    /** @return The last resolved state of the extension, or {@code null} if it is unknown. */
    @Nullable
    public ExtensionState stateOf(@NonNull String id) {
        return states.get(id);
    }

    /**
     * Store the user's choice and apply it immediately.
     *
     * @return Whether the choice was accepted; extensions that are not user disableable reject it.
     */
    public boolean setEnabled(@NonNull String id, boolean enabled) {
        if (!ExtensionPreferences.setEnabled(activity, id, enabled, isUserDisableable(id))) {
            return false;
        }
        apply();
        return true;
    }

    /**
     * Start and stop extensions so that the running set matches the resolved state.
     *
     * <p>Called on activity creation and after every user change. Extensions that are already running and
     * still allowed stay untouched, so that their views and listeners are not recreated.</p>
     */
    public void apply() {
        properties = ForkProperties.readTermuxProperties();
        Map<String, Extension> byId = new LinkedHashMap<>();
        for (Extension extension : installed) byId.put(extension.id(), extension);

        for (ExtensionStatus status : statuses()) {
            states.put(status.id, status.state);
            Extension extension = byId.get(status.id);
            if (extension == null) continue;
            if (status.state.isRunning()) {
                start(extension);
            } else {
                stop(extension);
            }
        }
        // Stop extensions that are no longer part of the resolved list, like after a cascade disable.
        List<String> stale = new ArrayList<>();
        for (String id : running.keySet()) {
            if (!states.containsKey(id) || states.get(id) == null || !states.get(id).isRunning()) {
                stale.add(id);
            }
        }
        for (String id : stale) {
            Extension extension = byId.get(id);
            if (extension != null) stop(extension);
        }
    }

    /** Stop every running extension. Called when the activity is destroyed. */
    public void destroy() {
        for (String id : new ArrayList<>(running.keySet())) {
            Extension extension = running.get(id);
            if (extension != null) stop(extension);
        }
        properties = null;
    }

    private void start(@NonNull Extension extension) {
        if (running.containsKey(extension.id())) return;
        try {
            extension.onEnable(new Context(extension.id()));
            running.put(extension.id(), extension);
            Logger.logInfo(LOG_TAG, "Started extension: " + extension.id());
        } catch (Throwable throwable) {
            states.put(extension.id(), ExtensionState.FORCED_DISABLED);
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start extension: " + extension.id(), throwable);
        }
    }

    private void stop(@NonNull Extension extension) {
        if (!running.containsKey(extension.id())) return;
        running.remove(extension.id());
        try {
            extension.onDisable();
            Logger.logInfo(LOG_TAG, "Stopped extension: " + extension.id());
        } catch (Throwable throwable) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to stop extension: " + extension.id(), throwable);
        }
    }

    /** The context that the extension sees; it never exposes the manager itself. */
    private final class Context implements ExtensionContext {

        @NonNull
        private final String extensionId;

        Context(@NonNull String extensionId) {
            this.extensionId = extensionId;
        }

        @NonNull
        @Override
        public Activity activity() {
            return activity;
        }

        @NonNull
        @Override
        public Properties properties() {
            return properties != null ? properties : new Properties();
        }

        @NonNull
        @Override
        public SharedPreferences extensionSettings() {
            return preferences;
        }

        @Override
        public void logInfo(@NonNull String message) {
            Logger.logInfo(LOG_TAG, "[" + extensionId + "] " + message);
        }

        @Override
        public void logWarn(@NonNull String message) {
            Logger.logWarn(LOG_TAG, "[" + extensionId + "] " + message);
        }
    }
}
