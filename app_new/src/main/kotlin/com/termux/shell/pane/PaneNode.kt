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

/**
 * 窗格树的纯函数操作。
 *
 * 目的：把结构变更从界面中剥离为不依赖 Android 的函数，便于单元测试与后续跨进程复用。
 *
 * 约束：全部函数不修改入参；非法请求（目标不存在、关闭最后一个叶子）返回 null。
 */
object PaneLayout {

    /** 按深度优先顺序返回全部叶子。 */
    fun leaves(node: PaneNode): List<PaneLeaf> = buildList { collectLeaves(node, this) }

    /** 返回第一个叶子；任何合法树都至少包含一个叶子。 */
    fun firstLeaf(node: PaneNode): PaneLeaf = firstLeafOrNull(node)
        ?: throw IllegalArgumentException("窗格树必须至少包含一个叶子节点")

    /** 对每个叶子应用变换并返回新树，用于绑定会话标识等结构内更新。 */
    fun mapLeaves(node: PaneNode, transform: (PaneLeaf) -> PaneLeaf): PaneNode = when (node) {
        is PaneLeaf -> transform(node)
        is PaneSplit -> node.copy(
            first = mapLeaves(node.first, transform),
            second = mapLeaves(node.second, transform),
        )
    }

    fun findLeaf(node: PaneNode, leafId: String): PaneLeaf? = when (node) {
        is PaneLeaf -> if (node.id == leafId) node else null
        is PaneSplit -> findLeaf(node.first, leafId) ?: findLeaf(node.second, leafId)
    }

    /** 返回树中全部节点标识的最大序号（形如 `pane-3`、`split-5`），用于分配新标识。 */
    fun maxNodeNumber(node: PaneNode): Int = when (node) {
        is PaneLeaf -> numericSuffix(node.id)
        is PaneSplit -> maxOf(numericSuffix(node.id), maxNodeNumber(node.first), maxNodeNumber(node.second))
    }

    /**
     * 把指定叶子替换为分割节点。
     *
     * @return 新树与新叶子标识；目标叶子不存在时返回 null。
     */
    fun split(root: PaneNode, leafId: String, orientation: SplitOrientation): PaneSplitResult? {
        val leaf = findLeaf(root, leafId) ?: return null
        val number = maxNodeNumber(root) + 1
        val newLeaf = PaneLeaf("pane-$number")
        val splitNode = PaneSplit(
            id = "split-$number",
            orientation = orientation,
            first = leaf,
            second = newLeaf,
        )
        val newRoot = replace(root, leafId, splitNode) ?: return null
        return PaneSplitResult(newRoot, newLeaf.id)
    }

    /**
     * 关闭指定叶子，由兄弟节点顶替父分割节点。
     *
     * @return 关闭结果；目标不存在或目标为最后一个叶子时返回 null。
     */
    fun close(root: PaneNode, leafId: String): PaneCloseResult? {
        if (root is PaneLeaf) {
            return null
        }
        val parent = findParent(root, leafId) ?: return null
        val sibling = if (parent.first.id == leafId) parent.second else parent.first
        val newRoot = replace(root, parent.id, sibling) ?: return null
        return PaneCloseResult(newRoot, firstLeaf(sibling).id)
    }

    /**
     * 设置分割比例，超出 [PaneSplit.MIN_RATIO] 与 [PaneSplit.MAX_RATIO] 时截断。
     *
     * @return 新的窗格树；目标不存在时返回 null。
     */
    fun setRatio(root: PaneNode, splitId: String, ratio: Float): PaneNode? {
        val clamped = clampRatio(ratio)
        return replace(root, splitId) { split ->
            if (split is PaneSplit) split.copy(ratio = clamped) else split
        }
    }

    /**
     * 按方向查找相邻叶子。
     *
     * 行为：先把窗格树换算为归一化矩形，再取位于目标方向、且与当前叶子在正交轴上重叠最大的叶子；
     * 重叠相同则以距离最近者优先。用于方向键在窗格之间移动焦点。
     *
     * @return 目标叶子标识；该方向没有相邻叶子时返回 null。
     */
    fun focusInDirection(root: PaneNode, focusedLeafId: String, direction: PaneDirection): String? {
        val rects = HashMap<String, Rect>()
        collectRects(root, 0f, 0f, 1f, 1f, rects)
        val current = rects[focusedLeafId] ?: return null
        val tolerance = 0.0005f
        var best: String? = null
        var bestOverlap = -1f
        var bestDistance = Float.MAX_VALUE
        for ((id, rect) in rects) {
            if (id == focusedLeafId) {
                continue
            }
            val ahead = when (direction) {
                PaneDirection.LEFT -> rect.right <= current.left + tolerance
                PaneDirection.RIGHT -> rect.left >= current.right - tolerance
                PaneDirection.UP -> rect.bottom <= current.top + tolerance
                PaneDirection.DOWN -> rect.top >= current.bottom - tolerance
            }
            if (!ahead) {
                continue
            }
            val overlap = when (direction) {
                PaneDirection.LEFT, PaneDirection.RIGHT ->
                    minOf(current.bottom, rect.bottom) - maxOf(current.top, rect.top)
                PaneDirection.UP, PaneDirection.DOWN ->
                    minOf(current.right, rect.right) - maxOf(current.left, rect.left)
            }
            val distance = when (direction) {
                PaneDirection.LEFT -> current.left - rect.right
                PaneDirection.RIGHT -> rect.left - current.right
                PaneDirection.UP -> current.top - rect.bottom
                PaneDirection.DOWN -> rect.top - current.bottom
            }
            val better = overlap > bestOverlap + tolerance ||
                (kotlin.math.abs(overlap - bestOverlap) <= tolerance && distance < bestDistance)
            if (better) {
                best = id
                bestOverlap = overlap
                bestDistance = distance
            }
        }
        return best
    }

    /** 归一化矩形，坐标取值 0 至 1。 */
    private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun collectRects(
        node: PaneNode,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        out: MutableMap<String, Rect>,
    ) {
        when (node) {
            is PaneLeaf -> out[node.id] = Rect(left, top, left + width, top + height)
            is PaneSplit -> when (node.orientation) {
                SplitOrientation.HORIZONTAL -> {
                    val firstWidth = width * node.ratio
                    collectRects(node.first, left, top, firstWidth, height, out)
                    collectRects(node.second, left + firstWidth, top, width - firstWidth, height, out)
                }

                SplitOrientation.VERTICAL -> {
                    val firstHeight = height * node.ratio
                    collectRects(node.first, left, top, width, firstHeight, out)
                    collectRects(node.second, left, top + firstHeight, width, height - firstHeight, out)
                }
            }
        }
    }

    /** 把比例截断到允许区间。 */
    fun clampRatio(ratio: Float): Float = ratio.coerceIn(PaneSplit.MIN_RATIO, PaneSplit.MAX_RATIO)

    /**
     * 在叶子列表中循环迁移焦点。
     *
     * @return 新的焦点叶子标识；叶子不足两个时返回 null。
     */
    fun focusRelative(leaves: List<PaneLeaf>, focusedLeafId: String, delta: Int): String? {
        if (leaves.size < 2 || delta == 0) {
            return null
        }
        val current = leaves.indexOfFirst { it.id == focusedLeafId }.coerceAtLeast(0)
        val target = ((current + delta) % leaves.size + leaves.size) % leaves.size
        return leaves[target].id
    }

    /** 用新节点替换指定标识的节点。 */
    fun replace(root: PaneNode, nodeId: String, transform: (PaneNode) -> PaneNode): PaneNode? = when (root) {
        is PaneLeaf -> if (root.id == nodeId) transform(root) else null
        is PaneSplit -> {
            if (root.id == nodeId) {
                transform(root)
            } else {
                val newFirst = replace(root.first, nodeId, transform)
                val newSecond = if (newFirst == null) replace(root.second, nodeId, transform) else null
                when {
                    newFirst != null -> root.copy(first = newFirst)
                    newSecond != null -> root.copy(second = newSecond)
                    else -> null
                }
            }
        }
    }

    private fun replace(root: PaneNode, nodeId: String, replacement: PaneNode): PaneNode? =
        replace(root, nodeId) { replacement }

    private fun findParent(node: PaneNode, childId: String): PaneSplit? = when (node) {
        is PaneLeaf -> null
        is PaneSplit -> when {
            node.first.id == childId || node.second.id == childId -> node
            else -> findParent(node.first, childId) ?: findParent(node.second, childId)
        }
    }

    private fun firstLeafOrNull(node: PaneNode): PaneLeaf? = when (node) {
        is PaneLeaf -> node
        is PaneSplit -> firstLeafOrNull(node.first)
    }

    private fun collectLeaves(node: PaneNode, out: MutableList<PaneLeaf>) {
        when (node) {
            is PaneLeaf -> out += node
            is PaneSplit -> {
                collectLeaves(node.first, out)
                collectLeaves(node.second, out)
            }
        }
    }

    private fun numericSuffix(id: String): Int = id.substringAfterLast('-').toIntOrNull() ?: 0
}
