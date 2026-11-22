package com.termux.app.commands;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.menu.TerminalMenuAction;
import com.termux.shared.termux.interact.TextInputDialogUtils;

/**
 * 目的 — 作为窗口级动作的唯一执行入口。
 *
 * <p>行为 — 命令面板、会话抽屉按钮与会话新建按钮都调用这里的方法，因此同一个动作只有一份实现：
 * 终端菜单动作交给 {@link TermuxActivity#runTerminalMenuAction(TerminalMenuAction)}，新建会话、新建命名
 * 会话与切换软键盘也集中在此。切换软键盘走 {@code onToggleSoftKeyboardRequest()}，即抽屉按钮原先使用的
 * 实现，它同时处理「启用/禁用软键盘」与「显示/隐藏软键盘」两种配置。</p>
 *
 * <p>约束 — 只能在 {@link TermuxActivity} 创建完成且会话客户端已建立后使用；方法必须在主线程调用，且
 * 调用方关闭命令面板或会话抽屉之后才执行，以免对话框与抽屉竞争焦点。</p>
 */
public final class ShellCommandHost implements ShellCommandCatalog.Host {

    @NonNull
    private final TermuxActivity activity;

    public ShellCommandHost(@NonNull TermuxActivity activity) {
        this.activity = activity;
    }

    @Override
    public void runTerminalMenuAction(@NonNull TerminalMenuAction action) {
        activity.runTerminalMenuAction(action);
    }

    @Override
    public void addNewSession() {
        activity.getTermuxTerminalSessionClient().addNewSession(false, null);
    }

    /**
     * Ask for a name and create the session, or create it without a name through the failsafe button, which
     * exists because a name that the shell rejects cannot be stopped from the dialog itself.
     */
    @Override
    public void addNamedSession() {
        TextInputDialogUtils.textInput(activity, R.string.title_create_named_session, null,
            R.string.action_create_named_session_confirm,
            text -> activity.getTermuxTerminalSessionClient().addNewSession(false, text),
            R.string.action_new_session_failsafe,
            text -> activity.getTermuxTerminalSessionClient().addNewSession(true, text),
            -1, null, null);
    }

    @Override
    public void toggleSoftKeyboard() {
        activity.getTermuxTerminalViewClient().onToggleSoftKeyboardRequest();
    }
}
