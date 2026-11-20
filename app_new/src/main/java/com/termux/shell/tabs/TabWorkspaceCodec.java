package com.termux.shell.tabs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.pane.PaneLayoutCodec;
import com.termux.shell.pane.PaneNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 标签工作区的序列化编解码器。
 *
 * <p>目的：把「标签列表 + 每标签的窗格树 + 当前标签」写入状态或布局文件，使窗口在进程重建、
 * 旋转或应用重启后恢复到原结构。
 *
 * <p>格式：JSON 文档，版本号为 2。结构与示例：
 * <pre>
 * {"version":2,"activeTab":0,"tabs":[
 *   {"id":"tab-1","title":"Shell 1","root":{"type":"leaf","id":"pane-1","scrollRow":0}},
 *   {"id":"tab-2","title":"Shell 2","root":{"type":"split","id":"split-2","orientation":"vertical",
 *     "ratio":0.5,"first":{"type":"leaf","id":"pane-1"},"second":{"type":"leaf","id":"pane-3"}}}]}
 * </pre>
 *
 * <p>版本说明：窗口级标签模型取代了骨架期草案中的「窗格内标签列表」格式，故版本号直接取 2；
 * 版本 1 的文档不再解码，统一回退到默认单标签布局。
 *
 * <p>约束：解码失败时不抛出异常，统一返回 null；调用方负责回退。标签数量与窗格树深度均受上限约束。
 */
public final class TabWorkspaceCodec {

    /** 当前格式版本。 */
    public static final int CURRENT_VERSION = 2;

    private static final String KEY_VERSION = "version";
    private static final String KEY_ACTIVE_TAB = "activeTab";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ROOT = "root";

    private TabWorkspaceCodec() {
    }

    /**
     * 把工作区编码为文本。
     *
     * @throws IllegalStateException 编码过程中出现不可恢复的 JSON 构造错误。
     */
    @NonNull
    public static String encode(@NonNull TabWorkspace workspace) {
        try {
            JSONObject document = new JSONObject();
            document.put(KEY_VERSION, CURRENT_VERSION);
            document.put(KEY_ACTIVE_TAB, workspace.getActiveIndex());
            JSONArray tabs = new JSONArray();
            for (WorkspaceTab tab : workspace.getTabs()) {
                JSONObject encodedTab = new JSONObject();
                encodedTab.put(KEY_ID, tab.getId());
                encodedTab.put(KEY_TITLE, tab.getTitle());
                encodedTab.put(KEY_ROOT, PaneLayoutCodec.encodeNode(tab.getPanes().getRoot()));
                tabs.put(encodedTab);
            }
            document.put(KEY_TABS, tabs);
            return document.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("标签工作区编码失败", e);
        }
    }

    /**
     * 解码工作区。
     *
     * @param document 由 {@link #encode(TabWorkspace)} 生成的文本。
     * @return 解码结果；文本为空、版本不受支持、标签数量非法或任一窗格树非法时返回 null。
     */
    @Nullable
    public static DecodedWorkspace decode(@Nullable String document) {
        if (document == null || document.isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(document);
            if (object.optInt(KEY_VERSION, -1) != CURRENT_VERSION) {
                return null;
            }
            JSONArray encodedTabs = object.optJSONArray(KEY_TABS);
            if (encodedTabs == null || encodedTabs.length() == 0
                || encodedTabs.length() > TabWorkspace.MAX_TAB_COUNT) {
                return null;
            }
            List<WorkspaceTab> tabs = new ArrayList<>(encodedTabs.length());
            for (int i = 0; i < encodedTabs.length(); i++) {
                JSONObject encodedTab = encodedTabs.optJSONObject(i);
                if (encodedTab == null) {
                    return null;
                }
                String id = encodedTab.optString(KEY_ID, "");
                String title = encodedTab.optString(KEY_TITLE, "");
                if (id.isEmpty() || title.isEmpty()) {
                    return null;
                }
                PaneNode root = PaneLayoutCodec.decodeNode(encodedTab.optJSONObject(KEY_ROOT));
                if (root == null) {
                    return null;
                }
                WorkspaceTab tab = new WorkspaceTab(id, title);
                tab.getPanes().restore(root);
                tabs.add(tab);
            }
            return new DecodedWorkspace(tabs, object.optInt(KEY_ACTIVE_TAB, 0));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 返回给定文本中声明的格式版本号，供迁移逻辑判断。无法解析时返回 -1。
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
     * 解码结果。标签列表不可变；当前标签索引可能越界，由 {@link TabWorkspace#restore} 截断。
     */
    public static final class DecodedWorkspace {

        private final List<WorkspaceTab> mTabs;
        private final int mActiveIndex;

        private DecodedWorkspace(@NonNull List<WorkspaceTab> tabs, int activeIndex) {
            mTabs = Collections.unmodifiableList(tabs);
            mActiveIndex = activeIndex;
        }

        @NonNull
        public List<WorkspaceTab> getTabs() {
            return mTabs;
        }

        public int getActiveIndex() {
            return mActiveIndex;
        }
    }
}
