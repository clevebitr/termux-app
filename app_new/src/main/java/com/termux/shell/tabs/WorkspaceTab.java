package com.termux.shell.tabs;

import androidx.annotation.NonNull;

import com.termux.shell.pane.PaneLayoutController;

/**
 * 工作区标签。
 *
 * <p>目的：表达「一个标签对应一棵独立窗格树」的窗口模型，与 Windows Terminal 的标签语义一致。
 * 切换标签等价于整体替换当前窗格树，标签内部仍可继续分割为多个窗格。
 *
 * <p>行为：标题用于标签栏展示，初始标题由调用方按序号生成；终端接入后改由会话标题覆盖。
 *
 * <p>约束：标识在标签存活期间不得变更，并作为布局编码中的稳定键；窗格控制器随标签一同创建，
 * 不得转移到其他标签。
 */
public final class WorkspaceTab {

    private final String mId;
    private final PaneLayoutController mPanes;
    private String mTitle;

    /**
     * @param id    标签标识，不得为空。
     * @param title 初始标题，不得为空。
     */
    public WorkspaceTab(@NonNull String id, @NonNull String title) {
        if (id.isEmpty()) {
            throw new IllegalArgumentException("标签标识不得为空");
        }
        if (title.isEmpty()) {
            throw new IllegalArgumentException("标签标题不得为空");
        }
        mId = id;
        mTitle = title;
        mPanes = new PaneLayoutController();
    }

    /**
     * 返回标签标识。布局编码以该标识区分标签，分配后不得变更。
     */
    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull String title) {
        if (title.isEmpty()) {
            throw new IllegalArgumentException("标签标题不得为空");
        }
        mTitle = title;
    }

    /**
     * 返回本标签的窗格树控制器。返回值为可变的控制器实例，调用方负责在线程与生命周期上的正确性。
     */
    @NonNull
    public PaneLayoutController getPanes() {
        return mPanes;
    }
}
