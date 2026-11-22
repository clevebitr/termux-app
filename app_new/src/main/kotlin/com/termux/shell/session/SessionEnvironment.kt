package com.termux.shell.session

/**
 * 会话启动环境。
 *
 * 目的：把「会话进程需要哪些默认环境」集中到一处，避免在宿主、配置与界面之间重复拼接路径。
 * 骨架阶段没有 bootstrap，只能使用系统 shell，因此 PATH 指向系统目录，HOME 指向应用主目录。
 *
 * 约束：本类型不可变；路径由应用装配层提供，不得在单元测试中依赖真实文件系统。
 */
data class SessionEnvironment(
    val homeDirectory: String,
    val tempDirectory: String,
    val shellPath: String = DEFAULT_SHELL,
    val path: String = "/system/bin:/system/xbin",
    val language: String = "en_US.UTF-8",
) {

    /** 会话进程的默认环境变量。 */
    fun baseEnvironment(): Map<String, String> = linkedMapOf(
        "HOME" to homeDirectory,
        "TMPDIR" to tempDirectory,
        "PATH" to path,
        "LANG" to language,
        "TERM" to "xterm-256color",
        "SHELL" to shellPath,
    )

    companion object {
        const val DEFAULT_SHELL = "/system/bin/sh"
    }
}
