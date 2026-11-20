package com.termux.shell.render

/**
 * 屏幕脏行区间。
 *
 * 目的：以行区间描述一次输出所造成的可见变化，使渲染层只重绘受影响的行，而不重绘整个屏幕。
 * 旧模块在每次输出后重绘整屏，高吞吐输出时会明显掉帧（详见 `docs/UPGRADE.md` 第 3 节 R1）。
 *
 * 约束：本类型不可变；区间为闭区间，且 [minRow] 不得大于 [maxRow]，空区间用 [EMPTY] 表示。
 */
class DirtyRows private constructor(val minRow: Int, val maxRow: Int) {

    val isEmpty: Boolean
        get() = maxRow < minRow

    /** 返回区间覆盖的行数；空区间返回 0。 */
    fun rowCount(): Int = if (isEmpty) 0 else maxRow - minRow + 1

    /** 返回与另一区间的最小覆盖区间。空区间不改变另一区间，并保持实例同一性。 */
    fun union(other: DirtyRows): DirtyRows = when {
        other === EMPTY || other.isEmpty -> this
        isEmpty -> other
        else -> of(minOf(minRow, other.minRow), maxOf(maxRow, other.maxRow))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DirtyRows) return false
        return minRow == other.minRow && maxRow == other.maxRow
    }

    override fun hashCode(): Int = 31 * minRow + maxRow

    override fun toString(): String =
        if (isEmpty) "DirtyRows(empty)" else "DirtyRows($minRow..$maxRow)"

    companion object {

        /** 空区间。任何区间与空区间合并均返回另一区间本身。 */
        val EMPTY = DirtyRows(Int.MAX_VALUE, -1)

        /**
         * 构造闭区间。
         *
         * @throws IllegalArgumentException 起始行大于结束行。
         */
        fun of(minRow: Int, maxRow: Int): DirtyRows {
            require(minRow <= maxRow) { "起始行不得大于结束行：$minRow > $maxRow" }
            return DirtyRows(minRow, maxRow)
        }
    }
}
