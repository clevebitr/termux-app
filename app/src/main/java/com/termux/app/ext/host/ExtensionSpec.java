package com.termux.app.ext.host;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * The part of an {@link com.termux.app.ext.api.Extension} that decides whether it can run.
 *
 * <p>The host resolves the enabled state from these values without creating the extension, so that a
 * disabled extension never allocates anything. The graph logic is kept free of Android types so that it can
 * be tested on the JVM.</p>
 */
public final class ExtensionSpec {

    @NonNull
    public final String id;
    @NonNull
    public final List<String> requires;
    public final boolean userDisableable;
    public final boolean enabledByDefault;

    public ExtensionSpec(@NonNull String id, @NonNull List<String> requires,
                         boolean userDisableable, boolean enabledByDefault) {
        this.id = id;
        this.requires = Collections.unmodifiableList(requires);
        this.userDisableable = userDisableable;
        this.enabledByDefault = enabledByDefault;
    }

    public ExtensionSpec(@NonNull String id, boolean userDisableable, boolean enabledByDefault) {
        this(id, Collections.emptyList(), userDisableable, enabledByDefault);
    }
}
