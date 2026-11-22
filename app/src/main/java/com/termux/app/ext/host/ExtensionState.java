package com.termux.app.ext.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Why an extension is or is not running, as shown by the extensions settings page. */
public enum ExtensionState {

    /** The extension is enabled and its dependencies are satisfied. */
    ENABLED,

    /** The extension is enabled by the user but disabled by the host, so that the app stays usable. */
    FORCED_DISABLED,

    /** The user disabled the extension. */
    DISABLED,

    /** The extension is enabled but at least one dependency is not. */
    MISSING_DEPENDENCY,

    /** A dependency cycle was detected; the extension is not started. */
    DEPENDENCY_CYCLE;

    /** Whether the extension should be started in this state. */
    public boolean isRunning() {
        return this == ENABLED;
    }
}
