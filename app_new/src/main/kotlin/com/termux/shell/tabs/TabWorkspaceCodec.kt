package com.termux.shell.tabs

import com.termux.shell.pane.PaneLayout
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.PaneNode
import com.termux.shell.pane.PaneSplit
import com.termux.shell.pane.SplitOrientation
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 标签工作区的序列化编解码器。
 *
 * 目的：把「标签列表 + 每标签的窗格树 + 当前标签」写入状态或布局文件，使窗口在进程重建、
 * 旋转或应用重启后恢复到原结构。
 *
 * 格式：JSON 文档，版本号为 2，示例：
 * ```json
 * {"version":2,"activeTab":0,"tabs":[
 *   {"id":"tab-1","title":"Shell 1","focusedLeaf":"pane-1","root":{"type":"leaf","id":"pane-1"}},
 *   {"id":"tab-2","title":"Shell 2","root":{"type":"split","id":"split-2","orientation":"vertical",
 *     "ratio":0.5,"first":{"type":"leaf","id":"pane-1"},"second":{"type":"leaf","id":"pane-3"}}}]}
 * ```
 *
 * 约束：
 * - `focusedLeaf` 为可选字段，缺失时回退到第一个叶子，因此旧文档仍可解码；
 * - 解码失败不抛出异常，统一返回 null，由调用方回退到默认单标签布局；
 * - 标签数量与窗格树结构均受上限约束。
 */
object TabWorkspaceCodec {

    /** 当前格式版本。 */
    const val CURRENT_VERSION = 2

    private const val KEY_VERSION = "version"
    private const val KEY_ACTIVE_TAB = "activeTab"
    private const val KEY_TABS = "tabs"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_FOCUSED_LEAF = "focusedLeaf"
    private const val KEY_PROFILE = "profile"
    private const val KEY_ROOT = "root"
    private const val KEY_TYPE = "type"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_RATIO = "ratio"
    private const val KEY_FIRST = "first"
    private const val KEY_SECOND = "second"
    private const val KEY_SESSION = "session"
    private const val KEY_SCROLL_ROW = "scrollRow"

    private const val TYPE_LEAF = "leaf"
    private const val TYPE_SPLIT = "split"
    private const val ORIENTATION_HORIZONTAL = "horizontal"
    private const val ORIENTATION_VERTICAL = "vertical"

    /** 解码结果。当前标签索引可能越界，由 [TabWorkspace.restore] 截断。 */
    data class DecodedWorkspace(val tabs: List<WorkspaceTab>, val activeIndex: Int)

    /**
     * 把工作区编码为文本。
     *
     * @throws IllegalStateException 编码过程中出现不可恢复的 JSON 构造错误。
     */
    fun encode(workspace: TabWorkspace): String {
        try {
            val document = JSONObject()
            document.put(KEY_VERSION, CURRENT_VERSION)
            document.put(KEY_ACTIVE_TAB, workspace.activeIndex)
            val tabs = JSONArray()
            for (tab in workspace.tabs) {
                val encodedTab = JSONObject()
                encodedTab.put(KEY_ID, tab.id)
                encodedTab.put(KEY_TITLE, tab.title)
                encodedTab.put(KEY_FOCUSED_LEAF, tab.focusedLeaf.id)
                tab.profileId?.let { encodedTab.put(KEY_PROFILE, it) }
                encodedTab.put(KEY_ROOT, encodeNode(tab.root))
                tabs.put(encodedTab)
            }
            document.put(KEY_TABS, tabs)
            return document.toString()
        } catch (e: JSONException) {
            throw IllegalStateException("标签工作区编码失败", e)
        }
    }

    /**
     * 解码工作区。
     *
     * @param document 由 [encode] 生成的文本。
     * @return 解码结果；文本为空、版本不受支持、标签数量非法或任一窗格树非法时返回 null。
     */
    fun decode(document: String?): DecodedWorkspace? {
        if (document.isNullOrEmpty()) {
            return null
        }
        return try {
            val json = JSONObject(document)
            if (json.optInt(KEY_VERSION, -1) != CURRENT_VERSION) {
                return null
            }
            val encodedTabs = json.optJSONArray(KEY_TABS) ?: return null
            if (encodedTabs.length() == 0 || encodedTabs.length() > TabWorkspace.MAX_TAB_COUNT) {
                return null
            }
            val tabs = ArrayList<WorkspaceTab>(encodedTabs.length())
            for (index in 0 until encodedTabs.length()) {
                val encodedTab = encodedTabs.optJSONObject(index) ?: return null
                val id = encodedTab.optString(KEY_ID, "")
                val title = encodedTab.optString(KEY_TITLE, "")
                if (id.isEmpty() || title.isEmpty()) {
                    return null
                }
                val root = decodeNode(encodedTab.optJSONObject(KEY_ROOT)) ?: return null
                val focused = encodedTab.optString(KEY_FOCUSED_LEAF, "")
                    .takeIf { it.isNotEmpty() && PaneLayout.findLeaf(root, it) != null }
                    ?: PaneLayout.firstLeaf(root).id
                val profileId = encodedTab.optString(KEY_PROFILE, "").takeIf { it.isNotEmpty() }
                tabs += WorkspaceTab(id, title, root, focused, profileId)
            }
            DecodedWorkspace(tabs, json.optInt(KEY_ACTIVE_TAB, 0))
        } catch (e: JSONException) {
            null
        }
    }

    /** 返回给定文本中声明的格式版本号，供迁移逻辑判断。无法解析时返回 -1。 */
    fun readVersion(document: String?): Int {
        if (document.isNullOrEmpty()) {
            return -1
        }
        return try {
            JSONObject(document).optInt(KEY_VERSION, -1)
        } catch (e: JSONException) {
            -1
        }
    }

    private fun encodeNode(node: PaneNode): JSONObject {
        val encoded = JSONObject()
        encoded.put(KEY_ID, node.id)
        when (node) {
            is PaneLeaf -> {
                encoded.put(KEY_TYPE, TYPE_LEAF)
                node.sessionId?.let { encoded.put(KEY_SESSION, it) }
                encoded.put(KEY_SCROLL_ROW, node.scrollRow)
            }

            is PaneSplit -> {
                encoded.put(KEY_TYPE, TYPE_SPLIT)
                encoded.put(
                    KEY_ORIENTATION,
                    if (node.orientation == SplitOrientation.HORIZONTAL) ORIENTATION_HORIZONTAL
                    else ORIENTATION_VERTICAL,
                )
                encoded.put(KEY_RATIO, node.ratio.toDouble())
                encoded.put(KEY_FIRST, encodeNode(node.first))
                encoded.put(KEY_SECOND, encodeNode(node.second))
            }
        }
        return encoded
    }

    private fun decodeNode(json: JSONObject?): PaneNode? {
        if (json == null) {
            return null
        }
        val id = json.optString(KEY_ID, "")
        if (id.isEmpty()) {
            return null
        }
        return when (json.optString(KEY_TYPE, "")) {
            TYPE_LEAF -> PaneLeaf(
                id = id,
                sessionId = json.optString(KEY_SESSION, "").takeIf { it.isNotEmpty() },
                scrollRow = json.optInt(KEY_SCROLL_ROW, 0).coerceAtLeast(0),
            )

            TYPE_SPLIT -> {
                val first = decodeNode(json.optJSONObject(KEY_FIRST)) ?: return null
                val second = decodeNode(json.optJSONObject(KEY_SECOND)) ?: return null
                if (first.id == second.id) {
                    return null
                }
                val orientation = if (json.optString(KEY_ORIENTATION, ORIENTATION_HORIZONTAL) == ORIENTATION_VERTICAL) {
                    SplitOrientation.VERTICAL
                } else {
                    SplitOrientation.HORIZONTAL
                }
                val ratio = PaneLayout.clampRatio(json.optDouble(KEY_RATIO, PaneSplit.DEFAULT_RATIO.toDouble()).toFloat())
                PaneSplit(id, orientation, first, second, ratio)
            }

            else -> null
        }
    }
}
