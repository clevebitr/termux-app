package com.termux.app.ext.api;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Properties;

/**
 * What the host provides to an enabled extension.
 *
 * <p>The context is valid only between {@link Extension#onEnable} and {@link Extension#onDisable}; an
 * extension must not keep a reference to the activity after it has been disabled.</p>
 */
public interface ExtensionContext {

    /** The activity that hosts the terminal. */
    @NonNull
    Activity activity();

    /**
     * The properties of the {@code termux.properties} file, read from disk.
     *
     * <p>Reading the raw properties lets an extension add its own keys without changing the read-only
     * shared library. Never write to the returned object.</p>
     */
    @NonNull
    Properties properties();

    /** The preferences that store the enabled state of the extensions. */
    @NonNull
    SharedPreferences extensionSettings();

    /** Writes an information message to the log, prefixed with the extension id. */
    void logInfo(@NonNull String message);

    /** Writes a warning to the log, prefixed with the extension id. */
    void logWarn(@NonNull String message);
}
