package com.termux.shell.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 窗格树控制器。
 *
 * <p>目的：集中维护窗格树的结构性操作（分割、关闭、调整比例、焦点迁移），使视图层只负责绘制与命中测试。
 *
 * <p>约束：
 * <ul>
 *     <li>非线程安全，仅允许在主线程调用；</li>
 *     <li>树中至少保留一个叶子节点，关闭最后一个叶子的请求返回 false；</li>
 *     <li>叶子数量受 {@link #MAX_LEAF_COUNT} 限制，达到上限后分割请求返回 null。</li>
 * </ul>
 */
public final class PaneLayoutController {

    /** 分割比例下限。低于该值时子窗格可显示字符数过少，失去可用性。 */
    public static final float MIN_RATIO = 0.15f;

    /** 分割比例上限。 */
    public static final float MAX_RATIO = 0.85f;

    /** 单窗口内允许的最大叶子数量。上限用于约束内存占用与最小可读列宽。 */
    public static final int MAX_LEAF_COUNT = 8;

    private PaneNode mRoot;
    private PaneLeaf mFocusedLeaf;
    private int mNextNodeIndex = 1;

    public PaneLayoutController() {
        PaneLeaf leaf = new PaneLeaf("pane-1");
        mRoot = leaf;
        mFocusedLeaf = leaf;
    }

    @NonNull
    public PaneNode getRoot() {
        return mRoot;
    }

    @NonNull
    public PaneLeaf getFocusedLeaf() {
        return mFocusedLeaf;
    }

    public void setFocusedLeaf(@NonNull PaneLeaf leaf) {
        if (collectLeaves().contains(leaf)) {
            mFocusedLeaf = leaf;
        }
    }

    /**
     * 按深度优先顺序返回全部叶子节点。
     */
    @NonNull
    public List<PaneLeaf> collectLeaves() {
        List<PaneLeaf> leaves = new ArrayList<>();
        collect(mRoot, leaves);
        return leaves;
    }

    public int getLeafCount() {
        return collectLeaves().size();
    }

    /**
     * 分割指定叶子。
     *
     * @param leaf        待分割的叶子，必须属于当前树。
     * @param orientation {@link PaneSplit#ORIENTATION_HORIZONTAL} 或 {@link PaneSplit#ORIENTATION_VERTICAL}。
     * @return 新建的叶子；叶子不属于当前树或已达数量上限时返回 null。
     */
    @Nullable
    public PaneLeaf split(@NonNull PaneLeaf leaf, int orientation) {
        if (orientation != PaneSplit.ORIENTATION_HORIZONTAL && orientation != PaneSplit.ORIENTATION_VERTICAL) {
            throw new IllegalArgumentException("未知的分割方向：" + orientation);
        }
        if (!collectLeaves().contains(leaf) || getLeafCount() >= MAX_LEAF_COUNT) {
            return null;
        }

        PaneLeaf newLeaf = new PaneLeaf("pane-" + (++mNextNodeIndex));
        PaneSplit split = new PaneSplit("split-" + mNextNodeIndex, orientation, leaf, newLeaf);

        if (!replaceNode(leaf, split)) {
            return null;
        }
        mFocusedLeaf = newLeaf;
        return newLeaf;
    }

    /**
     * 关闭指定叶子。
     *
     * <p>关闭后由兄弟节点顶替父分割节点的位置；焦点迁移至兄弟子树内最左侧的叶子。
     *
     * @return 是否实际关闭。目标为最后一个叶子或不属于当前树时返回 false。
     */
    public boolean close(@NonNull PaneLeaf leaf) {
        if (mRoot == leaf) {
            return false;
        }
        PaneNode parent = findParent(mRoot, leaf);
        if (!(parent instanceof PaneSplit)) {
            return false;
        }
        PaneSplit parentSplit = (PaneSplit) parent;
        PaneNode sibling = parentSplit.siblingOf(leaf);
        if (!replaceNode(parentSplit, sibling)) {
            return false;
        }
        if (mFocusedLeaf == leaf) {
            List<PaneLeaf> leaves = new ArrayList<>();
            collect(sibling, leaves);
            mFocusedLeaf = leaves.get(0);
        }
        return true;
    }

    /**
     * 设置分割比例，超出范围时按上下限截断。
     *
     * @return 比例是否发生变化。
     */
    public boolean setRatio(@NonNull PaneSplit split, float ratio) {
        float clamped = Math.min(MAX_RATIO, Math.max(MIN_RATIO, ratio));
        if (Math.abs(clamped - split.getRatio()) < 0.0001f) {
            return false;
        }
        split.setRatioInternal(clamped);
        return true;
    }

    /**
     * 用新树替换当前树，用于布局恢复。调用方保证新树合法。
     */
    public void restore(@NonNull PaneNode root) {
        List<PaneLeaf> leaves = new ArrayList<>();
        collect(root, leaves);
        if (leaves.isEmpty()) {
            throw new IllegalArgumentException("恢复的窗格树必须至少包含一个叶子节点");
        }
        mRoot = root;
        mFocusedLeaf = leaves.get(0);
    }

    private boolean replaceNode(@NonNull PaneNode oldNode, @NonNull PaneNode newNode) {
        if (mRoot == oldNode) {
            mRoot = newNode;
            return true;
        }
        PaneNode parent = findParent(mRoot, oldNode);
        if (parent instanceof PaneSplit) {
            return ((PaneSplit) parent).replaceChild(oldNode, newNode);
        }
        return false;
    }

    @Nullable
    private static PaneNode findParent(@NonNull PaneNode current, @NonNull PaneNode target) {
        if (!(current instanceof PaneSplit)) {
            return null;
        }
        PaneSplit split = (PaneSplit) current;
        if (split.getFirst() == target || split.getSecond() == target) {
            return split;
        }
        PaneNode found = findParent(split.getFirst(), target);
        if (found != null) {
            return found;
        }
        return findParent(split.getSecond(), target);
    }

    private static void collect(@NonNull PaneNode node, @NonNull List<PaneLeaf> out) {
        if (node instanceof PaneLeaf) {
            out.add((PaneLeaf) node);
            return;
        }
        PaneSplit split = (PaneSplit) node;
        collect(split.getFirst(), out);
        collect(split.getSecond(), out);
    }
}
