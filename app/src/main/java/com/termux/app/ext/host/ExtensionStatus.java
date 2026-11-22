package com.termux.app.ext.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** The resolved state of one extension, used by the settings page and by the host. */
public final class ExtensionStatus {

    @NonNull
    public final String id;
    @NonNull
    public final List<String> requires;
    public final boolean userDisableable;
    @NonNull
    public final ExtensionState state;
    @Nullable
    public final String missingDependency;

    ExtensionStatus(@NonNull String id, @NonNull List<String> requires, boolean userDisableable,
                    @NonNull ExtensionState state, @Nullable String missingDependency) {
        this.id = id;
        this.requires = Collections.unmodifiableList(requires);
        this.userDisableable = userDisableable;
        this.state = state;
        this.missingDependency = missingDependency;
    }
}
