package com.marginalia.vault

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeVaultFileSystemTest {

    @Test
    fun writeFileThenReadFileReturnsSameContent() = runTest {
        val fs = FakeVaultFileSystem()
        fs.writeFile("library/notes/book.md", "# My Notes")
        assertEquals("# My Notes", fs.readFile("library/notes/book.md"))
    }

    @Test
    fun readFileReturnsNullForMissingPath() = runTest {
        val fs = FakeVaultFileSystem()
        assertNull(fs.readFile("missing/path.md"))
    }

    @Test
    fun fileExistsReturnsTrueAfterWrite() = runTest {
        val fs = FakeVaultFileSystem()
        assertFalse(fs.fileExists("notes/test.md"))
        fs.writeFile("notes/test.md", "content")
        assertTrue(fs.fileExists("notes/test.md"))
    }

    @Test
    fun fileExistsReturnsFalseAfterDelete() = runTest {
        val fs = FakeVaultFileSystem()
        fs.writeFile("notes/test.md", "content")
        fs.deleteFile("notes/test.md")
        assertFalse(fs.fileExists("notes/test.md"))
    }

    @Test
    fun listFilesReturnsFilesInDirectory() = runTest {
        val fs = FakeVaultFileSystem()
        fs.writeFile("library/book1.md", "a")
        fs.writeFile("library/book2.md", "b")
        fs.writeFile("workshop/note.md", "c")
        val files = fs.listFiles("library")
        assertEquals(2, files.size)
        assertTrue(files.contains("library/book1.md"))
        assertTrue(files.contains("library/book2.md"))
        assertFalse(files.contains("workshop/note.md"))
    }

    @Test
    fun listFilesExcludesNestedSubdirectoryFiles() = runTest {
        val fs = FakeVaultFileSystem()
        fs.writeFile("library/book.md", "a")
        fs.writeFile("library/subfolder/nested.md", "b")
        val files = fs.listFiles("library")
        assertTrue(files.contains("library/book.md"))
        assertFalse(files.contains("library/subfolder/nested.md"))
    }

    @Test
    fun moveFileTransfersContent() = runTest {
        val fs = FakeVaultFileSystem()
        fs.writeFile("old/path.md", "moved content")
        fs.moveFile("old/path.md", "new/path.md")
        assertNull(fs.readFile("old/path.md"))
        assertEquals("moved content", fs.readFile("new/path.md"))
    }
}
