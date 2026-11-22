package com.termux.app.ext;

import androidx.annotation.NonNull;

import com.termux.app.ext.api.Extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The extensions that this build contains.
 *
 * <p>Every fork feature that was extracted into an extension is registered here, in the order it should be
 * started when there are no dependencies between two of them. Adding a feature means adding one line here
 * and a package under {@code com.termux.app.ext}; removing a feature means removing that line, so the app
 * always compiles against the set of extensions that this file lists.</p>
 *
 * <p>The list is intentionally empty until the first feature is extracted; the extension settings page
 * shows an explanation in that state.</p>
 */
public final class ForkExtensions {

    private ForkExtensions() {
    }

    /** @return The extensions of this build, in start order between independent extensions. */
    @NonNull
    public static List<Extension> installed() {
        List<Extension> extensions = new ArrayList<>();
        // 迁移一个 fork 功能时在此登记，例如：extensions.add(new StatusBarExtension());
        return Collections.unmodifiableList(extensions);
    }
}
