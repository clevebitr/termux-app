package com.termux.shell.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.termux.shell.R
import com.termux.shell.config.ApplicationConfigSource
import com.termux.shell.tabs.TabWorkspace

/**
 * 窗口状态持有者的工厂。
 *
 * 目的：把「从应用装配层取进程级单例」与「窗口内的状态持有者」分开，使 [ShellViewModel]
 * 不直接引用 `ShellApplication` 的静态入口，也便于在测试中替换构造参数。
 *
 * 行为：用应用级配置来源、应用资源生成的新标签标题、进程级会话宿主与布局存储构造视图模型。
 *
 * 约束：仅用于窗口创建；窗口重配置时复用同一实例，进程回收后由窗口重新创建。
 */
class ShellViewModelFactory(
    private val application: Application,
    private val initialWorkspace: TabWorkspace,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ShellViewModel(
            configSource = ApplicationConfigSource(application),
            initialWorkspace = initialWorkspace,
            titleFactory = { index -> application.getString(R.string.shell_tab_title, index) },
            sessionHost = com.termux.shell.ShellApplication.sessions(),
            sessionEnvironment = com.termux.shell.ShellApplication.sessionEnvironment(),
            layoutStore = com.termux.shell.ShellApplication.layouts(),
        ) as T
    }
}
