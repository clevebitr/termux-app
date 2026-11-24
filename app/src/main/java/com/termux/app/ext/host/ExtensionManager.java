package com.termux.app.ext.host;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionLayoutHost;
import com.termux.app.properties.ForkProperties;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Starts and stops the extensions of the fork according to the user's choices.
 *
 * <p>The manager owns the only mapping from an extension id to its instance. It reads the enabled state from
 * {@link ExtensionHost}, starts the extensions that may run (dependencies first), and stops the ones that may
 * not. A failure inside one extension is logged and contained, so that a broken extension cannot
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
    private final ExtensionHost host;
    @NonNull
    private final List<Extension> installed;
    @NonNull
    private final Map<String, Extension> running = new LinkedHashMap<>();
    @NonNull
    private final Map<String, ExtensionState> states = new LinkedHashMap<>();
    @Nullable
    private Properties properties;

    public ExtensionManager(@NonNull Activity activity) {
        this.activity = activity;
        this.host = ExtensionHost.get(activity);
        this.installed = host.installed();
    }

    /** @return The registered extensions, in registration order. */
    @NonNull
    public List<Extension> installed() {
        return installed;
    }

    /** @return The resolved state of every registered extension, dependencies first. */
    @NonNull
    public List<ExtensionStatus> statuses() {
        return host.statuses();
    }

    /** @return Whether the user may disable the extension. */
    public boolean isUserDisableable(@NonNull String id) {
        return host.isUserDisableable(id);
    }

    /** @return The ids of the extensions that would also stop when {@code id} is disabled. */
    @NonNull
    public List<String> dependentsOf(@NonNull String id) {
        return host.dependentsOf(id);
    }

    /** @return Whether the extension is currently running. */
    public boolean isRunning(@NonNull String id) {
        return running.containsKey(id);
    }

    /**
     * @return The state that the manager last applied, or {@code null} if it has not applied yet. Unlike
     *         {@link ExtensionHost#stateOf(String)} this includes {@link ExtensionState#FORCED_DISABLED}, which
     *         the manager sets when an extension throws while starting.
     */
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
        if (!host.setEnabled(id, enabled)) return false;
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

    /** Tell the running extensions that the activity became visible, so that periodic work may start. */
    public void onStart() {
        for (Extension extension : new ArrayList<>(running.values())) {
            try {
                extension.onStart();
            } catch (Throwable throwable) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start the extension: " + extension.id(), throwable);
            }
        }
    }

    /** Tell the running extensions that the activity is no longer visible, so that periodic work must stop. */
    public void onStop() {
        for (Extension extension : new ArrayList<>(running.values())) {
            try {
                extension.onStop();
            } catch (Throwable throwable) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to stop the extension: " + extension.id(), throwable);
            }
        }
    }

    /**
     * Tell the running extensions that the configuration they read may have changed.
     *
     * <p>The properties are re-read first, so that an extension which parses them sees the new values.</p>
     */
    public void onReload() {
        properties = ForkProperties.readTermuxProperties();
        for (Extension extension : new ArrayList<>(running.values())) {
            try {
                extension.onReload();
            } catch (Throwable throwable) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to reload the extension: " + extension.id(), throwable);
            }
        }
    }

    /**
     * Start one extension.
     *
     * <p>The extension is marked as running before {@link Extension#onEnable} is called, because setting itself up
     * may require the host to query it again, like a status bar that takes space from the terminal while its own
     * view is being created. A failure removes it again, so a broken extension never stays in the running set.</p>
     */
    private void start(@NonNull Extension extension) {
        if (running.containsKey(extension.id())) return;

        running.put(extension.id(), extension);
        try {
            extension.onEnable(new Context(extension.id()));
            Logger.logInfo(LOG_TAG, "Started extension: " + extension.id());
        } catch (Throwable throwable) {
            running.remove(extension.id());
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
            return activity.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends View> T view(int viewId) {
            return (T) activity.findViewById(viewId);
        }

        @Override
        public void requestExtensionLayout() {
            if (activity instanceof ExtensionLayoutHost)
                ((ExtensionLayoutHost) activity).requestExtensionLayout();
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
