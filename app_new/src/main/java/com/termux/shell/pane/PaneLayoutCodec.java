package com.termux.shell.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.session.SessionId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 窗格树序列化编解码器。
 *
 * <p>目的：把窗格树写入配置文件，使窗口在进程重建或应用重启后恢复原有的分割结构与滚动位置。
 *
 * <p>格式：JSON 对象，包含版本号与根节点。版本号用于格式演进时执行迁移；未提升版本号不得改变既有字段语义。
 * 结构示例：
 * <pre>
 * {"version":1,"root":{"type":"split","id":"split-2","orientation":"horizontal","ratio":0.5,
 *   "first":{"type":"leaf","id":"pane-1","tabs":["s1"],"activeTab":0,"scrollRow":120},
 *   "second":{"type":"leaf","id":"pane-3","tabs":[],"activeTab":-1,"scrollRow":0}}}
 * </pre>
 *
 * <p>约束：解码失败时不抛出异常，统一返回 null，由调用方回退到默认单窗格布局。
 */
public final class PaneLayoutCodec {

    public static final int CURRENT_VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_ROOT = "root";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ID = "id";
    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_RATIO = "ratio";
    private static final String KEY_FIRST = "first";
    private static final String KEY_SECOND = "second";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_ACTIVE_TAB = "activeTab";
    private static final String KEY_SCROLL_ROW = "scrollRow";

    private static final String TYPE_LEAF = "leaf";
    private static final String TYPE_SPLIT = "split";
    private static final String ORIENTATION_HORIZONTAL = "horizontal";
    private static final String ORIENTATION_VERTICAL = "vertical";

    private PaneLayoutCodec() {
    }

    /**
     * 把窗格树编码为文本。
     *
     * @throws IllegalStateException 编码过程中出现不可恢复的 JSON 构造错误。
     */
    @NonNull
    public static String encode(@NonNull PaneNode root) {
        try {
            JSONObject document = new JSONObject();
            document.put(KEY_VERSION, CURRENT_VERSION);
            document.put(KEY_ROOT, encodeNode(root));
            return document.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("窗格树编码失败", e);
        }
    }

    /**
     * 解码窗格树。
     *
     * @param document 由 {@link #encode(PaneNode)} 生成的文本。
     * @return 解码结果；文本为空、格式非法或版本不受支持时返回 null。
     */
    @Nullable
    public static PaneNode decode(@Nullable String document) {
        if (document == null || document.isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(document);
            if (object.optInt(KEY_VERSION, -1) != CURRENT_VERSION) {
                return null;
            }
            JSONObject root = object.optJSONObject(KEY_ROOT);
            if (root == null) {
                return null;
            }
            return decodeNode(root);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 返回给定文本中声明的格式版本号，供配置迁移逻辑判断。无法解析时返回 -1。
     */
    public static int readVersion(@Nullable String document) {
        if (document == null || document.isEmpty()) {
            return -1;
        }
        try {
            return new JSONObject(document).optInt(KEY_VERSION, -1);
        } catch (JSONException e) {
            return -1;
        }
    }

    /**
     * 返回给定树中全部叶子节点的标识，按深度优先顺序排列，用于日志与校验。
     */
    @NonNull
    public static List<String> collectLeafIds(@Nullable PaneNode root) {
        List<String> ids = new ArrayList<>();
        if (root != null) {
            collectLeafIds(root, ids);
        }
        return ids;
    }

    @NonNull
    private static JSONObject encodeNode(@NonNull PaneNode node) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(KEY_ID, node.getId());
        if (node instanceof PaneLeaf) {
            PaneLeaf leaf = (PaneLeaf) node;
            object.put(KEY_TYPE, TYPE_LEAF);
            JSONArray tabs = new JSONArray();
            for (SessionId tab : leaf.getTabs()) {
                tabs.put(tab.getValue());
            }
            object.put(KEY_TABS, tabs);
            object.put(KEY_ACTIVE_TAB, leaf.getActiveTabIndex());
            object.put(KEY_SCROLL_ROW, leaf.getScrollRow());
        } else {
            PaneSplit split = (PaneSplit) node;
            object.put(KEY_TYPE, TYPE_SPLIT);
            object.put(KEY_ORIENTATION, split.getOrientation() == PaneSplit.ORIENTATION_HORIZONTAL
                ? ORIENTATION_HORIZONTAL : ORIENTATION_VERTICAL);
            object.put(KEY_RATIO, split.getRatio());
            object.put(KEY_FIRST, encodeNode(split.getFirst()));
            object.put(KEY_SECOND, encodeNode(split.getSecond()));
        }
        return object;
    }

    @Nullable
    private static PaneNode decodeNode(@NonNull JSONObject object) {
        String id = object.optString(KEY_ID, "");
        if (id.isEmpty()) {
            return null;
        }
        String type = object.optString(KEY_TYPE, "");
        if (TYPE_LEAF.equals(type)) {
            PaneLeaf leaf = new PaneLeaf(id);
            JSONArray tabs = object.optJSONArray(KEY_TABS);
            if (tabs != null) {
                for (int i = 0; i < tabs.length(); i++) {
                    String value = tabs.optString(i, "");
                    if (!value.isEmpty()) {
                        leaf.addTab(SessionId.of(value));
                    }
                }
            }
            leaf.selectTab(object.optInt(KEY_ACTIVE_TAB, -1));
            leaf.setScrollRow(object.optInt(KEY_SCROLL_ROW, 0));
            return leaf;
        }
        if (TYPE_SPLIT.equals(type)) {
            JSONObject first = object.optJSONObject(KEY_FIRST);
            JSONObject second = object.optJSONObject(KEY_SECOND);
            if (first == null || second == null) {
                return null;
            }
            PaneNode firstNode = decodeNode(first);
            PaneNode secondNode = decodeNode(second);
            if (firstNode == null || secondNode == null) {
                return null;
            }
            int orientation = ORIENTATION_VERTICAL.equals(object.optString(KEY_ORIENTATION, ORIENTATION_HORIZONTAL))
                ? PaneSplit.ORIENTATION_VERTICAL : PaneSplit.ORIENTATION_HORIZONTAL;
            PaneSplit split = new PaneSplit(id, orientation, firstNode, secondNode);
            float ratio = (float) object.optDouble(KEY_RATIO, PaneSplit.DEFAULT_RATIO);
            split.setRatioInternal(Math.min(PaneLayoutController.MAX_RATIO,
                Math.max(PaneLayoutController.MIN_RATIO, ratio)));
            return split;
        }
        return null;
    }

    private static void collectLeafIds(@NonNull PaneNode node, @NonNull List<String> out) {
        if (node instanceof PaneLeaf) {
            out.add(node.getId());
            return;
        }
        PaneSplit split = (PaneSplit) node;
        collectLeafIds(split.getFirst(), out);
        collectLeafIds(split.getSecond(), out);
    }
}
