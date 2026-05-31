package com.marginalia.model

import com.marginalia.vault.LibraryError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LibraryErrorTest {

    @Test
    fun fileNotFoundCarriesPath() {
        val error = LibraryError.FileNotFound("vault/library/missing.epub")
        assertEquals("vault/library/missing.epub", error.path)
    }

    @Test
    fun parseErrorCarriesMessage() {
        val error = LibraryError.ParseError("unexpected token at line 5")
        assertEquals("unexpected token at line 5", error.message)
    }

    @Test
    fun writeErrorCarriesMessage() {
        val error = LibraryError.WriteError("disk full")
        assertEquals("disk full", error.message)
    }

    @Test
    fun bookAlreadyExistsIsSingleton() {
        assertEquals(LibraryError.BookAlreadyExists, LibraryError.BookAlreadyExists)
    }

    @Test
    fun errorVariantsAreDistinct() {
        val fileNotFound: LibraryError = LibraryError.FileNotFound("path")
        val parseError: LibraryError = LibraryError.ParseError("msg")
        val writeError: LibraryError = LibraryError.WriteError("msg")
        val alreadyExists: LibraryError = LibraryError.BookAlreadyExists

        assertNotEquals(fileNotFound, parseError)
        assertNotEquals(fileNotFound, writeError)
        assertNotEquals(fileNotFound, alreadyExists)
        assertNotEquals(parseError, writeError)
        assertNotEquals(parseError, alreadyExists)
        assertNotEquals(writeError, alreadyExists)
    }

    @Test
    fun errorIsExhaustive() {
        val errors: List<LibraryError> = listOf(
            LibraryError.FileNotFound("p"),
            LibraryError.ParseError("m"),
            LibraryError.WriteError("m"),
            LibraryError.BookAlreadyExists
        )
        errors.forEach { error ->
            val handled = when (error) {
                is LibraryError.FileNotFound -> true
                is LibraryError.ParseError -> true
                is LibraryError.WriteError -> true
                is LibraryError.BookAlreadyExists -> true
            }
            assertTrue(handled)
        }
    }
}
