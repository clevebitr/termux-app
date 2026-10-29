package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.notifications.NotificationConfig;
import com.termux.app.properties.ForkProperties;
import com.termux.app.notifications.NotificationOverlayView;
import com.termux.app.notifications.RecentNotifications;
import com.termux.app.notifications.RecentNotificationsDialog;
import com.termux.app.terminal.menu.TerminalMenuAction;
import com.termux.app.terminal.menu.TerminalMenuConfig;
import com.termux.app.terminal.menu.TerminalMenuDialog;
import com.termux.app.notifications.TerminalNotificationManager;
import com.termux.app.statusbar.StatusBarConfig;
import com.termux.app.statusbar.StatusBarController;
import com.termux.app.statusbar.StatusBarView;
import com.termux.app.terminal.TerminalSearchController;
import com.termux.app.terminal.TerminalSplitScreenController;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalNotification;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /** The search bar of the terminal, which is overlayed on top of the terminal view. */
    private TerminalSearchController mTerminalSearchController;

    /** The layout of the split screen, which owns the sizes and the orientation of the panes. */
    private TerminalSplitScreenController mTerminalSplitScreenController;

    /**
     * The containers of the terminal panes. Index 0 is the primary pane, which is always shown, and
     * index 1 is the secondary pane, which is only shown when split screen is enabled.
     */
    private final FrameLayout[] mTerminalPaneContainers = new FrameLayout[2];

    /**
     * The {@link TerminalView} of each pane. The view of the active pane is {@link #mTerminalView},
     * which most of the activity operates on, so that the code that was written for a single
     * terminal view keeps working with split screen.
     */
    private final TerminalView[] mTerminalViews = new TerminalView[2];

    /**
     * The {@link TermuxTerminalViewClient} of each pane. Each view has its own client, since the
     * callbacks of a view must act on the view that received the event, not on the active pane.
     * The client of the active pane is {@link #mTermuxTerminalViewClient}.
     */
    private final TermuxTerminalViewClient[] mTermuxTerminalViewClients = new TermuxTerminalViewClient[2];

    /** The index of the pane that is active, i.e. that receives the keyboard, extra keys and menus. */
    private int mActiveTerminalViewIndex = 0;

    /** The status bar that shows the time, battery, memory, cpu, network, ip and proxy segments. */
    private StatusBarView mStatusBarView;
    /** The controller that refreshes the segments of the {@link #mStatusBarView}. */
    private StatusBarController mStatusBarController;

    /**
     * The overlay that shows the notification cards, like for a command that exited with a non-zero
     * exit code, in a corner of the terminal.
     */
    private NotificationOverlayView mNotificationOverlay;
    /** The manager that reads the `notification-*` properties and decides which cards are shown. */
    private TerminalNotificationManager mNotificationManager;
    /** The number of test notifications that were sent, so that each one shows a different kind. */
    private int mTestNotificationCount;

    /** The configuration of the terminal context menu and of the text selection toolbar. */
    private TerminalMenuConfig mTerminalMenuConfig = new TerminalMenuConfig();

    /**
     * The terminal extra keys view.
     */
    /**
     * The {@link ExtraKeysView} of each extra keys panel that has been created by the terminal toolbar
     * view pager, since multiple panels can be defined with the `extra-keys-panels` property and swiped
     * between. The views are removed when the pager destroys them, like when the panel count changes.
     */
    final List<ExtraKeysView> mExtraKeysViews = new ArrayList<>();

    /**
     * The client for the {@link #mExtraKeysViews extra keys views}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * Whether a {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE} broadcast was received while the activity
     * was not visible, and its styling therefore still has to be reloaded. The settings activities are
     * launched in front of this activity and change the termux properties while it is stopped, in which
     * case the styling is reloaded in {@link #onStart()} instead, since reloading it while the activity
     * is not visible is not safe.
     */
    private boolean mReloadActivityStylingOnStart;

    /**
     * Whether the activity must be recreated when {@link #mReloadActivityStylingOnStart} is handled,
     * since some properties are only applied when the activity is created, like the `fullscreen` and
     * `extra-keys-style` properties.
     */
    private boolean mRecreateActivityOnStart;

    /**
     * Whether the soft keyboard was last reported as visible, or {@code null} if it has not been
     * determined yet. See {@link #updateTerminalToolbarVisibilityForSoftKeyboardVisibility()}.
     */
    private Boolean mIsSoftKeyboardVisibleForToolbar;

    /**
     * Whether the terminal toolbar visibility was last set manually by the user, in which case the
     * `hide-extra-keys-when-soft-keyboard-hidden` property does not change it until the soft keyboard
     * visibility changes again.
     */
    private boolean mIsTerminalToolbarVisibilitySetManually;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;



    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();
        setTerminalPadding();
        setUpStatusBar();
        setUpNotificationOverlay();
        reloadTerminalMenuConfig();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        mTerminalSearchController = new TerminalSearchController(this);
        mTerminalSearchController.setUp();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        // Refresh the session list when the drawer is opened, since the details shown for the sessions,
        // like the foreground process and the working directory, may have changed while it was closed.
        getDrawer().addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                if (mTermuxSessionListViewController != null)
                    mTermuxSessionListViewController.notifyDataSetChanged();
            }
        });

        // The receiver must be registered for the whole lifetime of the activity and not only while it
        // is started, since the settings activities change termux properties while this activity is
        // stopped, and the reload style broadcast that they send must not be lost.
        registerTermuxActivityBroadcastReceiver();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mStatusBarController != null) {
            mStatusBarController.start();
            updateStatusBarLayout();
        }

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        for (TermuxTerminalViewClient viewClient : mTermuxTerminalViewClients) {
            if (viewClient != null) viewClient.onStart();
        }

        if (mPreferences.isTerminalMarginAdjustmentEnabled() || mProperties.shouldHideExtraKeysWhenSoftKeyboardIsHidden())
            addTermuxActivityRootViewGlobalLayoutListener();

        // Apply the styling reload requested by the settings activities while this activity was stopped.
        if (mReloadActivityStylingOnStart) {
            boolean recreateActivity = mRecreateActivityOnStart;
            mReloadActivityStylingOnStart = false;
            mRecreateActivityOnStart = false;

            Logger.logDebug(LOG_TAG, "Applying activity styling reload requested while not visible");
            reloadActivityStyling(recreateActivity);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        for (TermuxTerminalViewClient viewClient : mTermuxTerminalViewClients) {
            if (viewClient != null) viewClient.onResume();
        }

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    /**
     * Report the focus change of the terminal window to the terminal program of the current session,
     * if it has enabled focus reporting with `DECSET 1004`. A program can use it to know when the app
     * is switched away from, like to save files, which cannot be done when the app is not visible.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        Logger.logVerbose(LOG_TAG, "onWindowFocusChanged(hasFocus=" + hasFocus + ")");

        if (mIsInvalidState) return;

        TerminalSession session = getCurrentSession();
        if (session == null) return;

        TerminalEmulator emulator = session.getEmulator();
        if (emulator != null) emulator.setWindowFocused(hasFocus);
    }

    /** Returns whether the window of the activity currently has focus. */
    public boolean isWindowFocused() {
        return hasWindowFocus();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mStatusBarController != null) mStatusBarController.stop();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        for (TermuxTerminalViewClient viewClient : mTermuxTerminalViewClients) {
            if (viewClient != null) viewClient.onStop();
        }

        removeTermuxActivityRootViewGlobalLayoutListener();

        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        unregisterTermuxActivityBroadcastReceiver();

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    /**
     * Reload the termux properties from disk.
     *
     * @return Whether the extra keys panels changed, in which case the views of the extra keys must be
     *         recreated, since the views are created from the panels.
     */
    private boolean reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        for (TermuxTerminalViewClient viewClient : mTermuxTerminalViewClients) {
            if (viewClient != null) viewClient.onReloadProperties();
        }

        // The extra keys panels are only parsed when the extra keys client is created or when they are
        // reloaded, since the parsing is not handled by `onReloadProperties()` above.
        return mTermuxTerminalExtraKeys != null && mTermuxTerminalExtraKeys.reloadExtraKeys();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE. If the `night-mode` property is set to `color-scheme`,
        // then it is resolved to light or dark based on the termux color scheme background color.
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    /**
     * Re-apply the app night mode, since it may be based on the termux color scheme with the
     * `color-scheme` value of the `night-mode` property, which may have been changed by
     * Termux:Styling in the `colors.properties` file. The activity is recreated by
     * {@link AppCompatActivityUtils} if the night mode actually changed, so that the new theme
     * takes effect.
     */
    public void updateNightMode() {
        setActivityTheme();
    }

    /**
     * Set the window (decor view) background color, like the terminal background color, and update
     * the status bar and navigation bar icon colors so that they remain visible on the background,
     * since it may be a light or dark color depending on the termux color scheme set by
     * Termux:Styling in the `colors.properties` file.
     *
     * The status bar and navigation bar are translucent, so their background color is the window
     * background color, but their icon colors must be manually updated, since android does not do
     * that automatically for a runtime set background color when the app theme does not match it.
     */
    public void updateWindowBackgroundColor(int backgroundColor) {
        getWindow().getDecorView().setBackgroundColor(backgroundColor);

        boolean isLightBackground = TermuxThemeUtils.isLightBackground(backgroundColor);
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(isLightBackground);
            windowInsetsController.setAppearanceLightNavigationBars(isLightBackground);
        }
    }

    /** @return The {@link TerminalView} of every pane, including the hidden secondary pane. */
    public TerminalView[] getTerminalViews() {
        return mTerminalViews;
    }

    /** @return Whether split screen is enabled, i.e. whether the secondary pane is visible. */
    public boolean isSplitScreenEnabled() {
        return mTerminalSplitScreenController.isEnabled();
    }

    /**
     * Enable or disable split screen, and set the orientation of the panes.
     *
     * When split screen is enabled, the secondary pane shows a running session that is not shown in
     * the primary pane, or a new session if there is none. Both panes can show any session from the
     * sessions drawer, and tapping a pane makes it the active pane that receives the keyboard, the
     * extra keys and the menus. Closing split screen does not stop the session of the secondary pane.
     *
     * @param enabled Whether split screen should be enabled.
     * @param orientation Either {@link LinearLayout#VERTICAL} for panes above each other or
     *                    {@link LinearLayout#HORIZONTAL} for panes next to each other.
     */
    /** @return The orientation of the split screen panes, either {@link LinearLayout#VERTICAL} or {@link LinearLayout#HORIZONTAL}. */
    public int getSplitScreenOrientation() {
        return mTerminalSplitScreenController.getOrientation();
    }

    public void setSplitScreen(boolean enabled, int orientation) {
        if (orientation != LinearLayout.VERTICAL && orientation != LinearLayout.HORIZONTAL) return;
        mTerminalSplitScreenController.setOrientation(orientation);

        if (!enabled) {
            if (mTerminalSplitScreenController.isEnabled()) {
                mTerminalSplitScreenController.setEnabled(false);
                mTerminalSplitScreenController.updateLayout();
                setActiveTerminalPane(0);
                mTerminalSplitScreenController.updatePaneActiveStates(mActiveTerminalViewIndex);
            }
            return;
        }

        if (mTerminalSplitScreenController.isEnabled()) {
            // Already split, so just change the orientation of the panes.
            mTerminalSplitScreenController.updateLayout();
            return;
        }

        TerminalSession primarySession = mTerminalViews[0].getCurrentSession();
        TerminalSession secondarySession = mTerminalViews[1].getCurrentSession();
        if (secondarySession == null || secondarySession == primarySession || !secondarySession.isRunning()) {
            // Show a different session in the secondary pane, or create a new one if there is none.
            secondarySession = findSessionForSecondaryPane(primarySession);
            if (secondarySession == null) {
                TermuxService service = getTermuxService();
                if (service != null && service.getTermuxSessionsSize() >= TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
                    showToast(getString(R.string.msg_max_terminals_reached), true);
                    return;
                }
                secondarySession = createSessionForSecondaryPane();
            }
            if (secondarySession == null) return;

            // Attach the session to the secondary pane's view. The fonts and the font size have
            // already been applied to it, since they are applied to the view of every pane.
            mTerminalViews[1].attachSession(secondarySession);
        }

        mTerminalSplitScreenController.setEnabled(true);
        mTerminalSplitScreenController.updateLayout();
        mTerminalSplitScreenController.updatePaneActiveStates(mActiveTerminalViewIndex);
        // The secondary pane may have been running for a long time while it was hidden, so show its
        // current output right away instead of waiting for the next output of its session.
        mTerminalViews[1].onScreenOutput();
    }

    /**
     * Make the pane at {@code index} the active pane, which receives the keyboard, the extra keys
     * and the context menu. Only the session of the active pane receives focus events.
     */
    public void setActiveTerminalPane(int index) {
        if (index < 0 || index >= mTerminalViews.length) return;
        if (!mTerminalSplitScreenController.isEnabled()) index = 0;
        if (index == mActiveTerminalViewIndex) return;

        TerminalSession previousSession = getCurrentSession();
        // Clear the search highlights of the previously active pane, since the search bar operates
        // on the active pane only.
        mTerminalViews[mActiveTerminalViewIndex].clearSearch();

        mActiveTerminalViewIndex = index;
        mTerminalView = mTerminalViews[index];
        mTermuxTerminalViewClient = mTermuxTerminalViewClients[index];

        mTerminalSplitScreenController.updatePaneActiveStates(mActiveTerminalViewIndex);

        // Only the session of the active pane may report having window focus.
        if (previousSession != null && previousSession.getEmulator() != null)
            previousSession.getEmulator().setWindowFocused(false);

        TerminalSession session = mTerminalView.getCurrentSession();
        if (session != null && session.getEmulator() != null)
            session.getEmulator().setWindowFocused(isWindowFocused());

        mTerminalView.requestFocus();
        // The pane was not redrawn while it was not the active pane, so show the output that arrived
        // in the meantime, without changing its scroll position.
        mTerminalView.onScreenOutput();
        if (session != null) {
            mTermuxTerminalSessionActivityClient.checkAndScrollToSession(session);
            mTermuxTerminalSessionActivityClient.updateBackgroundColor();
            refreshTerminalSearch();
        }
    }

    /** @return Whether the {@link TerminalView} of any pane has focus. */
    public boolean isAnyTerminalViewFocused() {
        for (TerminalView terminalView : mTerminalViews) {
            if (terminalView != null && terminalView.hasFocus()) return true;
        }
        return false;
    }

    /** @return The {@link TerminalView} of the pane that is not active, or {@code null} if split screen is disabled. */
    @Nullable
    public TerminalView getInactiveTerminalPaneView() {
        if (!mTerminalSplitScreenController.isEnabled()) return null;
        return mTerminalViews[1 - mActiveTerminalViewIndex];
    }

    /**
     * @return The {@link TerminalView} of the visible pane that is showing {@code session}, or
     *         {@code null} if no visible pane is showing it, like when the session is only in the
     *         sessions drawer.
     */
    @Nullable
    public TerminalView getTerminalViewForSession(@Nullable TerminalSession session) {
        if (session == null) return null;

        for (int i = 0; i < mTerminalViews.length; i++) {
            // The secondary pane is hidden when split screen is disabled, so it must not be redrawn.
            if (i != 0 && !mTerminalSplitScreenController.isEnabled()) continue;
            TerminalView terminalView = mTerminalViews[i];
            if (terminalView != null && terminalView.getCurrentSession() == session) return terminalView;
        }

        return null;
    }

    /** @return The session shown in the pane that is not active, or {@code null} if split screen is disabled. */
    @Nullable
    public TerminalSession getInactiveTerminalPaneSession() {
        if (!mTerminalSplitScreenController.isEnabled()) return null;
        return mTerminalViews[1 - mActiveTerminalViewIndex].getCurrentSession();
    }

    /**
     * Swap the sessions of the two panes, so that the session that was shown in the inactive pane is
     * shown in the active one. This is used when the user selects the session of the other pane from
     * the sessions drawer, since a session cannot be attached to two terminal views at the same time,
     * because the views would resize it differently.
     */
    public void swapTerminalPaneSessions() {
        if (!mTerminalSplitScreenController.isEnabled()) return;

        TerminalView activeView = mTerminalViews[mActiveTerminalViewIndex];
        TerminalView inactiveView = mTerminalViews[1 - mActiveTerminalViewIndex];
        TerminalSession activeSession = activeView.getCurrentSession();
        TerminalSession inactiveSession = inactiveView.getCurrentSession();

        if (inactiveSession != null) activeView.attachSession(inactiveSession);
        if (activeSession != null) inactiveView.attachSession(activeSession);

        // The active pane now shows a different session, which has not been redrawn while it was
        // shown in the inactive pane. Redraw the inactive pane, which also gets the other session.
        TerminalView inactivePaneView = getInactiveTerminalPaneView();
        if (inactivePaneView != null) inactivePaneView.onScreenOutput();

        if (inactiveSession != null) {
            mTermuxTerminalSessionActivityClient.checkAndScrollToSession(inactiveSession);
            mTermuxTerminalSessionActivityClient.updateBackgroundColor();
            refreshTerminalSearch();
        }
    }

    /** @return A running session that is not {@code excludedSession}, or {@code null} if there is none. */
    @Nullable
    private TerminalSession findSessionForSecondaryPane(@Nullable TerminalSession excludedSession) {
        TermuxService service = getTermuxService();
        if (service == null) return null;

        for (int i = 0; i < service.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = service.getTermuxSession(i);
            if (termuxSession == null) continue;
            TerminalSession session = termuxSession.getTerminalSession();
            if (session == null || session == excludedSession || !session.isRunning()) continue;
            return session;
        }
        return null;
    }

    /** @return A new session for the secondary pane, or {@code null} if it failed to be created. */
    @Nullable
    private TerminalSession createSessionForSecondaryPane() {
        TermuxService service = getTermuxService();
        if (service == null) return null;

        TermuxSession termuxSession = service.createTermuxSession(null, null, null,
            mProperties.getDefaultWorkingDirectory(), false, null);
        return termuxSession != null ? termuxSession.getTerminalSession() : null;
    }

    /** Update the layout params of the panes and the divider for the current orientation and split state. */
    /** Show the terminal search bar and focus its text input, so that the user can type a query. */
    public void showTerminalSearch() {
        mTerminalSearchController.show();
    }

    /** Hide the terminal search bar and clear the search highlights. */
    public void hideTerminalSearch() {
        mTerminalSearchController.hide();
    }

    /** @return Whether the search bar of the terminal is shown. */
    public boolean isTerminalSearchBarVisible() {
        return mTerminalSearchController.isVisible();
    }

    /**
     * Apply the query of the search bar to the current session again, which is required after a
     * session switch since the search matches of the previous session's buffer are then invalid.
     */
    public void refreshTerminalSearch() {
        mTerminalSearchController.refresh();
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    /**
     * Set the padding around the terminal view from the `terminal-padding-left`, `terminal-padding-top`,
     * `terminal-padding-right` and `terminal-padding-bottom` properties, so that the terminal grid is
     * inset from the edges of the window, like the padding of the Windows Terminal.
     *
     * The padding is applied to the container of the terminal panes instead of the terminal views
     * themselves, so that only their size is reduced and the drawing, touch coordinates, text
     * selection and input connection code of the terminal views do not need to know about the
     * padding. The terminal toolbar and the sessions drawer are not padded, so that they stay full
     * width and the padding area shows the terminal background color, like a frame around the panes.
     */
    private void setTerminalPadding() {
        View terminalPanesContainer = findViewById(R.id.terminal_panes_container);
        if (terminalPanesContainer == null) return;

        int paddingTop = mProperties.getTerminalPaddingTop();
        int paddingBottom = mProperties.getTerminalPaddingBottom();

        // When the status bar does not overlay the terminal, its height is reserved by insetting the
        // panes container on the edge it is shown on. This resizes the terminal views, so that the
        // terminal has fewer rows and the terminal program gets a SIGWINCH, instead of the content
        // being covered by the status bar or being pushed into the soft keyboard area.
        StatusBarConfig statusBarConfig = getStatusBarConfig();
        if (statusBarConfig != null && statusBarConfig.enabled && !statusBarConfig.segments.isEmpty()
            && !statusBarConfig.overlay) {
            if (statusBarConfig.positionBottom)
                paddingBottom += statusBarConfig.heightDp;
            else
                paddingTop += statusBarConfig.heightDp;
        }

        terminalPanesContainer.setPadding(
            dpToPx(mProperties.getTerminalPaddingLeft()),
            dpToPx(paddingTop),
            dpToPx(mProperties.getTerminalPaddingRight()),
            dpToPx(paddingBottom));
    }

    /** @return The configuration of the status bar, or {@code null} if it was not loaded yet. */
    @Nullable
    private StatusBarConfig getStatusBarConfig() {
        return mStatusBarController != null ? mStatusBarController.getConfig() : null;
    }

    private int dpToPx(int dp) {
        return Math.round(ViewUtils.dpToPx(this, dp));
    }

    /**
     * Set up the status bar that shows the time, battery, memory, cpu, network, ip and proxy
     * segments, which are configured with the `status-bar-*` properties from `termux.properties` or
     * the status bar section of the termux settings.
     */
    private void setUpStatusBar() {
        mStatusBarView = findViewById(R.id.status_bar_view);
        if (mStatusBarView == null) return;

        mStatusBarController = new StatusBarController(this, mStatusBarView);
        mStatusBarController.reload();
        updateStatusBarLayout();
    }

    /**
     * Set up the overlay that shows the notification cards in a corner of the terminal, like for a
     * command that exited with a non-zero exit code, which are configured with the `notification-*`
     * properties from `termux.properties` or the notification section of the termux settings.
     */
    private void setUpNotificationOverlay() {
        mNotificationOverlay = findViewById(R.id.notification_overlay);
        if (mNotificationOverlay == null) return;

        mNotificationManager = new TerminalNotificationManager(this, mNotificationOverlay);
        mNotificationManager.setListener(new TerminalNotificationManager.Listener() {
            @Override
            public void onTerminalNotificationClicked(@NonNull TerminalNotification notification) {
                TermuxActivity.this.onTerminalNotificationClicked(notification);
            }

            @Override
            public void onTerminalNotificationClosed(@NonNull TerminalNotification notification, boolean expired) {
                // Nothing to do yet, the list of recent notifications is filled by a later milestone.
            }
        });
        mNotificationManager.reload();
        updateNotificationOverlayLayout();
    }

    /**
     * Apply the height of the status bar to the notification cards, so that they are moved below it
     * when they are shown in the top corner and the status bar overlays the terminal, since both are
     * drawn in the same content container.
     */
    public void updateNotificationOverlayLayout() {
        if (mNotificationOverlay == null || mNotificationManager == null) return;

        int statusBarInsetPx = 0;
        StatusBarConfig statusBarConfig = getStatusBarConfig();
        if (statusBarConfig != null && statusBarConfig.enabled && !statusBarConfig.segments.isEmpty()
            && statusBarConfig.overlay && !statusBarConfig.positionBottom)
            statusBarInsetPx = dpToPx(statusBarConfig.heightDp);

        mNotificationOverlay.setStatusBarInsetPx(statusBarInsetPx);
    }

    /**
     * Called when a notification card is clicked, which jumps to the command that the notification is
     * about when it was generated for a command that exited with a non-zero exit code.
     */
    private void onTerminalNotificationClicked(@NonNull TerminalNotification notification) {
        Logger.logDebug(LOG_TAG, "Terminal notification card clicked: " + notification);
    }

    /** @return The manager of the notification cards, or {@code null} if the overlay is not in the layout. */
    @Nullable
    public TerminalNotificationManager getTerminalNotificationManager() {
        return mNotificationManager;
    }

    /**
     * Jump to the command with {@code commandSequence} in {@code session}, like when the card of a
     * failed command is clicked.
     *
     * The session is switched to first if it is not shown in a pane, and the command is looked up by
     * its sequence number, so that the right command is jumped to even when other commands have
     * finished in the meantime.
     */
    public void jumpToCommand(@Nullable TerminalSession session, int commandSequence) {
        if (session == null) return;

        TerminalView terminalView = getTerminalViewForSession(session);
        if (terminalView == null && mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.setCurrentSession(session);
            terminalView = getTerminalViewForSession(session);
        }
        if (terminalView == null) return;

        // Fall back to the previous command if the command is no longer in the buffer, which happens
        // when the output of the command was longer than the whole buffer.
        if (!terminalView.scrollToCommandSequence(commandSequence))
            terminalView.scrollToPreviousCommand();
    }

    /**
     * Send a test notification, which cycles through the kinds of notifications so that the user can
     * check the appearance of the cards and tune the `notification-*` properties without having to
     * wait for a command to fail.
     */
    public void sendTestNotification() {
        if (mNotificationManager == null) return;

        String separator = " " + getString(R.string.notification_meta_separator) + " ";
        String cwd = getString(R.string.notification_test_meta_cwd);
        String duration = getString(R.string.notification_test_meta_duration);
        String session = getString(R.string.notification_test_meta_session);
        String meta = cwd + separator + duration + separator + session;

        TerminalNotification notification;
        switch (mTestNotificationCount++ % 4) {
            case 0:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.FAILURE, TerminalNotification.Source.EXIT_CODE)
                    .setTitle(getString(R.string.notification_test_title_failed_command, 1))
                    .setBody(getString(R.string.notification_test_meta_command))
                    .setMeta(meta)
                    .build();
                break;
            case 1:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.SUCCESS, TerminalNotification.Source.EXIT_CODE)
                    .setTitle(getString(R.string.notification_test_title_succeeded_command))
                    .setBody(getString(R.string.notification_test_body_succeeded_command))
                    .setMeta(meta)
                    .build();
                break;
            case 2:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.WARNING, TerminalNotification.Source.APP_EVENT)
                    .setTitle(getString(R.string.notification_test_title_session_exited))
                    .setMeta(session)
                    .build();
                break;
            default:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.INFO, TerminalNotification.Source.OSC)
                    .setTitle(getString(R.string.notification_test_title_osc))
                    .setBody(getString(R.string.notification_test_body_osc))
                    .setMeta(getString(R.string.notification_test_meta_osc_app))
                    .build();
                break;
        }

        mNotificationManager.notifyForTesting(notification);
    }

    /**
     * Show the notifications that were shown recently, including the ones whose cards have already
     * disappeared. Clicking an entry jumps to the command of a failed command, or copies the text of the
     * other notifications to the clipboard, since they have no command that could be jumped to.
     */
    public void showRecentNotifications() {
        if (mNotificationManager == null) return;

        List<RecentNotifications.Entry> entries = mNotificationManager.getRecentNotifications().getEntries();
        NotificationConfig config = mNotificationManager.getConfig();

        RecentNotificationsDialog.show(this, config, entries, entry -> {
            if (entry.onOpen != null) {
                entry.onOpen.run();
            } else {
                ShareUtils.copyTextToClipboard(this, RecentNotificationsDialog.getTextFor(entry.notification),
                    getString(R.string.msg_copied_to_clipboard));
            }
        }, () -> mNotificationManager.getRecentNotifications().clear());
    }

    /**
     * Hook the system menu of the activity to show the terminal context menu instead, so that the
     * hardware menu key opens it.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        showTerminalMenu();
        return false;
    }

    /** Reload the configuration of the terminal context menu and of the text selection toolbar. */
    private void reloadTerminalMenuConfig() {
        mTerminalMenuConfig = TerminalMenuConfig.parse(ForkProperties.readTermuxProperties());
    }

    /** @return The configuration of the terminal context menu and of the text selection toolbar. */
    @NonNull
    public TerminalMenuConfig getTerminalMenuConfig() {
        return mTerminalMenuConfig;
    }

    /**
     * Show the terminal context menu, which is a styled dialog instead of the system context menu, with
     * the actions and the order that the `terminal-menu-items` property defines.
     *
     * The actions that cannot be used are not shown, like the action that shares the selected text when
     * no text is selected, so that the menu only shows what the user can actually do.
     */
    public void showTerminalMenu() {
        TerminalSession session = getCurrentSession();
        if (session == null || mTerminalView == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();
        boolean hasSelectedText = !DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText());
        boolean notificationsEnabled = mNotificationManager != null && mNotificationManager.isEnabled();

        List<TerminalMenuDialog.Item> items = new ArrayList<>();
        for (TerminalMenuAction action : mTerminalMenuConfig.menuItems) {
            switch (action) {
                case SPLIT_CLOSE:
                    if (!isSplitScreenEnabled()) continue;
                    break;
                case SHARE_SELECTED_TEXT:
                    if (!hasSelectedText) continue;
                    break;
                case AUTOFILL_USERNAME:
                case AUTOFILL_PASSWORD:
                    if (!autoFillEnabled) continue;
                    break;
                case TEST_NOTIFICATION:
                    if (!notificationsEnabled) continue;
                    break;
                case RECENT_NOTIFICATIONS:
                    // The list is only useful when a notification was shown since the app was started.
                    if (mNotificationManager == null || mNotificationManager.getRecentNotifications().isEmpty())
                        continue;
                    break;
                default:
                    break;
            }

            boolean enabled = action != TerminalMenuAction.KILL_PROCESS || session.isRunning();
            items.add(new TerminalMenuDialog.Item(action, action.getTitle(this, session), enabled));
        }

        if (items.isEmpty()) return;

        TerminalMenuDialog.show(this, mTerminalMenuConfig, items, this::onTerminalMenuActionSelected,
            () -> mTerminalView.unsetStoredSelectedText());
    }

    /** Run the action of an item of the terminal context menu. */
    private void onTerminalMenuActionSelected(@NonNull TerminalMenuAction action) {
        TerminalSession session = getCurrentSession();

        switch (action) {
            case SEARCH:
                showTerminalSearch();
                break;
            case PREVIOUS_COMMAND:
                mTerminalView.scrollToPreviousCommand();
                break;
            case NEXT_COMMAND:
                mTerminalView.scrollToNextCommand();
                break;
            case SPLIT_VERTICAL:
                setSplitScreen(true, LinearLayout.VERTICAL);
                break;
            case SPLIT_HORIZONTAL:
                setSplitScreen(true, LinearLayout.HORIZONTAL);
                break;
            case SPLIT_CLOSE:
                setSplitScreen(false, mTerminalSplitScreenController.getOrientation());
                break;
            case SELECT_URL:
                mTermuxTerminalViewClient.showUrlSelection();
                break;
            case SHARE_TRANSCRIPT:
                mTermuxTerminalViewClient.shareSessionTranscript();
                break;
            case SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                break;
            case AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                break;
            case AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                break;
            case RESET_TERMINAL:
                onResetTerminalSession(session);
                break;
            case KILL_PROCESS:
                showKillSessionDialog(session);
                break;
            case STYLING:
                showStylingDialog();
                break;
            case KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                break;
            case HELP:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                break;
            case SETTINGS:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                break;
            case REPORT:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                break;
            case RECENT_NOTIFICATIONS:
                showRecentNotifications();
                break;
            case TEST_NOTIFICATION:
                sendTestNotification();
                break;
            default:
                break;
        }
    }

    /**
     * Show or hide the status bar and apply its position and overlay from the `status-bar-position`
     * and `status-bar-overlay` properties.
     *
     * The status bar is hosted in the content container of the sessions drawer, at the top or bottom
     * edge, so that the drawer is drawn over it and it never covers the drawer. When it does not
     * overlay the terminal, the terminal is inset by its height instead, which resizes the terminal
     * views (fewer rows, `SIGWINCH` to the terminal programs) without covering any content.
     */
    public void updateStatusBarLayout() {
        if (mStatusBarView == null || mStatusBarController == null) return;

        StatusBarConfig config = getStatusBarConfig();
        if (config == null) return;

        boolean visible = config.enabled && !config.segments.isEmpty();
        mStatusBarView.setVisibility(visible ? View.VISIBLE : View.GONE);

        // Only an overlay needs an elevation, since the terminal is otherwise inset by the height of
        // the status bar by setTerminalPadding() and nothing is drawn below it.
        mStatusBarView.setElevation(visible && config.overlay ? dpToPx(4) : 0);

        // Inset the terminal by the height of the status bar, or restore the space it took before.
        setTerminalPadding();

        // Move the notification cards below the status bar when both are shown in the top corner.
        updateNotificationOverlayLayout();
    }

    /**
     * Run the action of a status bar segment that was clicked, as set with the
     * `status-bar-segment-<id>-action` property.
     */
    public void onStatusBarSegmentClicked(@NonNull StatusBarConfig.Segment segment) {
        try {
            switch (segment.action) {
                case StatusBarConfig.ACTION_WIFI_SETTINGS:
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    break;
                case StatusBarConfig.ACTION_BATTERY_SETTINGS:
                    startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
                    break;
                case StatusBarConfig.ACTION_DATA_SETTINGS:
                    startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS));
                    break;
                case StatusBarConfig.ACTION_APP_SETTINGS:
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                    break;
                case StatusBarConfig.ACTION_NEW_SESSION:
                    if (mTermuxTerminalSessionActivityClient != null)
                        mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to run the action \"" + segment.action + "\" of the status bar segment \"" + segment.id + "\"", e);
            Logger.showToast(this, getString(R.string.msg_status_bar_action_failed, segment.action), true);
        }
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal session client
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);

        // Set termux terminal views, one for each pane, and their clients
        mTerminalPaneContainers[0] = findViewById(R.id.terminal_pane_0);
        mTerminalPaneContainers[1] = findViewById(R.id.terminal_pane_1);
        mTerminalViews[0] = findViewById(R.id.terminal_view);
        mTerminalViews[1] = findViewById(R.id.terminal_view_2);

        for (int i = 0; i < mTerminalViews.length; i++) {
            final int paneIndex = i;
            TerminalView terminalView = mTerminalViews[i];
            TermuxTerminalViewClient viewClient =
                new TermuxTerminalViewClient(this, terminalView, mTermuxTerminalSessionActivityClient);
            mTermuxTerminalViewClients[i] = viewClient;
            terminalView.setTerminalViewClient(viewClient);
            registerForContextMenu(terminalView);

            // Make the pane of the tapped view the active pane before the view handles the touch, so
            // that all the gesture callbacks, the keyboard and the menus act on that pane.
            terminalView.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mTerminalSplitScreenController.isEnabled() &&
                    paneIndex != mActiveTerminalViewIndex)
                    setActiveTerminalPane(paneIndex);
                return false;
            });
        }

        mActiveTerminalViewIndex = 0;
        mTerminalView = mTerminalViews[0];
        mTermuxTerminalViewClient = mTermuxTerminalViewClients[0];

        for (TermuxTerminalViewClient viewClient : mTermuxTerminalViewClients) {
            if (viewClient != null) viewClient.onCreate();
        }

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();

        mTerminalSplitScreenController = new TerminalSplitScreenController(this, mTerminalPaneContainers,
            findViewById(R.id.terminal_panes_container), findViewById(R.id.terminal_split_divider));
        mTerminalSplitScreenController.setUpDividerTouchListener();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    /**
     * Recreate the pages of the terminal toolbar view pager, like when the extra keys panels have changed,
     * since each extra keys page is created from a panel. The text typed in the text input page is
     * preserved by the pager adapter.
     */
    private void recreateTerminalToolbarViewPagerPages() {
        ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        PagerAdapter adapter = terminalToolbarViewPager.getAdapter();
        if (adapter == null) return;

        Logger.logDebug(LOG_TAG, "Recreating the terminal toolbar view pager pages since the extra keys panels changed");
        adapter.notifyDataSetChanged();

        // The current page may not exist anymore if the number of the panels decreased.
        if (terminalToolbarViewPager.getCurrentItem() >= adapter.getCount())
            terminalToolbarViewPager.setCurrentItem(adapter.getCount() - 1, false);
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            mTermuxTerminalExtraKeys.getExtraKeysPanelsMaxRows() *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        // The user made the choice manually, so it should be respected even if the
        // `hide-extra-keys-when-soft-keyboard-hidden` property would hide the toolbar, until the soft
        // keyboard visibility changes again.
        mIsTerminalToolbarVisibilitySetManually = true;
        setTerminalToolbarVisibility(showNow);
    }

    private void setTerminalToolbarVisibility(boolean visible) {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        if ((terminalToolbarViewPager.getVisibility() == View.VISIBLE) == visible) return;

        terminalToolbarViewPager.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    /**
     * Update whether the terminal toolbar is visible based on the soft keyboard visibility, if the
     * `hide-extra-keys-when-soft-keyboard-hidden` property is enabled, so that the rows occupied by
     * the extra keys are given back to the terminal while the soft keyboard is hidden, like when the
     * terminal is being read.
     *
     * The soft keyboard visibility can only be checked when the layout is updated, so this must be
     * called from an {@link android.view.ViewTreeObserver.OnGlobalLayoutListener}, and needs to be
     * called when the properties are reloaded so that a change of the property takes effect
     * immediately even if the layout does not change afterwards.
     */
    public void updateTerminalToolbarVisibilityForSoftKeyboardVisibility() {
        if (mIsInvalidState || mProperties == null || mPreferences == null) return;

        boolean hideWhenSoftKeyboardIsHidden = mProperties.shouldHideExtraKeysWhenSoftKeyboardIsHidden();

        Boolean isSoftKeyboardVisible = null;
        if (hideWhenSoftKeyboardIsHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // The soft keyboard visibility cannot be checked on android 7 and below, so the toolbar
            // visibility is not updated based on it there.
            isSoftKeyboardVisible = KeyboardUtils.isSoftKeyboardVisible(this) ? Boolean.TRUE : Boolean.FALSE;
        }

        if (!Objects.equals(isSoftKeyboardVisible, mIsSoftKeyboardVisibleForToolbar)) {
            // The soft keyboard visibility changed, so the user should have to toggle the toolbar again
            // if they want it shown while the soft keyboard is hidden.
            mIsSoftKeyboardVisibleForToolbar = isSoftKeyboardVisible;
            mIsTerminalToolbarVisibilitySetManually = false;
        }

        boolean isTerminalToolbarVisible = mPreferences.shouldShowTerminalToolbar();
        if (isTerminalToolbarVisible && hideWhenSoftKeyboardIsHidden && !mIsTerminalToolbarVisibilitySetManually &&
            Boolean.FALSE.equals(isSoftKeyboardVisible)) {
            Logger.logDebug(LOG_TAG, "Hiding terminal toolbar since the soft keyboard is hidden");
            isTerminalToolbarVisible = false;
        }

        setTerminalToolbarVisibility(isTerminalToolbarVisible);
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }





    @SuppressLint({"RtlHardcoded", "MissingSuperCall"})
    @Override
    public void onBackPressed() {
        if (isTerminalSearchBarVisible()) {
            hideTerminalSearch();
        } else if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }




    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    /** The text color applied to the extra keys view buttons and the terminal toolbar text input view. */
    private int mExtraKeysTextColor = ExtraKeysView.DEFAULT_BUTTON_TEXT_COLOR;

    /** Get the {@link ExtraKeysView} of each extra keys panel that is currently created, if any. */
    public List<ExtraKeysView> getExtraKeysViews() {
        return mExtraKeysViews;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    /**
     * Add a {@link ExtraKeysView} panel to {@link #mExtraKeysViews} and set its colors. Must be called
     * before the caller reloads the extra keys view, since the colors are applied to the buttons when
     * they are created in {@link ExtraKeysView#reload(com.termux.shared.termux.extrakeys.ExtraKeysInfo, float)}.
     */
    public void addExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysViews.add(extraKeysView);
        setExtraKeysViewColors(extraKeysView);
    }

    /** Remove a {@link ExtraKeysView} panel from {@link #mExtraKeysViews}, like when the pager destroys it. */
    public void removeExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysViews.remove(extraKeysView);
    }

    /**
     * Update the colors of the extra keys view buttons from the termux color scheme and the
     * `extra-keys-*` properties and recreate the buttons so that the new colors are applied.
     *
     * This should be called whenever the colors of the termux color scheme set by Termux:Styling in
     * the `colors.properties` file or the `extra-keys-*` properties in `termux.properties` may have
     * changed.
     */
    public void updateExtraKeysViewColors() {
        // The status bar follows the colors of the color scheme as well.
        if (mStatusBarController != null) mStatusBarController.reloadColors();

        for (ExtraKeysView extraKeysView : mExtraKeysViews) {
            setExtraKeysViewColors(extraKeysView);

            // The colors are applied to the buttons when they are created, so they must be recreated.
            extraKeysView.reload(extraKeysView.getExtraKeysInfo(), getTerminalToolbarDefaultHeight());
        }
    }

    /**
     * Set the colors of the extra keys view buttons from the termux color scheme, like the one set
     * by Termux:Styling in the `colors.properties` file, and the `extra-keys-*` properties in
     * `termux.properties`, which take precedence and are not overwritten by Termux:Styling.
     */
    private void setExtraKeysViewColors(@NonNull ExtraKeysView extraKeysView) {
        TerminalColorScheme colorScheme = TerminalColors.COLOR_SCHEME;

        int textColor = getExtraKeysColor(mProperties.getExtraKeysForeground(),
            TermuxPropertyConstants.KEY_EXTRA_KEYS_FOREGROUND, colorScheme.getExtraKeysForegroundColor());
        int activeTextColor = getExtraKeysColor(mProperties.getExtraKeysActiveForeground(),
            TermuxPropertyConstants.KEY_EXTRA_KEYS_ACTIVE_FOREGROUND, colorScheme.getExtraKeysActiveForegroundColor());
        int backgroundColor = getExtraKeysColor(mProperties.getExtraKeysBackground(),
            TermuxPropertyConstants.KEY_EXTRA_KEYS_BACKGROUND, colorScheme.getExtraKeysBackgroundColor());
        int activeBackgroundColor = getExtraKeysColor(mProperties.getExtraKeysActiveBackground(),
            TermuxPropertyConstants.KEY_EXTRA_KEYS_ACTIVE_BACKGROUND, colorScheme.getExtraKeysActiveBackgroundColor());
        int tileBackgroundColor = getExtraKeysColor(mProperties.getExtraKeysTileBackground(),
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TILE_BACKGROUND_COLOR, colorScheme.getExtraKeysTileBackgroundColor());
        int tileActiveBackgroundColor = getExtraKeysColor(mProperties.getExtraKeysTileActiveBackground(),
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TILE_ACTIVE_BACKGROUND_COLOR, colorScheme.getExtraKeysTileActiveBackgroundColor());

        // The text color is also used by the terminal toolbar text input view, whose text color is
        // hardcoded to white in the layout and would not be visible if the toolbar background is light.
        mExtraKeysTextColor = textColor;

        extraKeysView.setButtonColors(textColor, activeTextColor, backgroundColor, activeBackgroundColor);
        extraKeysView.setButtonTileColors(tileBackgroundColor, tileActiveBackgroundColor);

        // The extra keys view is hosted in the terminal toolbar view pager, which also contains the
        // text input view, and its background is hardcoded to black in the layout, which would show
        // through around the extra keys buttons, so it must be updated as well to match the colors.
        View terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager != null)
            terminalToolbarViewPager.setBackgroundColor(backgroundColor);

        // The extra keys view background is set as well since its background defined by the
        // `buttonBarStyle` may be drawn over the parent view pager background.
        extraKeysView.setBackgroundColor(backgroundColor);

        EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null)
            setTerminalToolbarTextInputViewColors(textInputView);
    }

    /**
     * Set the colors of the terminal toolbar text input view, which shows the text being entered
     * with the keyboard, to the same colors as the extra keys view buttons, so that its text is
     * visible regardless of the toolbar background color. It is called when the view is created by
     * {@link TerminalToolbarViewPager} as well, since the view may not exist when the colors are
     * otherwise updated.
     */
    public void setTerminalToolbarTextInputViewColors(@NonNull EditText textInputView) {
        textInputView.setTextColor(mExtraKeysTextColor);
    }

    /**
     * Get the color defined by {@code value} for the {@code propertyName} property, otherwise the
     * {@code fallbackColor} if the property is not set or its value is invalid.
     */
    private int getExtraKeysColor(@Nullable String value, @NonNull String propertyName, int fallbackColor) {
        if (DataUtils.isNullOrEmpty(value)) return fallbackColor;

        try {
            return Color.parseColor(value.trim());
        } catch (IllegalArgumentException e) {
            Logger.logError(LOG_TAG, "Invalid color \"" + value + "\" defined for the \"" + propertyName + "\" property, using fallback color instead");
            return fallbackColor;
        }
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() < mTermuxTerminalExtraKeys.getExtraKeysPanelCount();
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == mTermuxTerminalExtraKeys.getExtraKeysPanelCount();
    }


    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            // The settings activities change the termux properties while this activity is stopped, since
            // they are launched in front of it, and the styling cannot be reloaded while the activity is
            // not visible, so the reload is applied in onStart() instead of being lost.
            if (!mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                if (TERMUX_ACTIVITY.ACTION_RELOAD_STYLE.equals(intent.getAction())) {
                    Logger.logDebug(LOG_TAG, "Received intent to reload styling while not visible");
                    mReloadActivityStylingOnStart = true;
                    mRecreateActivityOnStart = mRecreateActivityOnStart ||
                        intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true);
                }

                return;
            }

            fixTermuxActivityBroadcastReceiverIntent(intent);

            switch (intent.getAction()) {
                case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                    Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                    TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                    return;
                case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                    Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                    reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                    return;
                case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                    Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                    requestStoragePermission(false);
                    return;
                default:
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            boolean extraKeysPanelsChanged = reloadProperties();

            if (extraKeysPanelsChanged) {
                // The views of the extra keys are created from the panels, so they must be recreated, which
                // also applies the new colors and the all caps setting to them.
                recreateTerminalToolbarViewPagerPages();
            } else {
                for (ExtraKeysView extraKeysView : mExtraKeysViews) {
                    extraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                    extraKeysView.setShowPopupHints(mProperties.shouldExtraKeysPopupHintsBeShown());
                    extraKeysView.setShowTileBackground(mProperties.shouldExtraKeysTileBackgroundBeShown());
                    setExtraKeysViewColors(extraKeysView);
                    extraKeysView.reload(extraKeysView.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
                }
            }

            // Update NightMode.APP_NIGHT_MODE, since the `night-mode` property may be set to
            // `color-scheme`, in which case it depends on the termux color scheme.
            if (recreateActivity) {
                TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
            } else {
                // Applying the night mode locally recreates the activity if the mode actually
                // changed, so that a change of the `night-mode` property takes effect immediately.
                setActivityTheme();
            }
        }

        setMargins();
        setTerminalPadding();
        setTerminalToolbarHeight();

        // Rebuild the status bar, since the `status-bar-*` properties may have changed, and apply the
        // new position and overlay rules.
        if (mStatusBarController != null) {
            mStatusBarController.reload();
            updateStatusBarLayout();
        }

        // Reload the `notification-*` properties, which apply the new position, width and colors to
        // the cards that are currently shown.
        if (mNotificationManager != null) {
            mNotificationManager.reload();
            updateNotificationOverlayLayout();
        }

        // Reload the `terminal-menu-*` and `selection-toolbar-items` properties.
        reloadTerminalMenuConfig();

        // Update the terminal toolbar visibility in case the `hide-extra-keys-when-soft-keyboard-hidden`
        // property was changed or disabled while the soft keyboard was hidden, since the layout may not
        // change afterwards, like if the toolbar is hidden by this or if the terminal view does not change.
        updateTerminalToolbarVisibilityForSoftKeyboardVisibility();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        for (TermuxTerminalViewClient viewClient : mTermuxTerminalViewClients) {
            if (viewClient != null) viewClient.onReloadActivityStyling();
        }

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
