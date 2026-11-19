package com.termux.shell.pane;

import androidx.annotation.NonNull;

/**
 * 分割节点。
 *
 * <p>目的：描述一次分割的方向、比例与两个子节点。分割方向以「子节点的排列方向」定义，
 * 避免「横向分割/纵向分割」表述在界面与代码之间产生歧义。
 *
 * <p>约束：两个子节点均不可为空，且不得相同；比例被限制在 {@link PaneLayoutController#MIN_RATIO}
 * 与 {@link PaneLayoutController#MAX_RATIO} 之间。
 */
public final class PaneSplit extends PaneNode {

    /** 子节点左右排列，分割条为竖直线。 */
    public static final int ORIENTATION_HORIZONTAL = 0;

    /** 子节点上下排列，分割条为水平线。 */
    public static final int ORIENTATION_VERTICAL = 1;

    public static final float DEFAULT_RATIO = 0.5f;

    private final int mOrientation;
    private float mRatio;
    private PaneNode mFirst;
    private PaneNode mSecond;

    public PaneSplit(@NonNull String id, int orientation, @NonNull PaneNode first, @NonNull PaneNode second) {
        super(id);
        if (first == second) {
            throw new IllegalArgumentException("分割节点的两个子节点不得相同");
        }
        mOrientation = orientation;
        mFirst = first;
        mSecond = second;
        mRatio = DEFAULT_RATIO;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public float getRatio() {
        return mRatio;
    }

    void setRatioInternal(float ratio) {
        mRatio = ratio;
    }

    @NonNull
    public PaneNode getFirst() {
        return mFirst;
    }

    @NonNull
    public PaneNode getSecond() {
        return mSecond;
    }

    /**
     * 用新节点替换指定子节点。
     *
     * @return 是否发生替换；指定节点不是本节点的子节点时返回 false。
     */
    boolean replaceChild(@NonNull PaneNode oldChild, @NonNull PaneNode newChild) {
        if (mFirst == oldChild) {
            mFirst = newChild;
            return true;
        }
        if (mSecond == oldChild) {
            mSecond = newChild;
            return true;
        }
        return false;
    }

    /**
     * 返回另一个子节点。
     *
     * @throws IllegalArgumentException 指定节点不是本节点的子节点。
     */
    @NonNull
    PaneNode siblingOf(@NonNull PaneNode child) {
        if (mFirst == child) {
            return mSecond;
        }
        if (mSecond == child) {
            return mFirst;
        }
        throw new IllegalArgumentException("指定节点不是当前分割节点的子节点");
    }
}
