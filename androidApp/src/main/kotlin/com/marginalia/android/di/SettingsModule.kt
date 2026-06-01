package com.marginalia.android.di

import android.content.Context
import com.marginalia.android.platform.settings.AndroidSettingsPersistence
import com.marginalia.settings.SettingsRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRegistry(@ApplicationContext context: Context): SettingsRegistry {
        val persistence = AndroidSettingsPersistence(context)
        val registry = SettingsRegistry(persistence)
        AppSettings.all.forEach { registry.register(it) }
        return registry
    }
}
