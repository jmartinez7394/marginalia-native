package com.marginalia.vault

interface VaultFileSystem {
    suspend fun readFile(path: String): String?
    suspend fun writeFile(path: String, content: String)
    suspend fun deleteFile(path: String)
    suspend fun listFiles(directory: String): List<String>
    suspend fun fileExists(path: String): Boolean
    suspend fun createDirectory(path: String)
    suspend fun moveFile(fromPath: String, toPath: String)
}
