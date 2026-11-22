package com.termux.shell.config

import com.termux.shell.session.ProfileKind
import com.termux.shell.session.SessionProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** [FileProfileStore] 的持久化、更新与错误处理测试。 */
class FileProfileStoreTest {

    private lateinit var directory: File

    private lateinit var file: File

    @Before
    fun setUp() {
        directory = File(System.getProperty("java.io.tmpdir"), "shell-profiles-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        file = File(directory, "shell-profiles.json")
    }

    @After
    fun tearDown() {
        directory.listFiles()?.forEach { it.delete() }
        assertTrue(directory.delete())
    }

    @Test
    fun missingFileStartsEmpty() {
        val store = FileProfileStore(file)
        assertTrue(store.profiles.value.isEmpty())
    }

    @Test
    fun savePersistsAndReloadsFromNewInstance() {
        val store = FileProfileStore(file)
        assertTrue(store.save(SessionProfile(id = "p1", name = "dev")))
        assertEquals(1, store.profiles.value.size)

        val reloaded = FileProfileStore(file)
        assertEquals(1, reloaded.profiles.value.size)
        assertEquals("dev", reloaded.profiles.value[0].name)
    }

    @Test
    fun saveUpdatesExistingEntryById() {
        val store = FileProfileStore(file)
        store.save(SessionProfile(id = "p1", name = "dev"))
        store.save(SessionProfile(id = "p1", name = "dev2", kind = ProfileKind.DISTRO, distro = "debian"))

        assertEquals(1, store.profiles.value.size)
        assertEquals("dev2", store.profiles.value[0].name)
        assertEquals("debian", store.profiles.value[0].distro)
    }

    @Test
    fun deleteRemovesEntryAndReportsMissingId() {
        val store = FileProfileStore(file)
        store.save(SessionProfile(id = "p1", name = "dev"))

        assertTrue(store.delete("p1"))
        assertTrue(store.profiles.value.isEmpty())
        assertFalse(store.delete("p1"))
    }

    @Test
    fun invalidFileKeepsEmptyAndReportsReloadFailure() {
        file.writeText("{not json", Charsets.UTF_8)
        val store = FileProfileStore(file)
        assertTrue(store.profiles.value.isEmpty())
        assertFalse(store.reload())
    }
}
