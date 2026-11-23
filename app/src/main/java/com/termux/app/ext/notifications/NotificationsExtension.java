package com.termux.app.ext.notifications;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;
import com.termux.app.ext.api.ExtensionViewSlot;
import com.termux.app.ext.host.ExtensionHost;
import com.termux.app.ext.statusbar.StatusBarConfig;
import com.termux.app.ext.statusbar.StatusBarExtension;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalCommandResult;
import com.termux.terminal.TerminalNotification;
import com.termux.terminal.TerminalSession;

import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「终端内通知体系」作为可启用的扩展提供：失败命令、终端程序的 OSC 通知、应用事件与后台通知，
 * 在终端角落以卡片呈现，并保留一份最近通知历史。
 *
 * <p>行为 — 启用时接管布局预留的叠层视图（{@code R.id.notification_overlay}），创建
 * {@link TerminalNotificationManager} 与三个事件适配器（{@link ExitCodeNotifier}、{@link OscNotificationHandler}、
 * {@link AppEventNotifier}）；宿主把命令结束、标题变化、会话结束、响铃与 OSC 99 事件转发进来，扩展自行决定是否展示；
 * 禁用时停止后台通知、清空叠层与历史。</p>
 *
 * <p>约束 — 叠层位置依赖状态栏高度，因此通过 {@link ExtensionHost} 查询 {@link StatusBarExtension#getConfig()}，
 * 不允许直接读状态栏的视图；状态栏禁用或叠加在终端上时内距为 0，不需要声明依赖。</p>
 */
public final class NotificationsExtension implements Extension {

    private static final String LOG_TAG = "NotificationsExtension";

    /** The stable id of the extension. */
    public static final String ID = "notifications";

    /** The preference key of the settings entry that this extension owns. */
    public static final String PREFERENCE_KEY = "notifications";

    @Nullable
    private ExtensionContext mContext;
    @Nullable
    private TermuxActivity mActivity;
    @Nullable
    private NotificationOverlayView mOverlay;
    @Nullable
    private TerminalNotificationManager mManager;
    @Nullable
    private ExitCodeNotifier mExitCodeNotifier;
    @Nullable
    private OscNotificationHandler mOscNotificationHandler;
    @Nullable
    private AppEventNotifier mAppEventNotifier;

    /** The number of test notifications that were shown, which decides the kind of the next one. */
    private int mTestNotificationCount;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_notification_preferences_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_notification_preferences_summary;
    }

    @NonNull
    @Override
    public List<String> preferenceKeys() {
        return Collections.singletonList(PREFERENCE_KEY);
    }

    @NonNull
    @Override
    public List<ExtensionViewSlot> slots() {
        return Collections.singletonList(new ExtensionViewSlot(R.id.notification_overlay, "notification_overlay",
            "The corner of the terminal where the notification cards are shown"));
    }

    @NonNull
    @Override
    public List<ExtensionSettingsPage> settingsPages() {
        return Collections.singletonList(new ExtensionSettingsPage(
            R.string.termux_notification_preferences_title, R.xml.termux_notification_preferences,
            NotificationPreferencesFragment.class.getName()));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        mContext = context;
        mOverlay = context.view(R.id.notification_overlay);
        if (mOverlay == null) {
            context.logWarn("The layout has no notification overlay, the cards are not shown");
            return;
        }

        mActivity = (TermuxActivity) context.activity();
        mManager = new TerminalNotificationManager(mActivity, mOverlay);
        mManager.setListener(new TerminalNotificationManager.Listener() {
            @Override
            public void onTerminalNotificationClicked(@NonNull TerminalNotification notification) {
                Logger.logDebug(LOG_TAG, "Terminal notification card clicked: " + notification);
            }

            @Override
            public void onTerminalNotificationClosed(@NonNull TerminalNotification notification,
                                                     boolean expired) {
                // The history is kept by the manager itself, so nothing has to be done when a card closes.
            }
        });
        mManager.reload();

        mExitCodeNotifier = new ExitCodeNotifier(mActivity, mManager);
        mOscNotificationHandler = new OscNotificationHandler(mActivity, mManager);
        mAppEventNotifier = new AppEventNotifier(mActivity, mManager);

        updateOverlayLayout();
        context.logInfo("Terminal notifications enabled");
    }

    @Override
    public void onReload() {
        if (mManager == null) return;
        mManager.reload();
        updateOverlayLayout();
    }

    @Override
    public void onDisable() {
        if (mManager != null) mManager.reload();
        mManager = null;
        mExitCodeNotifier = null;
        mOscNotificationHandler = null;
        mAppEventNotifier = null;

        // The view belongs to the layout of the host, so it is emptied and hidden instead of removed, otherwise
        // enabling the extension again would have nowhere to draw into.
        if (mOverlay != null) {
            mOverlay.removeAllViews();
            mOverlay.setVisibility(View.GONE);
        }
        mOverlay = null;

        if (mContext != null) mContext.requestExtensionLayout();
        mContext = null;
        mActivity = null;
    }

    /** @return The manager of the cards and of the history, or {@code null} while the extension is off. */
    @Nullable
    public TerminalNotificationManager getManager() {
        return mManager;
    }

    /** @return Whether the cards are enabled by the properties, which decides whether the menu offers them. */
    public boolean isEnabledByProperties() {
        return mManager != null && mManager.isEnabled();
    }

    /** @return Whether a notification was shown since the app was started, which decides whether the history is useful. */
    public boolean hasRecentNotifications() {
        return mManager != null && !mManager.getRecentNotifications().isEmpty();
    }

    /** @return Whether an OSC notification may carry an icon, which the terminal view client has to know. */
    public boolean isOscIconsEnabled() {
        return mManager != null && mManager.getConfig().oscIcons;
    }

    /** Forward the result of a finished command to the failure cards. */
    public boolean onCommandFinished(@NonNull TerminalSession session, @NonNull TerminalCommandResult result) {
        return mExitCodeNotifier != null && mExitCodeNotifier.onCommandFinished(session, result);
    }

    /** Forward a session whose title changed, which may finish a command that reported its start by the title. */
    public boolean onTitleChanged(@NonNull TerminalSession session) {
        return mAppEventNotifier != null && mAppEventNotifier.onTitleChanged(session);
    }

    /** Forward a session that exited, which is shown as an application event. */
    public boolean onSessionFinished(@NonNull TerminalSession session, boolean exitCodeKnown) {
        return mAppEventNotifier != null && mAppEventNotifier.onSessionFinished(session, exitCodeKnown);
    }

    /** Forward an OSC 99 notification that a terminal program requested. */
    public void onOscNotification(@NonNull TerminalSession session, @NonNull TerminalNotification notification) {
        if (mOscNotificationHandler != null) mOscNotificationHandler.onTerminalNotification(session, notification);
    }

    /** Forward the close of an OSC 99 notification. */
    public void onOsc99Close(@NonNull String id) {
        if (mOscNotificationHandler != null) mOscNotificationHandler.onOsc99Close(id);
    }

    /** Forward the query of whether an OSC 99 notification is still alive. */
    public void onOsc99AliveQuery(@NonNull TerminalSession session, @NonNull String id) {
        if (mOscNotificationHandler != null) mOscNotificationHandler.onOsc99AliveQuery(session, id);
    }

    /** Forward the bell of a session, which may be shown as a notification. */
    public void onBell(@NonNull TerminalSession session) {
        if (mAppEventNotifier != null) mAppEventNotifier.onBell(session);
    }

    /**
     * Apply the height of the status bar to the cards, so that they are moved below it when it overlays the
     * terminal, since both are drawn in the same content container.
     */
    public void updateOverlayLayout() {
        if (mOverlay == null) return;

        TermuxActivity activity = mActivity;
        Extension extension = activity != null ? ExtensionHost.get(activity).extension(StatusBarExtension.ID) : null;
        StatusBarConfig statusBarConfig = extension instanceof StatusBarExtension
            ? ((StatusBarExtension) extension).getConfig() : null;

        int insetPx = 0;
        if (activity != null && statusBarConfig != null && statusBarConfig.enabled
            && !statusBarConfig.segments.isEmpty() && statusBarConfig.overlay
            && !statusBarConfig.positionBottom) {
            insetPx = Math.round(ViewUtils.dpToPx(activity, statusBarConfig.heightDp));
        }

        mOverlay.setStatusBarInsetPx(insetPx);
    }

    /**
     * Show a test notification, which cycles through the kinds of notifications so that the user can check the
     * appearance of the cards and tune the `notification-*` properties without waiting for a command to fail.
     */
    public void sendTestNotification() {
        TermuxActivity activity = mActivity;
        if (activity == null || mManager == null) return;

        String separator = " " + activity.getString(R.string.notification_meta_separator) + " ";
        String cwd = activity.getString(R.string.notification_test_meta_cwd);
        String duration = activity.getString(R.string.notification_test_meta_duration);
        String session = activity.getString(R.string.notification_test_meta_session);
        String meta = cwd + separator + duration + separator + session;

        TerminalNotification notification;
        switch (mTestNotificationCount++ % 4) {
            case 0:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.FAILURE,
                    TerminalNotification.Source.EXIT_CODE)
                    .setTitle(activity.getString(R.string.notification_test_title_failed_command, 1))
                    .setBody(activity.getString(R.string.notification_test_meta_command))
                    .setMeta(meta)
                    .build();
                break;
            case 1:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.SUCCESS,
                    TerminalNotification.Source.EXIT_CODE)
                    .setTitle(activity.getString(R.string.notification_test_title_succeeded_command))
                    .setBody(activity.getString(R.string.notification_test_body_succeeded_command))
                    .setMeta(meta)
                    .build();
                break;
            case 2:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.WARNING,
                    TerminalNotification.Source.APP_EVENT)
                    .setTitle(activity.getString(R.string.notification_test_title_session_exited))
                    .setMeta(session)
                    .build();
                break;
            default:
                notification = new TerminalNotification.Builder(TerminalNotification.Kind.INFO,
                    TerminalNotification.Source.OSC)
                    .setTitle(activity.getString(R.string.notification_test_title_osc))
                    .setBody(activity.getString(R.string.notification_test_body_osc))
                    .setMeta(activity.getString(R.string.notification_test_meta_osc_app))
                    .build();
                break;
        }

        mManager.notifyForTesting(notification);
    }

    /** Show the notifications that were shown recently, including the ones whose cards have already disappeared. */
    public void showRecentNotifications() {
        TermuxActivity activity = mActivity;
        if (activity == null || mManager == null) return;

        List<RecentNotifications.Entry> entries = mManager.getRecentNotifications().getEntries();
        NotificationConfig config = mManager.getConfig();

        RecentNotificationsDialog.show(activity, config, entries, entry -> {
            if (entry.onOpen != null) {
                entry.onOpen.run();
            } else {
                com.termux.shared.interact.ShareUtils.copyTextToClipboard(activity,
                    RecentNotificationsDialog.getTextFor(entry.notification),
                    activity.getString(R.string.msg_copied_to_clipboard));
            }
        }, () -> mManager.getRecentNotifications().clear());
    }
}
