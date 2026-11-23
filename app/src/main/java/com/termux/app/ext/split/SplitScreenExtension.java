package com.termux.app.ext.split;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionViewSlot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「分屏」作为可启用的扩展提供：两个窗格的排列、分隔条的拖拽与活动窗格的标记。
 *
 * <p>行为 — 启用时接管窗格容器、两个窗格与分隔条，创建 {@link TerminalSplitScreenController} 并装配拖拽；
 * 宿主决定何时分屏（副窗格的会话由宿主创建与挂载），但排列、比例与边框由扩展计算。禁用时扩展关闭分屏并
 * 回到单窗格布局，宿主随后忽略分屏请求。</p>
 *
 * <p>约束 — 扩展只拥有几何与状态，不拥有会话与终端视图本身：会话属于会话客户端，视图由宿主布局创建。
 * 因此 {@link #isEnabled()} 为 {@code false} 时不得再调用 {@link #updateLayout()} 之外的方法，避免在两窗格
 * 状态下留下错误布局。</p>
 */
public final class SplitScreenExtension implements Extension {

    /** The stable id of the extension. */
    public static final String ID = "split";

    @Nullable
    private TerminalSplitScreenController mController;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_split_extension_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_split_extension_summary;
    }

    @NonNull
    @Override
    public List<ExtensionViewSlot> slots() {
        return Collections.unmodifiableList(Arrays.asList(
            new ExtensionViewSlot(R.id.terminal_panes_container, "terminal_panes_container",
                "The container that holds the one or two panes"),
            new ExtensionViewSlot(R.id.terminal_split_divider, "terminal_split_divider",
                "The draggable divider between the panes")));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        LinearLayout panesContainer = context.view(R.id.terminal_panes_container);
        FrameLayout primaryPane = context.view(R.id.terminal_pane_0);
        FrameLayout secondaryPane = context.view(R.id.terminal_pane_1);
        View divider = context.view(R.id.terminal_split_divider);

        if (panesContainer == null || primaryPane == null || secondaryPane == null) {
            context.logWarn("The layout has no pane containers, split screen is not available");
            return;
        }

        mController = new TerminalSplitScreenController(context.activity(),
            new FrameLayout[]{primaryPane, secondaryPane}, panesContainer, divider);
        mController.setUpDividerTouchListener();
        context.logInfo("Split screen enabled");
    }

    @Override
    public void onDisable() {
        if (mController != null) {
            mController.setEnabled(false);
            mController.updateLayout();
            mController.updatePaneActiveStates(0);
        }
        mController = null;
    }

    /** @return Whether the two panes are shown; {@code false} while the extension is disabled. */
    public boolean isEnabled() {
        return mController != null && mController.isEnabled();
    }

    /**
     * Enable or disable the two panes.
     *
     * <p>The caller is responsible for attaching a session to the secondary pane before enabling the split and
     * for disposing of it after disabling, since sessions belong to the session client.</p>
     */
    public void setEnabled(boolean enabled) {
        if (mController == null) return;
        mController.setEnabled(enabled);
    }

    /** @return The orientation of the panes, either {@link LinearLayout#VERTICAL} or {@link LinearLayout#HORIZONTAL}. */
    public int getOrientation() {
        return mController != null ? mController.getOrientation() : LinearLayout.VERTICAL;
    }

    public void setOrientation(int orientation) {
        if (mController != null) mController.setOrientation(orientation);
    }

    /** @return The relative size of the first pane, so that it survives a recreation of the activity. */
    public float getPaneRatio() {
        return mController != null ? mController.getPaneRatio() : 0.5f;
    }

    public void setPaneRatio(float ratio) {
        if (mController != null) mController.setPaneRatio(ratio);
    }

    /** Apply the orientation and the sizes of the panes to their views. */
    public void updateLayout() {
        if (mController != null) mController.updateLayout();
    }

    /** Draw a border around the pane at {@code activeIndex}, so that the active pane can be told apart. */
    public void updatePaneActiveStates(int activeIndex) {
        if (mController != null) mController.updatePaneActiveStates(activeIndex);
    }
}
