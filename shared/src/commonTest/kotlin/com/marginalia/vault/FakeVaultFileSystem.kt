package com.marginalia.vault

class FakeVaultFileSystem : VaultFileSystem {

    private val files = mutableMapOf<String, String>()
    private val directories = mutableSetOf<String>()

    override suspend fun readFile(path: String): String? = files[path]

    override suspend fun writeFile(path: String, content: String) {
        files[path] = content
    }

    override suspend fun deleteFile(path: String) {
        files.remove(path)
    }

    override suspend fun listFiles(directory: String): List<String> {
        val prefix = if (directory.endsWith("/")) directory else "$directory/"
        return files.keys.filter { path ->
            path.startsWith(prefix) && !path.removePrefix(prefix).contains("/")
        }
    }

    override suspend fun fileExists(path: String): Boolean = files.containsKey(path)

    override suspend fun createDirectory(path: String) {
        directories.add(path)
    }

    override suspend fun moveFile(fromPath: String, toPath: String) {
        val content = files.remove(fromPath)
        if (content != null) files[toPath] = content
    }
}
