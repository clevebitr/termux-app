package com.termux.shell

import android.annotation.SuppressLint
import android.app.Application
import com.termux.shell.config.FileLayoutStore
import com.termux.shell.config.FileProfileStore
import com.termux.shell.config.LayoutStore
import com.termux.shell.config.ProfileStore
import com.termux.shell.config.PropertiesSettingsRepository
import com.termux.shell.config.SettingsRepository
import com.termux.shell.config.ShellPaths
import com.termux.shell.input.KeymapLoader
import com.termux.shell.input.KeymapTable
import com.termux.shell.session.LocalSessionHost
import com.termux.shell.session.SessionEnvironment
import com.termux.shell.log.ShellLog

/**
 * 新外壳的进程入口。
 *
 * 目的：集中装配进程级单例（路径、配置、键位表），避免各组件通过静态字段互相引用。
 *
 * 行为：启动时解析配置目录、读取属性文件与键位表。配置文件或键位表缺失时写入默认模板；
 * 读取失败时按内置默认值运行。
 *
 * 约束：
 * - 此处只创建轻量对象；会话、终端视图等重对象由窗口按需创建；
 * - 启动阶段读取的两个文件均为数 KB 量级，允许在主线程完成，避免界面在首帧后跳变；
 *   若后续新增更大的配置文件，应改为后台加载并在加载完成后通知界面。
 */
class ShellApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        paths = ShellPaths.forApplication(this)
        if (!paths.ensureTermuxDir()) {
            ShellLog.warn(SUB_TAG, "配置目录创建失败：${paths.termuxDir}")
        }
        settings = PropertiesSettingsRepository.open(paths.settingsFile)
        logInvalidSettings(settings)
        keymap = KeymapLoader.load(paths.keymapFile)
        profileStore = FileProfileStore(paths.profilesFile)
        layoutStore = FileLayoutStore(paths.layoutFile)
        sessionEnvironment = SessionEnvironment(
            homeDirectory = paths.homeDir.absolutePath,
            tempDirectory = cacheDir.absolutePath,
        )
        sessionHost = LocalSessionHost(this, sessionEnvironment)
        ShellLog.info(
            SUB_TAG,
            "配置就绪：配置项 ${settings.keys().size} 项，键位 ${keymap.size} 条，会话配置 ${profileStore.profiles.value.size} 条",
        )
    }

    companion object {

        private const val SUB_TAG = "ShellApplication"

        @Volatile
        private lateinit var paths: ShellPaths

        @Volatile
        private lateinit var settings: PropertiesSettingsRepository

        @Volatile
        private lateinit var keymap: KeymapTable

        @Volatile
        private lateinit var profileStore: ProfileStore

        // 会话宿主持有 Application 上下文，生命周期与进程一致；该引用不是泄漏，
        // 而是进程级单例的必要组成，因此抑制 Lint 的静态上下文检查。
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private lateinit var sessionHost: LocalSessionHost

        @Volatile
        private lateinit var layoutStore: LayoutStore

        @Volatile
        private lateinit var sessionEnvironment: SessionEnvironment

        /** 返回路径解析器。启动完成后可用。 */
        fun paths(): ShellPaths = paths

        /** 返回进程级配置来源。 */
        fun settings(): SettingsRepository = settings

        /** 返回当前生效的键位表。 */
        fun keymap(): KeymapTable = keymap

        /** 返回进程级会话配置存储。 */
        fun profiles(): ProfileStore = profileStore

        /** 返回进程级窗口布局存储。窗口启动时读取，退到后台时写入。 */
        fun layouts(): LayoutStore = layoutStore

        /** 返回进程级会话宿主。会话随应用进程存活。 */
        fun sessions(): LocalSessionHost = sessionHost

        /** 返回会话启动环境（主目录、临时目录等）。 */
        fun sessionEnvironment(): SessionEnvironment = sessionEnvironment

        /**
         * 重新读取配置文件与键位表。
         *
         * 说明：命令面板的「重新加载配置文件」调用本方法。读取失败时保留旧快照并返回 false。
         *
         * @return 是否成功读取配置文件。
         */
        fun reloadConfig(): Boolean {
            if (!settings.reload()) {
                return false
            }
            logInvalidSettings(settings)
            keymap = KeymapLoader.load(paths.keymapFile)
            // 复用同一实例，避免订阅了旧 StateFlow 的界面失去更新。
            if (!profileStore.reload()) {
                ShellLog.warn(SUB_TAG, "会话配置重载失败，保留旧列表")
            }
            ShellLog.info(
                SUB_TAG,
                "配置已重新加载，键位 ${keymap.size} 条，会话配置 ${profileStore.profiles.value.size} 条",
            )
            return true
        }

        private fun logInvalidSettings(repository: PropertiesSettingsRepository) {
            val invalidKeys = repository.getInvalidKeys()
            if (invalidKeys.isNotEmpty()) {
                ShellLog.warn(SUB_TAG, "以下配置项的取值非法，已回退到默认值：$invalidKeys")
            }
        }
    }
}
