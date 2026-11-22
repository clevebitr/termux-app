package com.termux.shell.pane

/**
 * 窗格树节点。
 *
 * 目的：以二叉树描述外壳的分割结构。叶子承载会话，分支描述分割方向与比例，
 * 与视图和会话均无直接依赖，因此可被序列化、单元测试与跨窗口传输。
 *
 * 约束：本类型及其实现均为不可变值；结构变更通过 [PaneLayout] 返回新树完成。
 */
sealed interface PaneNode {

    /** 节点标识。该标识在布局持久化中作为稳定键使用，分配后不得变更。 */
    val id: String
}

/** 窗格聚焦方向，用于方向键导航。 */
enum class PaneDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

/** 分割方向，按「子节点的排列方向」定义，避免「横向分割/纵向分割」表述产生歧义。 */
enum class SplitOrientation {
    /** 子节点左右排列，分割条为竖直线。 */
    HORIZONTAL,

    /** 子节点上下排列，分割条为水平线。 */
    VERTICAL,
}

/**
 * 叶子窗格。
 *
 * @param sessionId 承载的会话标识；为空表示尚未分配会话，界面显示占位内容。
 * @param scrollRow 恢复布局时的滚动位置。
 */
data class PaneLeaf(
    override val id: String,
    val sessionId: String? = null,
    val scrollRow: Int = 0,
) : PaneNode {

    val hasSession: Boolean
        get() = sessionId != null
}

/**
 * 分割节点。
 *
 * @param ratio 第一个子节点占可用空间的比例，取值被限制在 [MIN_RATIO] 与 [MAX_RATIO] 之间。
 */
data class PaneSplit(
    override val id: String,
    val orientation: SplitOrientation,
    val first: PaneNode,
    val second: PaneNode,
    val ratio: Float = DEFAULT_RATIO,
) : PaneNode {

    init {
        require(first.id != second.id) { "分割节点的两个子节点不得相同" }
    }

    companion object {
        const val DEFAULT_RATIO: Float = 0.5f

        /** 分割比例下限。低于该值时子窗格可显示字符数过少，失去可用性。 */
        const val MIN_RATIO: Float = 0.15f

        /** 分割比例上限。 */
        const val MAX_RATIO: Float = 0.85f
    }
}

/** 关闭叶子的结果：新的窗格树与应当获得焦点的叶子标识。 */
data class PaneCloseResult(val root: PaneNode, val focusLeafId: String)

/** 分割叶子的结果：新的窗格树与新创建的叶子标识。 */
data class PaneSplitResult(val root: PaneNode, val newLeafId: String)

