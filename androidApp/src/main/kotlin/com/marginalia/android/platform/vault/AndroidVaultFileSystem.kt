package com.marginalia.android.platform.vault

import com.marginalia.vault.VaultFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidVaultFileSystem(
    private val vaultRoot: File
) : VaultFileSystem {

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(vaultRoot, path)
        if (file.exists()) file.readText() else null
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(vaultRoot, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        File(vaultRoot, path).delete()
        Unit
    }

    override suspend fun listFiles(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = File(vaultRoot, directory)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()?.map { it.name } ?: emptyList()
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
}
