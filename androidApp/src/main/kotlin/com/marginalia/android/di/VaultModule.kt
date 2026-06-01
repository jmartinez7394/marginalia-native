package com.marginalia.android.di

import android.content.Context
import com.marginalia.android.platform.vault.AndroidLinkedNoteService
import com.marginalia.android.platform.vault.AndroidMarginAnnotationRepository
import com.marginalia.android.platform.vault.AndroidVaultFileSystem
import com.marginalia.android.platform.vault.BookFileRepository
import com.marginalia.vault.HighlightFileRepository
import com.marginalia.vault.HighlightRepository
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.LinkedNoteService
import com.marginalia.vault.MarginAnnotationRepository
import com.marginalia.vault.ConceptFileRegistry
import com.marginalia.vault.ConceptRegistry
import com.marginalia.vault.RegistrySignalFileService
import com.marginalia.vault.RegistrySignalService
import com.marginalia.vault.VaultFileSystem
import java.time.LocalDate
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

    @Provides
    @Singleton
    fun provideLinkedNoteService(fileSystem: VaultFileSystem): LinkedNoteService =
        AndroidLinkedNoteService(fileSystem)

    @Provides
    @Singleton
    fun provideRegistrySignalService(fileSystem: VaultFileSystem): RegistrySignalService =
        RegistrySignalFileService(fileSystem, clock = { System.currentTimeMillis() })

    @Provides
    @Singleton
    fun provideConceptRegistry(fileSystem: VaultFileSystem): ConceptRegistry =
        ConceptFileRegistry(fileSystem, todayIso = { LocalDate.now().toString() })

    @Provides
    @Singleton
    fun provideMarginAnnotationRepository(
        fileSystem: VaultFileSystem,
        libraryRepository: LibraryRepository
    ): MarginAnnotationRepository =
        AndroidMarginAnnotationRepository(fileSystem, libraryRepository)
}
