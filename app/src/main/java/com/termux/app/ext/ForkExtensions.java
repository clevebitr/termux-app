package com.termux.app.ext;

import androidx.annotation.NonNull;

import com.termux.app.ext.api.Extension;
import com.termux.app.ext.backup.BackupExtension;
import com.termux.app.ext.menu.TerminalMenuExtension;
import com.termux.app.ext.notifications.NotificationsExtension;
import com.termux.app.ext.search.SearchExtension;
import com.termux.app.ext.statusbar.StatusBarExtension;
import com.termux.app.ext.volumekeys.VolumeKeysExtension;

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
 * <p>The backup feature is the first extracted extension; the extension settings page shows the rest of
 * the list when they are migrated.</p>
 */
public final class ForkExtensions {

    private ForkExtensions() {
    }

    /** @return The extensions of this build, in start order between independent extensions. */
    @NonNull
    public static List<Extension> installed() {
        List<Extension> extensions = new ArrayList<>();
        // The status bar is started first, since the other extensions draw around the terminal and may need to
        // know whether it takes space from it.
        extensions.add(new StatusBarExtension());
        extensions.add(new TerminalMenuExtension());
        extensions.add(new NotificationsExtension());
        extensions.add(new SearchExtension());
        extensions.add(new VolumeKeysExtension());
        extensions.add(new BackupExtension());
        return Collections.unmodifiableList(extensions);
    }
}
