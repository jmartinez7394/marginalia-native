package com.marginalia.android.platform.vault

import com.marginalia.model.Book
import com.marginalia.model.BookFormat
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import com.marginalia.model.Result
import com.marginalia.vault.LibraryError
import com.marginalia.vault.VaultFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookFileRepositoryTest {

    private lateinit var fakeFs: FakeVaultFileSystem
    private lateinit var repo: BookFileRepository

    @BeforeTest
    fun setUp() {
        fakeFs = FakeVaultFileSystem()
        repo = BookFileRepository(fakeFs)
    }

    private fun makeBook(
        id: String = "book-1",
        title: String = "Meditations",
        territoryId: String = "library-default"
    ) = Book(
        id = id,
        title = title,
        author = "Marcus Aurelius",
        format = BookFormat.EPUB,
        filePath = "books/$id.epub",
        coverPath = null,
        addedAt = 1000L,
        lastOpenedAt = null,
        readingProgress = ReadingProgress.create(null, null, 0f, null),
        status = ReadingStatus.UNREAD,
        territoryId = territoryId
    )

    @Test
    fun `getAllBooks returns empty list for empty vault`() = runTest {
        val books = repo.getAllBooks("library-default")
        assertTrue(books.isEmpty())
    }

    @Test
    fun `addBook then getAllBooks returns the added book`() = runTest {
        val book = makeBook()
        val addResult = repo.addBook(book)
        assertTrue(addResult is Result.Success)

        val books = repo.getAllBooks("library-default")
        assertEquals(1, books.size)
        assertEquals(book, books.first())
    }

    @Test
    fun `book survives serialization round trip with all fields intact`() = runTest {
        val book = makeBook(id = "round-trip-1", title = "Nicomachean Ethics").copy(
            coverPath = ".marginalia/covers/round-trip-1.jpg",
            lastOpenedAt = 5000L,
            readingProgress = ReadingProgress.create("epubcfi(/6/4!/4/2/1:0)", null, 0.42f, 4000L),
            status = ReadingStatus.READING
        )

        repo.addBook(book)
        val books = repo.getAllBooks("library-default")

        assertEquals(1, books.size)
        val loaded = books.first()
        assertEquals(book.id, loaded.id)
        assertEquals(book.title, loaded.title)
        assertEquals(book.coverPath, loaded.coverPath)
        assertEquals(book.lastOpenedAt, loaded.lastOpenedAt)
        assertEquals(book.readingProgress.cfi, loaded.readingProgress.cfi)
        assertEquals(book.readingProgress.percentage, loaded.readingProgress.percentage)
        assertEquals(book.status, loaded.status)
    }

    @Test
    fun `updateBook updates the correct fields`() = runTest {
        val original = makeBook()
        repo.addBook(original)

        val updated = original.copy(
            status = ReadingStatus.READING,
            readingProgress = ReadingProgress.create(null, null, 0.25f, 2000L)
        )
        val updateResult = repo.updateBook(updated)
        assertTrue(updateResult is Result.Success)

        val books = repo.getAllBooks("library-default")
        assertEquals(1, books.size)
        assertEquals(ReadingStatus.READING, books.first().status)
        assertEquals(0.25f, books.first().readingProgress.percentage)
    }

    @Test
    fun `addBook returns BookAlreadyExists for duplicate id`() = runTest {
        val book = makeBook()
        repo.addBook(book)
        val second = repo.addBook(book)
        assertTrue(second is Result.Failure)
        assertTrue(second.error is LibraryError.BookAlreadyExists)
    }

    @Test
    fun `observeBooks emits updated list after addBook`() = runTest {
        val emptyList = repo.observeBooks("library-default").first()
        assertTrue(emptyList.isEmpty())

        repo.addBook(makeBook("b1"))
        repo.getAllBooks("library-default")

        val loaded = repo.observeBooks("library-default").first()
        assertEquals(1, loaded.size)
    }

    @Test
    fun `books in different territories are independent`() = runTest {
        repo.addBook(makeBook(id = "lib-1", territoryId = "library-default"))
        repo.addBook(makeBook(id = "study-1", territoryId = "study-default"))

        assertEquals(1, repo.getAllBooks("library-default").size)
        assertEquals(1, repo.getAllBooks("study-default").size)
        assertNull(repo.getAllBooks("library-default").find { it.id == "study-1" })
    }
}

private class FakeVaultFileSystem : VaultFileSystem {
    private val files = mutableMapOf<String, String>()

    override suspend fun readFile(path: String): String? = files[path]

    override suspend fun writeFile(path: String, content: String) {
        files[path] = content
    }

    override suspend fun deleteFile(path: String) {
        files.remove(path)
    }

    override suspend fun listFiles(directory: String): List<String> =
        files.keys.filter { it.startsWith("$directory/") }
            .map { it.removePrefix("$directory/").substringBefore("/") }
            .distinct()

    override suspend fun fileExists(path: String): Boolean = files.containsKey(path)

    override suspend fun createDirectory(path: String) {}

    override suspend fun moveFile(fromPath: String, toPath: String) {
        val content = files.remove(fromPath)
        if (content != null) files[toPath] = content
    }
}
