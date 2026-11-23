package com.termux.app.ext.drawer;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.properties.ForkProperties;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * 目的 — 负责会话抽屉（drawer）的外观、把手、开合与概要，把这些逻辑从 Activity 中移出。
 *
 * <p>行为 — 抽屉跟随终端配色方案：{@link #updateColors} 取当前会话的调色板算出背景、前景、次要文字与强调色，
 * 并生成会话行的选中态背景；{@link #setUp} 装配把手、开合模式与打开时的刷新；{@link #reloadConfig} 重新读取
 * `drawer-*` 属性；{@link #updateSummary} 在头部显示会话数量、前台进程与工作目录。</p>
 *
 * <p>约束 — 只能在主线程使用。{@link #reloadConfig} 在 `setContentView` 之前也会被调用（属性在创建视图前
 * 解析），因此所有方法必须容忍视图尚未创建，此时直接返回而不抛异常。</p>
 */
public final class TerminalDrawerController {

    @NonNull
    private final TermuxActivity mActivity;

    @NonNull
    private DrawerConfig mConfig = new DrawerConfig();

    @Nullable
    private TermuxSessionsListViewController mSessionsListAdapter;

    private int mBackgroundColor = Color.BLACK;
    private int mForegroundColor = Color.WHITE;
    private int mSecondaryTextColor = 0xB3FFFFFF;
    private int mAccentColor = Color.WHITE;

    @Nullable
    private Drawable mSessionRowBackground;

    public TerminalDrawerController(@NonNull TermuxActivity activity) {
        mActivity = activity;
    }

    /**
     * Find the views of the drawer and set up the handle and the listener that refreshes the summary, which
     * must be called when the activity created its content view.
     */
    public void setUp() {
        // The colors are applied before the first session is available, so that the drawer does not flash the
        // colors of the app theme, which the black and white theme does not use at all.
        updateColors(mActivity.getCurrentSession());
        setUpHandle();
        applyConfig();
    }

    /** Let the controller refresh the rows of the session list when the colors of the drawer change. */
    public void setSessionsListAdapter(@Nullable TermuxSessionsListViewController adapter) {
        mSessionsListAdapter = adapter;
    }

    /**
     * Read the `drawer-*` properties again and apply them.
     *
     * <p>The properties are not part of the ones that the shared library parses, so they are read from the file
     * on every reload, like the `status-bar-*` and `notification-*` ones.</p>
     */
    public void reloadConfig() {
        mConfig = DrawerConfig.parse(ForkProperties.readTermuxProperties());
        applyConfig();
    }

    /** @return The list view that shows the sessions, or {@code null} before the content view exists. */
    @Nullable
    public ListView getSessionsListView() {
        return mActivity.findViewById(R.id.terminal_sessions_list);
    }

    /** Close the drawer if it is open. */
    public void closeDrawer() {
        DrawerLayout drawer = mActivity.getDrawer();
        if (drawer != null) drawer.closeDrawers();
    }

    /** @return Whether the drawer is open. */
    public boolean isOpen() {
        DrawerLayout drawer = mActivity.getDrawer();
        return drawer != null && drawer.isDrawerOpen(GravityCompat.START);
    }

    /** Lock or unlock the edge swipe of the drawer, like while the terminal is in copy mode. */
    public void setLockMode(int lockMode) {
        DrawerLayout drawer = mActivity.getDrawer();
        if (drawer != null) drawer.setDrawerLockMode(lockMode, GravityCompat.START);
    }

    /** Open the drawer, like from the edge handle, the terminal menu or a virtual key. */
    public void openDrawer() {
        DrawerLayout drawer = mActivity.getDrawer();
        if (drawer == null) return;

        // A locked drawer may not be opened by the layout, so it is unlocked for the programmatic open.
        if (drawer.getDrawerLockMode(GravityCompat.START) == DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);

        drawer.openDrawer(GravityCompat.START);
    }

    /**
     * Apply the color scheme of {@code session} to the drawer, so that it uses the background, the foreground
     * and the accent color of the terminal instead of the black and white of the app theme. A session that has
     * no emulator, like before the first session was started, falls back to the colors of the app theme.
     */
    public void updateColors(@Nullable TerminalSession session) {
        int[] colors = session != null && session.getEmulator() != null
            ? session.getEmulator().mColors.mCurrentColors : null;

        if (colors != null) {
            mBackgroundColor = colors[TextStyle.COLOR_INDEX_BACKGROUND];
            mForegroundColor = colors[TextStyle.COLOR_INDEX_FOREGROUND];
            // The `color4` of the palette (blue) is the accent color of the informational notification cards
            // too, so the whole fork uses the same accent color for the same kind of information.
            mAccentColor = DrawerColorUtils.visibleAccentColor(colors[4], mBackgroundColor);
        } else {
            boolean isLightTheme = !ThemeUtils.shouldEnableDarkTheme(mActivity,
                NightMode.getAppNightMode().getName());
            mBackgroundColor = isLightTheme ? Color.WHITE : Color.BLACK;
            mForegroundColor = DrawerColorUtils.contrastingTextColor(mBackgroundColor);
            mAccentColor = mForegroundColor;
        }

        // The details of a session are drawn in a dimmer color than its title, and the divider below the
        // header in a color that is barely visible, so that neither competes with the text.
        mSecondaryTextColor = DrawerColorUtils.withAlpha(mForegroundColor, 0xB3);

        // The row of the session that is shown in the terminal keeps a tint of the accent color, and the
        // ripple of a pressed row is drawn on top of it within a mask, so that it is a rounded flash of the
        // row instead of a block without a content.
        StateListDrawable rowContent = new StateListDrawable();
        rowContent.addState(new int[]{android.R.attr.state_activated},
            new ColorDrawable(DrawerColorUtils.blend(mBackgroundColor, mAccentColor, 0.22f)));
        rowContent.addState(StateSet.WILD_CARD, new ColorDrawable(Color.TRANSPARENT));

        mSessionRowBackground = new RippleDrawable(
            ColorStateList.valueOf(DrawerColorUtils.withAlpha(mAccentColor, 0x33)),
            rowContent, new ColorDrawable(Color.WHITE));

        applyColors();
    }

    /** Apply the colors that {@link #updateColors} computed to the views of the drawer. */
    private void applyColors() {
        View drawer = mActivity.findViewById(R.id.left_drawer);
        if (drawer == null) return;

        drawer.setBackgroundColor(mBackgroundColor);

        TextView titleView = mActivity.findViewById(R.id.drawer_title);
        if (titleView != null) titleView.setTextColor(mForegroundColor);

        TextView summaryView = mActivity.findViewById(R.id.drawer_summary);
        if (summaryView != null) summaryView.setTextColor(mSecondaryTextColor);

        View divider = mActivity.findViewById(R.id.drawer_header_divider);
        if (divider != null)
            divider.setBackgroundColor(DrawerColorUtils.withAlpha(mForegroundColor, 0x33));

        ImageButton settingsButton = mActivity.findViewById(R.id.settings_button);
        if (settingsButton != null) settingsButton.setColorFilter(mForegroundColor);

        ImageButton paletteButton = mActivity.findViewById(R.id.palette_button);
        if (paletteButton != null) paletteButton.setColorFilter(mForegroundColor);

        for (int id : new int[]{R.id.toggle_keyboard_button, R.id.new_session_button}) {
            View button = mActivity.findViewById(id);
            if (!(button instanceof MaterialButton)) continue;

            MaterialButton materialButton = (MaterialButton) button;
            materialButton.setTextColor(mForegroundColor);
            materialButton.setIconTint(ColorStateList.valueOf(mForegroundColor));
        }

        ListView sessionsList = mActivity.findViewById(R.id.terminal_sessions_list);
        if (sessionsList != null) sessionsList.setBackgroundColor(Color.TRANSPARENT);

        DrawerHandleView handle = mActivity.findViewById(R.id.drawer_handle);
        if (handle != null) handle.setHandleColor(accentWithConfiguredOpacity());

        updateSummary();

        // The rows that are already laid out keep the colors of the previous color scheme, and the accent
        // strip of the current session may have moved to another row.
        notifyRowsChanged();
    }

    /** @return The accent color at the opacity that the `drawer-handle-opacity` property configures. */
    private int accentWithConfiguredOpacity() {
        int alpha = Math.round(255f * mConfig.handleOpacityPercent / 100f);
        return DrawerColorUtils.withAlpha(mAccentColor, alpha);
    }

    /**
     * Set up the handle on the left edge of the terminal that opens the drawer, since a swipe from the edge is
     * the system back gesture on android 10 and later.
     */
    private void setUpHandle() {
        DrawerHandleView handle = mActivity.findViewById(R.id.drawer_handle);
        if (handle == null) return;

        handle.setOnOpenDrawerListener(this::openDrawer);

        // The handle is drawn under the drawer, so it is faded out while the drawer is dragged open, which
        // also makes it clear that the touch of the drawer belongs to the drawer.
        DrawerLayout drawer = mActivity.getDrawer();
        if (drawer != null) {
            drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                    handle.setAlpha(1f - slideOffset);
                }

                @Override
                public void onDrawerOpened(@NonNull View drawerView) {
                    // The details of a session may have changed while the drawer was closed.
                    updateSummary();
                    notifyRowsChanged();
                }
            });
        }

        // The position of the handle is a fraction of the height of the screen, so it is re-applied when the
        // height of the layout changes, like when the soft keyboard or the extra keys change it.
        View parent = (View) handle.getParent();
        if (parent != null) parent.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                              oldLeft, oldTop, oldRight, oldBottom) ->
            positionHandle());
    }

    /**
     * Show or hide the handle and enable or disable the edge swipe of the drawer layout, as the
     * `drawer-open-mode` property configures it, and apply the appearance of the handle.
     */
    private void applyConfig() {
        DrawerHandleView handle = mActivity.findViewById(R.id.drawer_handle);
        if (handle != null) {
            handle.setVisibility(mConfig.isHandleVisible() ? View.VISIBLE : View.GONE);
            handle.setHandleLengthDp(mConfig.handleLengthDp);
            // The opacity of the bar is a percentage of the accent color, so a low value keeps the terminal
            // readable behind it.
            handle.setHandleColor(accentWithConfiguredOpacity());
            positionHandle();
        }

        DrawerLayout drawer = mActivity.getDrawer();
        if (drawer == null) return;

        // The edge drag of the drawer layout is disabled when the system back gesture would steal it, since
        // it would close the app window instead of opening the drawer.
        drawer.setDrawerLockMode(mConfig.isEdgeSwipeEnabled()
            ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START);
    }

    /** Move the handle to the configured fraction of the height of the screen. */
    private void positionHandle() {
        View handle = mActivity.findViewById(R.id.drawer_handle);
        if (handle == null || !(handle.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;

        View parent = (View) handle.getParent();
        if (parent == null || parent.getHeight() <= 0) return;

        int handleHeight = handle.getHeight();
        int freeHeight = Math.max(0, parent.getHeight() - handleHeight);
        int topMargin = Math.round(freeHeight * mConfig.handlePositionPercent / 100f);

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) handle.getLayoutParams();
        layoutParams.topMargin = topMargin;
        // The parent is inset by the margin of the content, but the bar has to be flush with the screen edge.
        layoutParams.setMarginStart(-Math.round(ViewUtils.dpToPx(mActivity, 3)));
        handle.setLayoutParams(layoutParams);
    }

    /**
     * Show a summary of the current session in the header of the drawer, like the number of sessions and the
     * process and the working directory of the active one, so that the user can tell where they are before
     * opening the drawer.
     */
    public void updateSummary() {
        TextView summaryView = mActivity.findViewById(R.id.drawer_summary);
        if (summaryView == null) return;

        int sessionCount = mActivity.getTermuxService() != null
            ? mActivity.getTermuxService().getTermuxSessionsSize() : 0;
        if (sessionCount == 0) {
            summaryView.setVisibility(View.GONE);
            return;
        }

        List<String> parts = new ArrayList<>();
        parts.add(mActivity.getString(R.string.termux_drawer_summary_sessions, sessionCount));

        TerminalSession session = mActivity.getCurrentSession();
        if (session != null) {
            int pid = session.getPid();
            String processName = SessionDetails.getForegroundProcessName(pid);
            if (processName != null) parts.add(processName);
            String workingDirectory = SessionDetails.getWorkingDirectory(pid);
            if (workingDirectory != null) parts.add(workingDirectory);
        }

        summaryView.setText(SessionDetails.join(parts,
            " " + mActivity.getString(R.string.termux_drawer_summary_separator) + " "));
        summaryView.setVisibility(View.VISIBLE);
    }

    private void notifyRowsChanged() {
        if (mSessionsListAdapter != null) mSessionsListAdapter.notifyDataSetChanged();
    }

    /** @return The background color of the drawer, which follows the terminal color scheme. */
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    /** @return The color of the text of the drawer, which follows the terminal color scheme. */
    public int getForegroundColor() {
        return mForegroundColor;
    }

    /** @return The color of the details in the drawer, which is dimmer than the foreground. */
    public int getSecondaryTextColor() {
        return mSecondaryTextColor;
    }

    /** @return The accent color of the drawer, which marks the current session. */
    public int getAccentColor() {
        return mAccentColor;
    }

    /** @return The background of a session row, which tints the row of the current session when activated. */
    @Nullable
    public Drawable getSessionRowBackground() {
        return mSessionRowBackground;
    }
}
