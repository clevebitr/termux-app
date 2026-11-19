package com.termux.shell.pane;

import androidx.annotation.NonNull;

/**
 * 窗格树节点。
 *
 * <p>目的：以二叉树描述外壳的分割结构。叶子节点承载会话，分支节点仅描述分割方向与比例，
 * 与视图和会话均无直接依赖，因此可被序列化、单元测试与跨窗口传输。
 *
 * <p>约束：节点不可在树上重复出现；每个节点只允许有一个父节点。
 */
public abstract class PaneNode {

    private final String mId;

    protected PaneNode(@NonNull String id) {
        mId = id;
    }

    /**
     * 返回节点标识。该标识在布局持久化中作为稳定键使用，分配后不得变更。
     */
    @NonNull
    public final String getId() {
        return mId;
    }
}
