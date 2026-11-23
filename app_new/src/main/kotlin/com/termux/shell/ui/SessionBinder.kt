package com.termux.shell.ui

import com.termux.shell.pane.PaneLayout
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.session.ProfileCommandBuilder
import com.termux.shell.session.SessionEnvironment
import com.termux.shell.session.SessionId
import com.termux.shell.session.SessionProfile
import com.termux.shell.session.SessionProvider
import com.termux.shell.tabs.TabWorkspace

/**
 * 窗格叶子与会话进程的绑定。
 *
 * 目的：把「哪些叶子需要会话、按哪个配置启动、如何结束」从窗口状态持有者中分离，
 * 使标签与窗格的纯结构操作保持无副作用，会话生命周期只在这一处变化。
 *
 * 行为：
 * - [bind] 为尚未绑定或绑定已失效的叶子创建会话，已绑定且宿主仍能描述该会话的叶子保持不变；
 * - [close] 结束指定会话，宿主不存在的测试环境下为空操作；
 * - 创建失败时不修改叶子，界面按占位内容呈现。
 *
 * 约束：本类型只在主线程调用；会话创建由宿主决定是否真正启动进程；本类型不持有视图。
 */
class SessionBinder(
    private val host: SessionProvider?,
    private val profileLookup: (String?) -> SessionProfile?,
    private val environment: SessionEnvironment,
) {

    /** 为工作区中缺少有效会话的叶子补齐会话，返回新的不可变工作区。 */
    fun bind(workspace: TabWorkspace): TabWorkspace {
        val sessionHost = host ?: return workspace
        return workspace.copy(
            tabs = workspace.tabs.map { tab ->
                tab.copy(
                    root = PaneLayout.mapLeaves(tab.root) { leaf ->
                        ensureSession(sessionHost, leaf, tab.profileId)
                    },
                )
            },
        )
    }

    /** 结束会话；宿主不存在或会话已结束时为空操作。 */
    fun close(sessionId: String) {
        val sessionHost = host ?: return
        runCatching { sessionHost.closeSession(SessionId.of(sessionId)) }
    }

    private fun ensureSession(host: SessionProvider, leaf: PaneLeaf, profileId: String?): PaneLeaf {
        val existing = leaf.sessionId
        if (existing != null && host.describe(SessionId.of(existing)) != null) {
            return leaf
        }
        val request = ProfileCommandBuilder.build(profileLookup(profileId), environment)
        val sessionId = runCatching { host.createSession(request) }.getOrNull() ?: return leaf
        return leaf.copy(sessionId = sessionId.value)
    }
}
