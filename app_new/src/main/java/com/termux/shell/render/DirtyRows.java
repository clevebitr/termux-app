package com.termux.shell.render;

import androidx.annotation.NonNull;

/**
 * 屏幕脏行区间。
 *
 * <p>目的：以行区间描述一次输出所造成的可见变化，使渲染层只重绘受影响的行，而不重绘整个屏幕。
 * 旧模块在每次输出后重绘整屏，高吞吐输出时会明显掉帧（详见 {@code docs/UPGRADE.md} 第 3 节 R1）。
 *
 * <p>约束：本类型不可变；区间为闭区间，且 {@code minRow} 不得大于 {@code maxRow}，空区间用
 * {@link #EMPTY} 表示。
 */
public final class DirtyRows {

    /** 空区间。任何区间与空区间合并均返回另一区间本身。 */
    public static final DirtyRows EMPTY = new DirtyRows(Integer.MAX_VALUE, -1);

    private final int mMinRow;
    private final int mMaxRow;

    private DirtyRows(int minRow, int maxRow) {
        mMinRow = minRow;
        mMaxRow = maxRow;
    }

    /**
     * 构造闭区间。
     *
     * @throws IllegalArgumentException 起始行大于结束行。
     */
    @NonNull
    public static DirtyRows of(int minRow, int maxRow) {
        if (minRow > maxRow) {
            throw new IllegalArgumentException("起始行不得大于结束行：" + minRow + " > " + maxRow);
        }
        return new DirtyRows(minRow, maxRow);
    }

    public boolean isEmpty() {
        return mMaxRow < mMinRow;
    }

    public int getMinRow() {
        return mMinRow;
    }

    public int getMaxRow() {
        return mMaxRow;
    }

    /**
     * 返回区间覆盖的行数；空区间返回 0。
     */
    public int rowCount() {
        return isEmpty() ? 0 : mMaxRow - mMinRow + 1;
    }

    /**
     * 返回与另一区间的最小覆盖区间。
     */
    @NonNull
    public DirtyRows union(@NonNull DirtyRows other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new DirtyRows(Math.min(mMinRow, other.mMinRow), Math.max(mMaxRow, other.mMaxRow));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DirtyRows)) {
            return false;
        }
        DirtyRows that = (DirtyRows) other;
        return mMinRow == that.mMinRow && mMaxRow == that.mMaxRow;
    }

    @Override
    public int hashCode() {
        return 31 * mMinRow + mMaxRow;
    }

    @NonNull
    @Override
    public String toString() {
        return isEmpty() ? "DirtyRows(empty)" : "DirtyRows(" + mMinRow + ".." + mMaxRow + ")";
    }
}
