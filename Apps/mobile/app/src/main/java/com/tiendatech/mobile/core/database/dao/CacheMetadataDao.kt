package com.tiendatech.mobile.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tiendatech.mobile.core.database.entity.CacheMetadataEntity

@Dao
interface CacheMetadataDao {
    @Query("SELECT * FROM cache_metadata WHERE cacheKey = :key LIMIT 1")
    suspend fun find(key: String): CacheMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: CacheMetadataEntity)

    @Query("DELETE FROM cache_metadata")
    suspend fun clear()
}
