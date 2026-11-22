package com.termux.shell.session

/**
 * 会话配置类型。
 *
 * 目的：区分「直接启动本地 shell」「登录某个发行版」「连接远程主机」三种会话来源，
 * 使标签在创建时能携带足够信息，而启动逻辑留给会话宿主。
 */
enum class ProfileKind {
    SHELL,
    DISTRO,
    SSH,
}

/**
 * 会话配置。
 *
 * 目的：把「如何启动一个会话」保存为可复用的数据：命令、工作目录、环境变量，
 * 以及发行版名称或 SSH 主机。标签只记录配置标识，由会话宿主在创建会话时解析。
 *
 * 行为：与标签模型一致保持不可变；名称与标识不得为空，发行版类型必须给出发行版名称，
 * SSH 类型必须给出主机地址，校验失败抛出 [IllegalArgumentException]。
 *
 * 约束：本类型不依赖 Android 运行时，可在 JVM 单元测试中直接构造；不得保存密码等敏感信息，
 * 凭据应由 SSH 密钥或用户自行输入。
 */
data class SessionProfile(
    val id: String,
    val name: String,
    val kind: ProfileKind = ProfileKind.SHELL,
    val command: String = "",
    val workingDirectory: String = "",
    val environment: Map<String, String> = emptyMap(),
    val distro: String = "",
    val sshHost: String = "",
) {

    init {
        require(id.isNotBlank()) { "配置标识不得为空" }
        require(name.isNotBlank()) { "配置名称不得为空" }
        if (kind == ProfileKind.DISTRO) {
            require(distro.isNotBlank()) { "发行版类型必须填写发行版名称" }
        }
        if (kind == ProfileKind.SSH) {
            require(sshHost.isNotBlank()) { "SSH 类型必须填写主机地址" }
        }
    }
}
