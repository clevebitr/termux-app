package com.termux.shell.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ProfileCommandBuilder] 的类型映射、转义与环境合并测试。 */
class ProfileCommandBuilderTest {

    private val environment = SessionEnvironment(
        homeDirectory = "/home/user",
        tempDirectory = "/home/user/tmp",
    )

    @Test
    fun defaultRequestRunsSystemShellInHome() {
        val request = ProfileCommandBuilder.build(null, environment)
        assertEquals(listOf("/system/bin/sh"), request.argv)
        assertEquals("/home/user", request.workingDirectory)
        assertEquals("/system/bin:/system/xbin", request.environment["PATH"])
        assertEquals("xterm-256color", request.environment["TERM"])
    }

    @Test
    fun shellCommandIsPassedToDashC() {
        val profile = SessionProfile(id = "p1", name = "dev", command = "bash -l")
        val request = ProfileCommandBuilder.build(profile, environment)
        assertEquals(listOf("/system/bin/sh", "-c", "bash -l"), request.argv)
    }

    @Test
    fun distroUsesProotDistroLoginWithQuoting() {
        val profile = SessionProfile(
            id = "p1",
            name = "ubuntu",
            kind = ProfileKind.DISTRO,
            distro = "ubuntu'24",
        )
        val request = ProfileCommandBuilder.build(profile, environment)
        assertEquals("/system/bin/sh", request.argv[0])
        assertEquals("-c", request.argv[1])
        assertTrue(request.argv[2].startsWith("exec proot-distro login 'ubuntu'\\''24'"))
    }

    @Test
    fun sshUsesHostAndOptionalCommand() {
        val profile = SessionProfile(
            id = "p1",
            name = "server",
            kind = ProfileKind.SSH,
            sshHost = "user@example.com",
            command = "tmux attach",
        )
        val request = ProfileCommandBuilder.build(profile, environment)
        assertEquals("exec ssh 'user@example.com' tmux attach", request.argv[2])
    }

    @Test
    fun profileEnvironmentOverridesDefaults() {
        val profile = SessionProfile(
            id = "p1",
            name = "dev",
            environment = mapOf("PATH" to "/custom", "EDITOR" to "nvim"),
        )
        val request = ProfileCommandBuilder.build(profile, environment)
        assertEquals("/custom", request.environment["PATH"])
        assertEquals("nvim", request.environment["EDITOR"])
        assertEquals("/home/user", request.environment["HOME"])
    }

    @Test
    fun blankWorkingDirectoryFallsBackToHome() {
        val profile = SessionProfile(id = "p1", name = "dev", workingDirectory = "  ")
        val request = ProfileCommandBuilder.build(profile, environment)
        assertEquals("/home/user", request.workingDirectory)
    }
}
