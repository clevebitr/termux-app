package com.termux.shell.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.termux.shell.R;
import com.termux.shell.ShellApplication;
import com.termux.shell.commands.CommandCatalog;
import com.termux.shell.commands.ShellCommand;
import com.termux.shell.config.SettingsRepository;
import com.termux.shell.config.SettingsSpec;
import com.termux.shell.input.ShellAction;
import com.termux.shell.input.ShellInputRouter;
import com.termux.shell.log.ShellLog;
import com.termux.shell.pane.PaneHostView;
import com.termux.shell.pane.PaneLayoutController;
import com.termux.shell.pane.PaneLeaf;
import com.termux.shell.pane.PaneSplit;
import com.termux.shell.tabs.TabWorkspace;
import com.termux.shell.tabs.TabWorkspaceCodec;
import com.termux.shell.tabs.WorkspaceTab;

import java.util.List;

/**
 * 外壳窗口宿主。
 *
 * <p>目的：作为单个窗口的唯一入口，负责生命周期、窗口内边距、窗口级操作（新建窗口）与布局装配，
 * 并把标签与窗格结构交给 {@link TabWorkspace}、{@link LayoutHost} 与 {@link PaneHostView}。
 *
 * <p>边界：
 * <ul>
 *     <li>本类不持有会话状态，会话由 {@code SessionHost} 管理；</li>
 *     <li>本类不实现终端渲染与键序编码，二者分别由渲染层与输入层承担；</li>
 *     <li>骨架阶段窗格内显示占位内容，终端视图挂载在后续里程碑完成。</li>
 * </ul>
 *
 * <p>交互模型（对齐 Windows Terminal）：一个窗口一条标签栏，每个标签持有独立的窗格树；
 * 分屏、关闭窗格与焦点迁移都作用于当前标签。快捷键经 {@link ShellInputRouter} 命中后进入
 * {@link #executeAction(String)}，与命令面板、溢出菜单共用同一套动作实现。
 *
 * <p>关于多窗口：清单中声明 {@code documentLaunchMode="always"}，使每次「新建窗口」产生独立任务，
 * 从而支持桌面窗口模式与分屏模式下的并行窗口（详见 {@code docs/UPGRADE.md} 第 3 节 W1）。
 */
public final class ShellActivity extends AppCompatActivity
    implements CommandCatalog.Actions, CommandCatalog.State {

    private static final String SUB_TAG = "ShellActivity";
    private static final String STATE_WORKSPACE = "workspace";

    private final TabWorkspace mWorkspace = new TabWorkspace();
    private final ShellInputRouter mInputRouter = new ShellInputRouter();

    private final TabStripView.Listener mTabStripListener = new TabStripView.Listener() {
        @Override
        public void onTabSelected(int index) {
            if (mWorkspace.select(index)) {
                refreshUi();
            }
        }

        @Override
        public void onTabClosed(int index) {
            closeTabAt(index);
        }

        @Override
        public void onTabLongPressed(int index, @NonNull View anchor) {
            showTabMenu(index, anchor);
        }

        @Override
        public void onNewTabRequested() {
            newTab();
        }
    };

    private LayoutHost mLayoutHost;
    private TabStripView mTabStripView;
    private PaneHostView mPaneHostView;
    private MaterialButton mPaletteButton;

    private ShellViewOptions mViewOptions;
    private boolean mTabBarVisible;
    private boolean mCommandPaletteEnabled;

    @Nullable
    private CommandPaletteController mCommandPalette;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_shell);

        SettingsRepository settings = ShellApplication.settings();
        mTabBarVisible = settings.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true);
        mCommandPaletteEnabled = settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true);
        mViewOptions = ShellViewOptions.fromSettings(settings, mTabBarVisible);

        restoreWorkspace(savedInstanceState);
        if (mWorkspace.isEmpty()) {
            createFirstTab();
        }

        ViewGroup root = findViewById(R.id.shell_root);
        TextView railSummary = findViewById(R.id.shell_rail_summary);
        ViewGroup content = findViewById(R.id.shell_content);
        View tabBar = findViewById(R.id.shell_tab_bar);
        TextView header = findViewById(R.id.shell_header);
        mTabStripView = findViewById(R.id.shell_tab_strip);
        mPaneHostView = findViewById(R.id.shell_pane_host);
        mPaletteButton = findViewById(R.id.shell_action_palette);

        mLayoutHost = new LayoutHost(root, findViewById(R.id.shell_rail), railSummary, content,
            tabBar, header, mTabStripView, mPaneHostView);
        mTabStripView.setListener(mTabStripListener);

        bindActions();
        configureInputRouter();
        applyWindowInsets();
        refreshUi();
        updateSystemBarsAppearance();

        ShellLog.info(SUB_TAG, "窗口已创建，档位=" + WindowSizeResolver.currentWidthClass(this)
            + "，" + mWorkspace.describeState());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_WORKSPACE, TabWorkspaceCodec.encode(mWorkspace));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfiguration) {
        super.onConfigurationChanged(newConfiguration);
        applyThemeColors();
        refreshUi();
        updateSystemBarsAppearance();
    }

    @Override
    protected void onDestroy() {
        if (mCommandPalette != null) {
            mCommandPalette.release();
            mCommandPalette = null;
        }
        super.onDestroy();
    }

    /**
     * 快捷键优先于视图默认处理。
     *
     * <p>说明：只在按下事件上查询键位表；未命中时交回系统分发，保证终端视图与文本选择等行为不受影响。
     */
    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && mInputRouter.onKeyDown(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void restoreWorkspace(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String encoded = savedInstanceState.getString(STATE_WORKSPACE);
        TabWorkspaceCodec.DecodedWorkspace decoded = TabWorkspaceCodec.decode(encoded);
        if (decoded == null) {
            ShellLog.warn(SUB_TAG, "标签工作区恢复失败，回退到单标签布局", null);
            return;
        }
        mWorkspace.restore(decoded.getTabs(), decoded.getActiveIndex());
    }

    private void createFirstTab() {
        WorkspaceTab tab = mWorkspace.newTab(getString(R.string.shell_tab_title, 1));
        if (tab == null) {
            ShellLog.warn(SUB_TAG, "首个标签创建失败", null);
        }
    }

    /**
     * 应用系统栏内边距。
     *
     * <p>说明：外壳自行绘制背景，因此关闭系统栏自动内边距，改为把内边距施加在根视图上。
     * 该处理在顶部与底部系统栏高度变化时同样生效，包括手势导航与桌面窗口标题栏。
     */
    private void applyWindowInsets() {
        ViewGroup root = findViewById(R.id.shell_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }

    private void bindActions() {
        mPaletteButton.setOnClickListener(view -> openCommandPalette());
        findViewById(R.id.shell_action_new_tab).setOnClickListener(view -> newTab());
        findViewById(R.id.shell_action_overflow).setOnClickListener(this::showWindowMenu);
    }

    private void configureInputRouter() {
        SettingsRepository settings = ShellApplication.settings();
        mInputRouter.setKeymap(ShellApplication.keymap());
        mInputRouter.setShellShortcutsEnabled(
            settings.getBoolean(SettingsSpec.KEY_KEYMAP_ENABLED, true));
        mInputRouter.setHost(this::executeAction);
    }

    /**
     * 按当前状态重新装配界面。所有结构性操作完成后必须调用。
     */
    private void refreshUi() {
        WindowWidthClass widthClass = WindowSizeResolver.currentWidthClass(this);
        mPaletteButton.setVisibility(mCommandPaletteEnabled ? View.VISIBLE : View.GONE);
        mViewOptions = mViewOptions.withTabBarVisible(mTabBarVisible);
        mLayoutHost.apply(widthClass, mWorkspace, mViewOptions);
        mTabStripView.refreshThemeColors();
        mPaneHostView.refreshThemeColors();
    }

    private void applyThemeColors() {
        mTabStripView.refreshThemeColors();
        mPaneHostView.refreshThemeColors();
    }

    /**
     * 按当前日夜模式设置系统栏图标明暗，使浅色背景下图标为深色。
     */
    private void updateSystemBarsAppearance() {
        boolean nightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), decorView);
        controller.setAppearanceLightStatusBars(!nightMode);
        controller.setAppearanceLightNavigationBars(!nightMode);
    }

    /**
     * 执行外壳动作。
     *
     * <p>说明：快捷键、命令面板与菜单三条入口共用本方法，保证同一动作的行为一致。
     *
     * @return 动作是否被识别。
     */
    private boolean executeAction(@NonNull String action) {
        switch (action) {
            case ShellAction.NEW_TAB:
                newTab();
                return true;
            case ShellAction.CLOSE_TAB:
                closeActiveTab();
                return true;
            case ShellAction.NEXT_TAB:
                selectRelativeTab(1);
                return true;
            case ShellAction.PREVIOUS_TAB:
                selectRelativeTab(-1);
                return true;
            case ShellAction.MOVE_TAB_LEFT:
                moveActiveTab(-1);
                return true;
            case ShellAction.MOVE_TAB_RIGHT:
                moveActiveTab(1);
                return true;
            case ShellAction.SPLIT_RIGHT:
                splitFocusedPane(PaneSplit.ORIENTATION_HORIZONTAL);
                return true;
            case ShellAction.SPLIT_DOWN:
                splitFocusedPane(PaneSplit.ORIENTATION_VERTICAL);
                return true;
            case ShellAction.CLOSE_PANE:
                closeFocusedPane();
                return true;
            case ShellAction.FOCUS_NEXT_PANE:
                focusRelativePane(true);
                return true;
            case ShellAction.FOCUS_PREVIOUS_PANE:
                focusRelativePane(false);
                return true;
            case ShellAction.NEW_WINDOW:
                openNewWindow();
                return true;
            case ShellAction.COMMAND_PALETTE:
                openCommandPalette();
                return true;
            case ShellAction.TOGGLE_TAB_BAR:
                toggleTabBar();
                return true;
            case ShellAction.RELOAD_CONFIG:
                reloadConfig();
                return true;
            default:
                ShellLog.warn(SUB_TAG, "未识别的外壳动作：" + action, null);
                return false;
        }
    }

    @Override
    public void newTab() {
        WorkspaceTab tab = mWorkspace.newTab(getString(R.string.shell_tab_title, mWorkspace.size() + 1));
        if (tab == null) {
            toast(R.string.shell_tab_limit);
            return;
        }
        refreshUi();
    }

    @Override
    public void closeTab() {
        closeActiveTab();
    }

    @Override
    public void nextTab() {
        selectRelativeTab(1);
    }

    @Override
    public void previousTab() {
        selectRelativeTab(-1);
    }

    @Override
    public void moveTabLeft() {
        moveActiveTab(-1);
    }

    @Override
    public void moveTabRight() {
        moveActiveTab(1);
    }

    @Override
    public void splitRight() {
        splitFocusedPane(PaneSplit.ORIENTATION_HORIZONTAL);
    }

    @Override
    public void splitDown() {
        splitFocusedPane(PaneSplit.ORIENTATION_VERTICAL);
    }

    @Override
    public void closePane() {
        closeFocusedPane();
    }

    @Override
    public void focusNextPane() {
        focusRelativePane(true);
    }

    @Override
    public void focusPreviousPane() {
        focusRelativePane(false);
    }

    @Override
    public void newWindow() {
        openNewWindow();
    }

    @Override
    public void toggleTabBar() {
        mTabBarVisible = !mTabBarVisible;
        refreshUi();
    }

    @Override
    public void reloadConfig() {
        if (!ShellApplication.reloadConfig()) {
            toast(R.string.shell_config_reload_failed);
            return;
        }
        SettingsRepository settings = ShellApplication.settings();
        mCommandPaletteEnabled = settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true);
        mTabBarVisible = settings.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true);
        mViewOptions = ShellViewOptions.fromSettings(settings, mTabBarVisible);
        mInputRouter.setKeymap(ShellApplication.keymap());
        mInputRouter.setShellShortcutsEnabled(settings.getBoolean(SettingsSpec.KEY_KEYMAP_ENABLED, true));
        refreshUi();
        toast(R.string.shell_config_reloaded);
    }

    @Override
    public boolean canCloseTab() {
        return mWorkspace.size() > 1;
    }

    @Override
    public boolean canSplitPane() {
        PaneLayoutController panes = activePanes();
        return panes != null && panes.getLeafCount() < PaneLayoutController.MAX_LEAF_COUNT;
    }

    @Override
    public boolean canClosePane() {
        PaneLayoutController panes = activePanes();
        return panes != null && panes.getLeafCount() > 1;
    }

    private void closeTabAt(int index) {
        if (!mWorkspace.close(index)) {
            toast(R.string.shell_tab_close_denied);
            return;
        }
        refreshUi();
    }

    private void closeActiveTab() {
        closeTabAt(mWorkspace.getActiveIndex());
    }

    private void selectRelativeTab(int offset) {
        if (mWorkspace.selectRelative(offset)) {
            refreshUi();
        }
    }

    private void moveActiveTab(int offset) {
        int from = mWorkspace.getActiveIndex();
        if (mWorkspace.move(from, from + offset)) {
            refreshUi();
        }
    }

    private void splitFocusedPane(int orientation) {
        PaneLayoutController panes = activePanes();
        if (panes == null) {
            return;
        }
        PaneLeaf newLeaf = panes.split(panes.getFocusedLeaf(), orientation);
        if (newLeaf == null) {
            toast(R.string.shell_pane_limit);
            return;
        }
        refreshUi();
    }

    private void closeFocusedPane() {
        PaneLayoutController panes = activePanes();
        if (panes == null) {
            return;
        }
        if (!panes.close(panes.getFocusedLeaf())) {
            toast(R.string.shell_pane_close_denied);
            return;
        }
        refreshUi();
    }

    private void focusRelativePane(boolean forward) {
        PaneLayoutController panes = activePanes();
        if (panes == null) {
            return;
        }
        boolean changed = forward ? panes.focusNext() : panes.focusPrevious();
        if (changed) {
            mPaneHostView.invalidate();
        }
    }

    /**
     * 新建独立窗口。
     *
     * <p>说明：{@code FLAG_ACTIVITY_NEW_DOCUMENT} 与 {@code FLAG_ACTIVITY_MULTIPLE_TASK} 组合使用，
     * 使新窗口在最近任务中独立出现，并可在桌面窗口模式下与前一个窗口并排显示。
     */
    private void openNewWindow() {
        Intent intent = new Intent(this, ShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent);
    }

    private void openCommandPalette() {
        if (!mCommandPaletteEnabled) {
            ShellLog.debug(SUB_TAG, "命令面板已在配置中禁用");
            return;
        }
        ShellLog.debug(SUB_TAG, "打开命令面板");
        getCommandPalette().show(buildCommands());
    }

    @NonNull
    private List<ShellCommand> buildCommands() {
        return CommandCatalog.create(this, ShellApplication.keymap(), this, this);
    }

    @NonNull
    private CommandPaletteController getCommandPalette() {
        if (mCommandPalette == null) {
            mCommandPalette = new CommandPaletteController(this);
        }
        return mCommandPalette;
    }

    private void showWindowMenu(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_shell_actions);
        menu.setOnMenuItemClickListener(this::onWindowMenuItem);
        menu.show();
    }

    private boolean onWindowMenuItem(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_new_tab) {
            return executeAction(ShellAction.NEW_TAB);
        }
        if (itemId == R.id.action_split_right) {
            return executeAction(ShellAction.SPLIT_RIGHT);
        }
        if (itemId == R.id.action_split_down) {
            return executeAction(ShellAction.SPLIT_DOWN);
        }
        if (itemId == R.id.action_close_pane) {
            return executeAction(ShellAction.CLOSE_PANE);
        }
        if (itemId == R.id.action_toggle_tab_bar) {
            return executeAction(ShellAction.TOGGLE_TAB_BAR);
        }
        if (itemId == R.id.action_command_palette) {
            return executeAction(ShellAction.COMMAND_PALETTE);
        }
        if (itemId == R.id.action_new_window) {
            return executeAction(ShellAction.NEW_WINDOW);
        }
        if (itemId == R.id.action_reload_config) {
            return executeAction(ShellAction.RELOAD_CONFIG);
        }
        return false;
    }

    private void showTabMenu(int index, @NonNull View anchor) {
        if (index < 0 || index >= mWorkspace.size()) {
            return;
        }
        // 长按标签先选中，再弹出菜单，使菜单动作作用于用户长按的标签。
        if (mWorkspace.select(index)) {
            refreshUi();
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_tab_actions);
        menu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.tab_action_new) {
                return executeAction(ShellAction.NEW_TAB);
            }
            if (itemId == R.id.tab_action_move_left) {
                return executeAction(ShellAction.MOVE_TAB_LEFT);
            }
            if (itemId == R.id.tab_action_move_right) {
                return executeAction(ShellAction.MOVE_TAB_RIGHT);
            }
            if (itemId == R.id.tab_action_close) {
                return executeAction(ShellAction.CLOSE_TAB);
            }
            return false;
        });
        menu.show();
    }

    @Nullable
    private PaneLayoutController activePanes() {
        WorkspaceTab activeTab = mWorkspace.getActiveTab();
        return activeTab == null ? null : activeTab.getPanes();
    }

    private void toast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }
}
