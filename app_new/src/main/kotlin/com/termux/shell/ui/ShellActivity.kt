package com.termux.shell.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.termux.shell.R
import com.termux.shell.ShellApplication
import com.termux.shell.config.SettingsSpec
import com.termux.shell.input.ShellInputRouter
import com.termux.shell.log.ShellLog
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.tabs.TabWorkspaceCodec
import com.termux.shell.ui.shell.ShellScreen

/**
 * 外壳窗口宿主。
 *
 * 目的：作为单个窗口的唯一入口，负责生命周期、边到边布局、快捷键分发与 Compose 内容装配；
 * 窗口内的全部状态由 [ShellViewModel] 持有，并在窗口重配置期间存活。
 *
 * 边界：
 * - 本类型不持有标签与窗格结构，结构在视图模型中以不可变状态存在；
 * - 本类型不实现终端渲染与键序编码；
 * - 实时渲染的重对象（终端视图）将来由窗格叶子以 `AndroidView` 承载，不进入 Compose 合成。
 *
 * 关于多窗口：清单中声明 `documentLaunchMode="always"`，使每次「新建窗口」产生独立任务，
 * 从而支持桌面窗口模式与分屏模式下的并行窗口。
 */
class ShellActivity : ComponentActivity() {

    private val inputRouter = ShellInputRouter()
    private lateinit var viewModel: ShellViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialWorkspace = restoreWorkspace(savedInstanceState)
            ?: ShellApplication.layouts().read()
            ?: TabWorkspace.initial(getString(R.string.shell_tab_title, 1))
        viewModel = ViewModelProvider(
            this,
            ShellViewModelFactory(application, initialWorkspace),
        )[ShellViewModel::class.java]

        inputRouter.host = ShellInputRouter.Host { action -> viewModel.execute(action) }
        inputRouter.keymap = viewModel.keymap
        inputRouter.shellShortcutsEnabled = viewModel.keymapEnabled

        setContent {
            val darkTheme = when (viewModel.themeMode) {
                SettingsSpec.THEME_DARK -> true
                SettingsSpec.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            // 键位表与快捷键开关以视图模型状态为准，重载配置后自动同步到输入路由。
            LaunchedEffect(viewModel.keymap, viewModel.keymapEnabled) {
                inputRouter.keymap = viewModel.keymap
                inputRouter.shellShortcutsEnabled = viewModel.keymapEnabled
            }
            // 新建窗口是一次性事件，视图模型只递增令牌，实际创建由窗口宿主完成。
            LaunchedEffect(viewModel.newWindowRequestToken) {
                if (viewModel.consumeNewWindowRequest()) {
                    openNewWindow()
                }
            }
            // 系统栏图标与系统栏显隐跟随应用内主题，而不是仅跟随系统深色设置。
            SideEffect {
                applyWindowAppearance(darkTheme, viewModel.fullscreen)
            }
            ShellTheme(darkTheme = darkTheme) {
                ShellScreen(viewModel)
            }
        }

        ShellLog.info(
            SUB_TAG,
            "窗口已创建，${viewModel.workspace.describeState()}，主题=${viewModel.themeMode}",
        )
    }

    /**
     * 退到后台时保存布局。
     *
     * 说明：进程被系统回收时不会回调 `onSaveInstanceState` 之后的任何写盘点，因此把保存放在
     * `onStop`；写入由状态持有者在 IO 协程完成，失败不影响窗口继续运行。
     */
    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            viewModel.persistLayout()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 从系统栏滑出、切换应用或分屏获得的焦点都会重置系统栏显隐，需要重新应用。
        if (hasFocus && ::viewModel.isInitialized) {
            applyWindowAppearance(currentDarkTheme(), viewModel.fullscreen)
        }
    }

    /**
     * 同步系统栏外观与显隐。
     *
     * 行为：图标亮暗跟随 Compose 主题；全屏模式隐藏状态栏与导航栏，并允许从屏幕边缘滑动临时调出。
     *
     * 约束：只在窗口获得焦点时调用，避免在失焦窗口上反复请求显隐造成闪烁。
     */
    private fun applyWindowAppearance(darkTheme: Boolean, fullscreen: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
        if (fullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** 按配置项与系统设置计算当前是否为深色主题，供非组合上下文使用。 */
    private fun currentDarkTheme(): Boolean = when (viewModel.themeMode) {
        SettingsSpec.THEME_DARK -> true
        SettingsSpec.THEME_LIGHT -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_WORKSPACE, TabWorkspaceCodec.encode(viewModel.workspace))
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
