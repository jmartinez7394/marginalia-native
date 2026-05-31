package com.marginalia.animachora

import com.marginalia.model.Result
import kotlinx.coroutines.flow.Flow

interface AnimachoraRepository {
    suspend fun getAnimachora(): AnimaChora?
    suspend fun saveAnimachora(animachora: AnimaChora): Result<Unit, AnimachoraError>
    suspend fun addTerritory(territory: Territory): Result<Territory, AnimachoraError>
    suspend fun updateTerritory(territory: Territory): Result<Territory, AnimachoraError>
    fun observeAnimachora(): Flow<AnimaChora?>
}

sealed class AnimachoraError {
    data class WriteError(val message: String) : AnimachoraError()
    object TerritoryNotFound : AnimachoraError()
    object AnimachoraNotInitialised : AnimachoraError()
}
