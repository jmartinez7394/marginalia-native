package com.marginalia.android.di

import android.content.Context
import com.marginalia.android.platform.device.AndroidDeviceCapabilities
import com.marginalia.android.platform.display.AndroidDisplayRefreshManager
import com.marginalia.device.DeviceCapabilities
import com.marginalia.device.DisplayRefreshManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {

    @Provides
    @Singleton
    fun provideDeviceCapabilities(@ApplicationContext context: Context): DeviceCapabilities =
        AndroidDeviceCapabilities(context)

    @Provides
    @Singleton
    fun provideDisplayRefreshManager(@ApplicationContext context: Context): DisplayRefreshManager =
        AndroidDisplayRefreshManager(context)
}
