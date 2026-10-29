package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.properties.ForkProperties;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The configuration of the sessions drawer, which is parsed from the `drawer-open-mode` property.
 *
 * On android 10 and later, a swipe from the left edge of the screen is the system back gesture, which
 * takes priority over the edge drag of a {@link androidx.drawerlayout.widget.DrawerLayout}, so the drawer
 * cannot be opened reliably that way anymore. The handle that the app draws on the edge is excluded from
 * the system gestures, and it can also open the drawer with a tap, but the user may prefer the edge swipe,
 * the handle or neither, which this configuration decides.
 */
public final class DrawerConfig {

    private static final String LOG_TAG = "DrawerConfig";

    /** Show the edge handle and keep the edge swipe of the drawer layout. */
    public static final String MODE_AUTO = "auto";
    /** Show the edge handle only, since the system back gesture wins over the edge swipe. */
    public static final String MODE_HANDLE = "handle";
    /** Keep the edge swipe only, like the app did before the handle was added. */
    public static final String MODE_EDGE = "edge";
    /** Neither, so the drawer can only be opened from the menu or the extra keys of the user. */
    public static final String MODE_NONE = "none";

    /** The supported values of the `drawer-open-mode` property. */
    public static final List<String> MODES = Collections.unmodifiableList(Arrays.asList(
        MODE_AUTO, MODE_HANDLE, MODE_EDGE, MODE_NONE));

    public static final String DEFAULT_MODE = MODE_AUTO;

    /** One of {@link #MODES}, which is {@link #DEFAULT_MODE} for an invalid value. */
    @NonNull
    public final String mode;

    public DrawerConfig() {
        this(DEFAULT_MODE);
    }

    public DrawerConfig(@NonNull String mode) {
        this.mode = mode;
    }

    /** @return Whether the handle that opens the drawer on the edge of the screen is shown. */
    public boolean isHandleVisible() {
        return !MODE_EDGE.equals(mode) && !MODE_NONE.equals(mode);
    }

    /**
     * @return Whether the edge swipe of the drawer layout is enabled. It is disabled when only the handle
     *         may open the drawer, since the system back gesture would steal the swipe and close the app
     *         window instead of opening the drawer.
     */
    public boolean isEdgeSwipeEnabled() {
        return !MODE_HANDLE.equals(mode) && !MODE_NONE.equals(mode);
    }

    /**
     * Parse the drawer configuration from the {@code properties} of the `termux.properties` file. An
     * invalid mode falls back to {@link #DEFAULT_MODE} instead of throwing, so that a broken configuration
     * never prevents the app from starting.
     */
    @NonNull
    public static DrawerConfig parse(@Nullable Properties properties) {
        if (properties == null) properties = new Properties();

        String mode = ForkProperties.getString(properties,
            TermuxPropertyConstants.KEY_DRAWER_OPEN_MODE, DEFAULT_MODE).trim().toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            Logger.logWarn(LOG_TAG, "Invalid value \"" + mode + "\" for the \""
                + TermuxPropertyConstants.KEY_DRAWER_OPEN_MODE + "\" property, supported values: "
                + MODES + ", using \"" + DEFAULT_MODE + "\" instead");
            mode = DEFAULT_MODE;
        }

        return new DrawerConfig(mode);
    }
}
