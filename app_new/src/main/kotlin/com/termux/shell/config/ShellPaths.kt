package com.termux.shell.config

import android.content.Context
import java.io.File

/**
 * 外壳的路径解析。
 *
 * 目的：把「配置写在哪里」集中到单一类型，避免各包自行拼接路径。用户配置写入应用私有目录下的
 * 独立主目录，布局等内部状态写入应用私有目录的另一子目录，二者的生命周期与卸载清理一致。
 *
 * 布局：`filesDir/home/.termux/shell.properties` 为用户可编辑的配置文件，
 * `filesDir/home/.termux/keymap.properties` 为键位表；布局状态在后续里程碑写入 `filesDir/layout/`。
 *
 * 约束：本类型只做路径计算与目录创建，不读写文件内容；路径解析不依赖 Android 运行时，
 * 可在单元测试中以临时目录构造。
 */
class ShellPaths(val appDir: File) {

    /** 应用私有主目录。包名切换为 `com.termux` 后该目录即真实用户主目录。 */
    val homeDir: File = File(appDir, HOME_DIR_NAME)

    val termuxDir: File = File(homeDir, TERMUX_DIR_NAME)

    val settingsFile: File = File(termuxDir, SETTINGS_FILE_NAME)

    val keymapFile: File = File(termuxDir, KEYMAP_FILE_NAME)

    val profilesFile: File = File(termuxDir, PROFILES_FILE_NAME)

    /** 窗口布局目录。布局属于应用内部状态，与用户可编辑的配置文件分开存放。 */
    val layoutDir: File = File(appDir, LAYOUT_DIR_NAME)

    /** 上次退出时的工作区结构。多个窗口共享同一文件，采用最后写入者生效。 */
    val layoutFile: File = File(layoutDir, LAYOUT_FILE_NAME)

    /**
     * 创建配置目录。
     *
     * @return 目录是否可用。创建失败时返回 false，调用方应记录日志并回退到内置默认值。
     */
    fun ensureTermuxDir(): Boolean = termuxDir.isDirectory || termuxDir.mkdirs()

    companion object {

        /** 应用私有主目录相对于 `filesDir` 的名称。 */
        const val HOME_DIR_NAME = "home"

        /** 用户配置目录名，与旧应用的 `~/.termux` 习惯保持一致。 */
        const val TERMUX_DIR_NAME = ".termux"

        const val SETTINGS_FILE_NAME = "shell.properties"
        const val KEYMAP_FILE_NAME = "keymap.properties"

        const val PROFILES_FILE_NAME = "shell-profiles.json"

        const val LAYOUT_DIR_NAME = "layout"
        const val LAYOUT_FILE_NAME = "window-layout.json"

        /** 按应用上下文创建路径解析器。 */
        fun forApplication(context: Context): ShellPaths = ShellPaths(context.filesDir)
    }
}
