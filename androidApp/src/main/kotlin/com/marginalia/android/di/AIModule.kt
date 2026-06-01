package com.marginalia.android.di

import com.marginalia.ai.AIProvider
import com.marginalia.ai.KeyRepository
import com.marginalia.android.platform.ai.AndroidKeyRepository
import com.marginalia.android.platform.ai.AnthropicAIProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    @Binds
    @Singleton
    abstract fun provideKeyRepository(impl: AndroidKeyRepository): KeyRepository

    @Binds
    @Singleton
    abstract fun provideAIProvider(impl: AnthropicAIProvider): AIProvider
}
