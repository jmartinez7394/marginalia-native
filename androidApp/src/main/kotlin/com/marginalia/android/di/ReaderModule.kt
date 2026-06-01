package com.marginalia.android.di

import android.content.Context
import com.marginalia.android.platform.reader.ReadiumBookOpener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {

    @Provides
    @Singleton
    fun provideReadiumBookOpener(
        @ApplicationContext context: Context,
        @VaultRootPath vaultRootPath: String
    ): ReadiumBookOpener = ReadiumBookOpener(context, vaultRootPath)
}
