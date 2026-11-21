package com.termux.shell.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.termux.shell.R
import com.termux.shell.ShellApplication
import com.termux.shell.config.SettingsSpec
import com.termux.shell.input.ShellInputRouter
import com.termux.shell.log.ShellLog
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.tabs.TabWorkspaceCodec

/**
 * 外壳窗口宿主。
 *
 * 目的：作为单个窗口的唯一入口，负责生命周期、边到边布局、快捷键分发与 Compose 内容装配；
 * 窗口内的全部状态由 [ShellController] 持有。
 *
 * 边界：
 * - 本类型不持有标签与窗格结构，结构在控制器中以不可变状态存在；
 * - 本类型不实现终端渲染与键序编码；
 * - 实时渲染的重对象（终端视图）将来由窗格叶子以 `AndroidView` 承载，不进入 Compose 合成。
 *
 * 关于多窗口：清单中声明 `documentLaunchMode="always"`，使每次「新建窗口」产生独立任务，
 * 从而支持桌面窗口模式与分屏模式下的并行窗口。
 */
class ShellActivity : ComponentActivity() {

    private lateinit var controller: ShellController
    private val inputRouter = ShellInputRouter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialWorkspace = restoreWorkspace(savedInstanceState)
            ?: TabWorkspace.initial(getString(R.string.shell_tab_title, 1))
        controller = ShellController(this, initialWorkspace).apply {
            onNewWindow = { openNewWindow() }
            // 设置页切换「外壳快捷键」后立即生效，无需重建窗口。
            onKeymapEnabledChanged = { enabled -> inputRouter.shellShortcutsEnabled = enabled }
            onMessage = { messageResId ->
                Toast.makeText(this@ShellActivity, messageResId, Toast.LENGTH_SHORT).show()
            }
        }

        configureInputRouter()

        setContent {
            val darkTheme = when (controller.themeMode) {
                SettingsSpec.THEME_DARK -> true
                SettingsSpec.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            ShellTheme(darkTheme = darkTheme) {
                ShellScreen(controller)
            }
        }

        ShellLog.info(
            SUB_TAG,
            "窗口已创建，${controller.workspace.describeState()}，主题=${controller.themeMode}",
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_WORKSPACE, TabWorkspaceCodec.encode(controller.workspace))
    }

    /**
     * 快捷键优先于视图默认处理。
     *
     * 说明：只在按下事件上查询键位表；未命中时交回系统分发，保证终端视图与文本选择等行为不受影响。
     * `ComponentActivity` 把该方法标注为库内可见，但覆写它是受支持的用法（运行时无限制），
     * 因此在此显式抑制检查；若后续改用无障碍过滤，需要同时复核这里的优先级。
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && inputRouter.onKeyDown(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun configureInputRouter() {
        val settings = ShellApplication.settings()
        inputRouter.keymap = ShellApplication.keymap()
        inputRouter.shellShortcutsEnabled = settings.getBoolean(SettingsSpec.KEY_KEYMAP_ENABLED, true)
        inputRouter.host = ShellInputRouter.Host { action -> controller.execute(action) }
    }

    private fun restoreWorkspace(state: Bundle?): TabWorkspace? {
        val encoded = state?.getString(STATE_WORKSPACE) ?: return null
        val decoded = TabWorkspaceCodec.decode(encoded)
        if (decoded == null) {
            ShellLog.warn(SUB_TAG, "标签工作区恢复失败，回退到单标签布局")
            return null
        }
        return TabWorkspace.restore(decoded.tabs, decoded.activeIndex)
    }

    /**
     * 新建独立窗口。
     *
     * 说明：`FLAG_ACTIVITY_NEW_DOCUMENT` 与 `FLAG_ACTIVITY_MULTIPLE_TASK` 组合使用，
     * 使新窗口在最近任务中独立出现，并可在桌面窗口模式下与前一个窗口并排显示。
     */
    private fun openNewWindow() {
        startActivity(
            Intent(this, ShellActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
        )
    }

    private companion object {
        const val SUB_TAG = "ShellActivity"
        const val STATE_WORKSPACE = "workspace"
    }
}
