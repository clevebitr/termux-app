package com.termux.shell.config

import com.termux.shell.session.ProfileKind
import com.termux.shell.session.SessionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ProfileCodec] 的编码与解码测试。 */
class ProfileCodecTest {

    @Test
    fun roundTripPreservesAllFields() {
        val profiles = listOf(
            SessionProfile(
                id = "p1",
                name = "dev",
                kind = ProfileKind.SHELL,
                command = "bash -l",
                workingDirectory = "/data/data/com.termux/files/home",
                environment = linkedMapOf("LANG" to "en_US.UTF-8", "EDITOR" to "nvim"),
            ),
            SessionProfile(id = "p2", name = "ubuntu", kind = ProfileKind.DISTRO, distro = "ubuntu"),
            SessionProfile(id = "p3", name = "server", kind = ProfileKind.SSH, sshHost = "user@example.com"),
        )

        val decoded = ProfileCodec.decode(ProfileCodec.encode(profiles))
        assertEquals(profiles, decoded)
    }

    @Test
    fun decodeRejectsInvalidDocuments() {
        assertNull(ProfileCodec.decode(null))
        assertNull(ProfileCodec.decode(""))
        assertNull(ProfileCodec.decode("{}"))
        assertNull(ProfileCodec.decode("""{"version":2,"profiles":[]}"""))
        assertNull(ProfileCodec.decode("""{"version":1,"profiles":[{"id":"p1","name":""}]}"""))
        assertNull(
            ProfileCodec.decode(
                """{"version":1,"profiles":[
                    {"id":"p1","name":"a"},
                    {"id":"p1","name":"b"}]}""",
            ),
        )
        assertNull(
            ProfileCodec.decode(
                """{"version":1,"profiles":[{"id":"p1","name":"a","kind":"UNKNOWN"}]}""",
            ),
        )
        assertNull(
            ProfileCodec.decode(
                """{"version":1,"profiles":[{"id":"p1","name":"a","kind":"DISTRO"}]}""",
            ),
        )
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val document = """{"version":1,"profiles":[
            {"id":"p1","name":"dev","kind":"SHELL","futureField":"ignored"}]}"""
        val decoded = ProfileCodec.decode(document)
        assertEquals(1, decoded?.size)
        assertEquals("dev", decoded?.get(0)?.name)
    }
}
