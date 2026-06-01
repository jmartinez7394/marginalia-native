package com.marginalia.android.platform.vault

import android.util.Log
import com.marginalia.vault.VaultFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidVaultFileSystem(
    private val vaultRoot: File
) : VaultFileSystem {

    init {
        Log.i(TAG, "Vault root: ${vaultRoot.absolutePath}")
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(vaultRoot, path)
        val exists = file.exists()
        Log.d(TAG, "readFile: $path → exists=$exists (${file.absolutePath})")
        if (exists) file.readText() else null
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(vaultRoot, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        Log.d(TAG, "writeFile: $path (${content.length} chars)")
        Unit
    }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        File(vaultRoot, path).delete()
        Unit
    }

    // Returns vault-relative paths (e.g. "library/notes/virtue-ethics.md") to match
    // FakeVaultFileSystem behaviour — callers pass these paths back to readFile.
    override suspend fun listFiles(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = File(vaultRoot, directory)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        val prefix = if (directory.endsWith("/")) directory else "$directory/"
        dir.listFiles()?.map { "$prefix${it.name}" } ?: emptyList()
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(vaultRoot, path).exists()
    }

    override suspend fun createDirectory(path: String) = withContext(Dispatchers.IO) {
        File(vaultRoot, path).mkdirs()
        Unit
    }

    override suspend fun moveFile(fromPath: String, toPath: String) = withContext(Dispatchers.IO) {
        val source = File(vaultRoot, fromPath)
        val dest = File(vaultRoot, toPath)
        dest.parentFile?.mkdirs()
        source.renameTo(dest)
        Unit
    }

    companion object {
        private const val TAG = "AndroidVaultFileSystem"
    }
}
