package com.termux.shell.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.session.SessionId;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 单个窗格树的序列化编解码器。
 *
 * <p>目的：把窗格树转换为可存储的 JSON 片段，使标签级布局编码器（{@code TabWorkspaceCodec}）
 * 无需了解窗格树的内部结构。
 *
 * <p>格式：节点对象。叶子形如 {@code {"type":"leaf","id":"pane-1","session":"…","scrollRow":0}}，
 * 分割节点形如 {@code {"type":"split","id":"split-2","orientation":"horizontal","ratio":0.5,
 * "first":{…},"second":{…}}}。{@code session} 字段仅在已分配会话时出现，缺失表示占位窗格。
 *
 * <p>约束：解码失败时不抛出异常，统一返回 null，由调用方回退到默认单窗格布局。
 */
public final class PaneLayoutCodec {

    private static final String KEY_TYPE = "type";
    private static final String KEY_ID = "id";
    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_RATIO = "ratio";
    private static final String KEY_FIRST = "first";
    private static final String KEY_SECOND = "second";
    private static final String KEY_SESSION = "session";
    private static final String KEY_SCROLL_ROW = "scrollRow";

    private static final String TYPE_LEAF = "leaf";
    private static final String TYPE_SPLIT = "split";
    private static final String ORIENTATION_HORIZONTAL = "horizontal";
    private static final String ORIENTATION_VERTICAL = "vertical";

    private PaneLayoutCodec() {
    }

    /**
     * 把窗格树编码为 JSON 对象。
     *
     * @throws JSONException 编码过程中出现不可恢复的 JSON 构造错误。
     */
    @NonNull
    public static JSONObject encodeNode(@NonNull PaneNode node) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(KEY_ID, node.getId());
        if (node instanceof PaneLeaf) {
            PaneLeaf leaf = (PaneLeaf) node;
            object.put(KEY_TYPE, TYPE_LEAF);
            if (leaf.getSessionId() != null) {
                object.put(KEY_SESSION, leaf.getSessionId().getValue());
            }
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

    /**
     * 解码窗格树节点。
     *
     * @return 解码结果；字段缺失或类型非法的节点返回 null。
     */
    @Nullable
    public static PaneNode decodeNode(@Nullable JSONObject object) {
        if (object == null) {
            return null;
        }
        String id = object.optString(KEY_ID, "");
        if (id.isEmpty()) {
            return null;
        }
        String type = object.optString(KEY_TYPE, "");
        if (TYPE_LEAF.equals(type)) {
            PaneLeaf leaf = new PaneLeaf(id);
            String session = object.optString(KEY_SESSION, "");
            if (!session.isEmpty()) {
                leaf.setSessionId(SessionId.of(session));
            }
            leaf.setScrollRow(object.optInt(KEY_SCROLL_ROW, 0));
            return leaf;
        }
        if (TYPE_SPLIT.equals(type)) {
            PaneNode first = decodeNode(object.optJSONObject(KEY_FIRST));
            PaneNode second = decodeNode(object.optJSONObject(KEY_SECOND));
            if (first == null || second == null || first == second) {
                return null;
            }
            int orientation = ORIENTATION_VERTICAL.equals(object.optString(KEY_ORIENTATION, ORIENTATION_HORIZONTAL))
                ? PaneSplit.ORIENTATION_VERTICAL : PaneSplit.ORIENTATION_HORIZONTAL;
            PaneSplit split = new PaneSplit(id, orientation, first, second);
            float ratio = (float) object.optDouble(KEY_RATIO, PaneSplit.DEFAULT_RATIO);
            split.setRatioInternal(Math.min(PaneLayoutController.MAX_RATIO,
                Math.max(PaneLayoutController.MIN_RATIO, ratio)));
            return split;
        }
        return null;
    }
}
