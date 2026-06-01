package com.marginalia.android.di

import android.content.Context
import com.marginalia.android.platform.vault.AndroidVaultFileSystem
import com.marginalia.android.platform.vault.BookFileRepository
import com.marginalia.vault.HighlightFileRepository
import com.marginalia.vault.HighlightRepository
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.VaultFileSystem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VaultModule {

    @Provides
    @Singleton
    @VaultRootPath
    fun provideVaultRootPath(@ApplicationContext context: Context): String =
        (context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath) +
            "/marginalia-vault"

    @Provides
    @Singleton
    fun provideVaultFileSystem(@VaultRootPath path: String): VaultFileSystem =
        AndroidVaultFileSystem(File(path))

    @Provides
    @Singleton
    fun provideLibraryRepository(fileSystem: VaultFileSystem): LibraryRepository =
        BookFileRepository(fileSystem)

    @Provides
    @Singleton
    fun provideHighlightRepository(
        fileSystem: VaultFileSystem,
        libraryRepository: LibraryRepository
    ): HighlightRepository =
        HighlightFileRepository(fileSystem, libraryRepository)
}
