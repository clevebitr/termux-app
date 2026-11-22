package com.termux.shell.session

/**
 * 按会话配置生成启动参数。
 *
 * 目的：把「配置 → argv / 工作目录 / 环境变量」的映射抽为纯函数，使会话宿主只负责进程与 PTY，
 * 且映射规则可以在 JVM 单元测试中直接验证。
 *
 * 行为：
 * - 全部类型都通过系统 shell 启动，`SHELL` 直接执行配置中的命令，`DISTRO` 调用
 *   `proot-distro login`，`SSH` 调用 `ssh`；这些工具是否安装由运行环境决定，缺失时终端会直接
 *   显示错误，不做隐式回退；
 * - 工作目录为空时使用 [SessionEnvironment.homeDirectory]；
 * - 环境变量以默认环境为底，配置中的同名键覆盖；
 * - 注入 shell 的参数一律做单引号转义，避免发行版名称或主机名被二次解释。
 *
 * 约束：不读取磁盘、不访问 Android API；不保存密码或私钥。
 */
object ProfileCommandBuilder {

    fun build(profile: SessionProfile?, environment: SessionEnvironment): SessionHost.SessionRequest {
        val baseEnvironment = environment.baseEnvironment()
        if (profile == null) {
            return SessionHost.SessionRequest(
                workingDirectory = environment.homeDirectory,
                argv = listOf(environment.shellPath),
                environment = baseEnvironment,
            )
        }
        val command = when (profile.kind) {
            ProfileKind.SHELL -> profile.command.trim()
            ProfileKind.DISTRO -> buildDistroCommand(profile)
            ProfileKind.SSH -> buildSshCommand(profile)
        }
        val argv = if (command.isEmpty()) {
            listOf(environment.shellPath)
        } else {
            listOf(environment.shellPath, "-c", command)
        }
        val mergedEnvironment = LinkedHashMap(baseEnvironment).apply {
            putAll(profile.environment)
        }
        return SessionHost.SessionRequest(
            workingDirectory = profile.workingDirectory.ifBlank { environment.homeDirectory },
            argv = argv,
            environment = mergedEnvironment,
        )
    }

    private fun buildDistroCommand(profile: SessionProfile): String {
        val login = "proot-distro login ${quote(profile.distro)}"
        val command = profile.command.trim()
        return if (command.isEmpty()) "exec $login" else "exec $login -- $command"
    }

    private fun buildSshCommand(profile: SessionProfile): String {
        val ssh = "ssh ${quote(profile.sshHost)}"
        val command = profile.command.trim()
        return if (command.isEmpty()) "exec $ssh" else "exec $ssh $command"
    }

    /** 单引号转义：把内部单引号替换为 `'\''`。 */
    internal fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
