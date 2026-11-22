package com.termux.shell.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** [SessionProfile] 的校验规则测试。 */
class SessionProfileTest {

    @Test
    fun shellProfileNeedsOnlyName() {
        val profile = SessionProfile(id = "p1", name = "dev")
        assertEquals(ProfileKind.SHELL, profile.kind)
        assertEquals("dev", profile.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejected() {
        SessionProfile(id = "p1", name = "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankIdIsRejected() {
        SessionProfile(id = "", name = "dev")
    }

    @Test(expected = IllegalArgumentException::class)
    fun distroProfileRequiresDistroName() {
        SessionProfile(id = "p1", name = "ubuntu", kind = ProfileKind.DISTRO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sshProfileRequiresHost() {
        SessionProfile(id = "p1", name = "server", kind = ProfileKind.SSH)
    }

    @Test
    fun validDistroAndSshProfilesAreAccepted() {
        val distro = SessionProfile(
            id = "p1",
            name = "ubuntu",
            kind = ProfileKind.DISTRO,
            distro = "ubuntu",
        )
        assertEquals("ubuntu", distro.distro)

        val ssh = SessionProfile(
            id = "p2",
            name = "server",
            kind = ProfileKind.SSH,
            sshHost = "user@example.com",
        )
        assertEquals("user@example.com", ssh.sshHost)
    }
}
