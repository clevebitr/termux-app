package com.termux.shell.config

import android.app.Application
import com.termux.shell.ShellApplication
import com.termux.shell.input.KeymapTable

/**
 * 基于进程单例的配置来源实现。
 *
 * 目的：把 [ShellApplication] 的静态配置入口适配为 [ConfigSource]，使窗口状态持有者不直接依赖静态字段。
 *
 * 约束：仅用于应用装配层；单元测试必须使用自定义的 [ConfigSource] 假实现。
 */
class ApplicationConfigSource(
    @Suppress("unused") private val application: Application,
) : ConfigSource {

    override fun settings(): SettingsRepository = ShellApplication.settings()

    override fun keymap(): KeymapTable = ShellApplication.keymap()

    override fun profiles(): ProfileStore = ShellApplication.profiles()

    override fun reload(): Boolean = ShellApplication.reloadConfig()

    override val configFilePath: String
        get() = ShellApplication.paths().settingsFile.absolutePath

    override val keymapFilePath: String
        get() = ShellApplication.paths().keymapFile.absolutePath
}
