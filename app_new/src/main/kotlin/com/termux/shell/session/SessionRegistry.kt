package com.termux.shell.session

import com.termux.shell.pane.PaneLeaf
import com.termux.shell.tabs.TabWorkspace

/**
 * 会话状态。
 *
 * 目的：区分「尚未建立进程的占位会话」与「正在运行」「已退出」三种情况，使会话列表在会话宿主
 * 接入前后都能给出准确的功能可用性说明。
 */
enum class SessionState {
    /** 已占据窗格但尚未分配会话进程，通常出现在会话宿主接入之前或布局恢复之后。 */
    PENDING,

    /** 会话进程正在运行。 */
    RUNNING,

    /** 会话进程已结束，记录仅用于查看与重新启动。 */
    EXITED,
}

/**
 * 会话记录。
 *
 * 目的：把「进程」与「视口」两种身份分开描述。一条记录可以附着在某个窗格上（[leafId] 与 [tabId]
 * 非空），也可以处于脱离状态（两者为空），从而支持关闭标签后进程继续存在的桌面习惯。
 *
 * 约束：本类型不可变；[id] 在会话存活期间不得变更。
 *
 * @param id 会话标识；占位会话使用 [SessionRegistry.PENDING_PREFIX] 前缀。
 * @param tabTitle 附着标签的标题；脱离时为最后附着的标签标题，未知时为空字符串。
 * @param title 会话自身标题（通常是前台进程名），尚未分配时为 null。
 */
data class SessionRecord(
    val id: String,
    val leafId: String?,
    val tabId: String?,
    val tabTitle: String,
    val title: String?,
    val workingDirectory: String?,
    val state: SessionState,
) {

    /** 是否附着在某个窗格上。 */
    val isAttached: Boolean
        get() = leafId != null && tabId != null

    /** 是否为尚未分配会话进程的占位记录。 */
    val isPending: Boolean
        get() = state == SessionState.PENDING
}

/**
 * 会话注册表。
 *
 * 目的：为侧栏与手机端抽屉提供「以会话为单位」的列表视图，使标签栏只负责布局、注册表只负责进程，
 * 消除两者在信息展示上的重复（职责划分见 `docs/UPGRADE.md` 第 13.4 节）。
 *
 * 行为：
 * - 记录按「标签顺序 + 窗格顺序」排列，脱离会话排在末尾；
 * - 由 [fromWorkspace] 派生的记录只包含工作区中已占据窗格的会话；
 * - 全部查询为只读操作，不修改工作区。
 *
 * 约束：本类型不可变且不依赖 Android 运行时，可在 JVM 单元测试中直接构造。
 */
class SessionRegistry private constructor(val records: List<SessionRecord>) {

    /** 会话总数。 */
    val size: Int
        get() = records.size

    /** 附着在窗格上的会话。 */
    fun attached(): List<SessionRecord> = records.filter { it.isAttached }

    /** 已脱离视口的会话。 */
    fun detached(): List<SessionRecord> = records.filterNot { it.isAttached }

    /** 按标识查找会话；不存在时返回 null。 */
    fun find(id: String): SessionRecord? = records.firstOrNull { it.id == id }

    /** 按标签分组，键为标签标识，值为该标签内的会话；保持原有顺序。 */
    fun byTab(): Map<String, List<SessionRecord>> =
        attached().groupBy { record -> record.tabId.orEmpty() }

    /** 返回替换后的注册表；目标不存在时返回自身。 */
    fun replace(record: SessionRecord): SessionRegistry {
        if (records.none { it.id == record.id }) {
            return this
        }
        return SessionRegistry(records.map { if (it.id == record.id) record else it })
    }

    /** 返回追加记录后的注册表；同标识记录会被替换。 */
    fun upsert(record: SessionRecord): SessionRegistry =
        SessionRegistry(records.filterNot { it.id == record.id } + record)

    /** 返回移除指定会话后的注册表。 */
    fun remove(id: String): SessionRegistry = SessionRegistry(records.filterNot { it.id == id })

    companion object {

        /** 占位会话标识前缀。 */
        const val PENDING_PREFIX = "pending:"

        /** 空注册表。 */
        fun empty(): SessionRegistry = SessionRegistry(emptyList())

        /** 用给定记录构造注册表，保持传入顺序。 */
        fun of(records: List<SessionRecord>): SessionRegistry = SessionRegistry(records)

        /**
         * 从标签工作区派生注册表。
         *
         * 行为：按标签与窗格的既有顺序逐条生成记录；已绑定会话标识的叶子生成运行中记录，
         * 未绑定的叶子生成占位记录，其标题为空，由界面按占位文案呈现。
         *
         * 约束：派生结果不包含脱离会话，因为脱离会话不出现在任何窗格中；会话宿主接入后由
         * 上层用 [upsert] 补充。
         */
        fun fromWorkspace(workspace: TabWorkspace): SessionRegistry {
            val records = ArrayList<SessionRecord>()
            for (tab in workspace.tabs) {
                for (leaf in tab.leaves) {
                    records += recordOf(tab.id, tab.title, leaf)
                }
            }
            return SessionRegistry(records)
        }

        private fun recordOf(tabId: String, tabTitle: String, leaf: PaneLeaf): SessionRecord {
            val sessionId = leaf.sessionId
            return if (sessionId == null) {
                SessionRecord(
                    id = PENDING_PREFIX + leaf.id,
                    leafId = leaf.id,
                    tabId = tabId,
                    tabTitle = tabTitle,
                    title = null,
                    workingDirectory = null,
                    state = SessionState.PENDING,
                )
            } else {
                SessionRecord(
                    id = sessionId,
                    leafId = leaf.id,
                    tabId = tabId,
                    tabTitle = tabTitle,
                    title = null,
                    workingDirectory = null,
                    state = SessionState.RUNNING,
                )
            }
        }
    }
}
