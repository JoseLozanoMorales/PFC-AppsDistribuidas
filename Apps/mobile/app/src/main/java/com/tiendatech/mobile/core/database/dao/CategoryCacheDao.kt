package com.tiendatech.mobile.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tiendatech.mobile.core.database.entity.CategoryCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryCacheDao {
    @Query("SELECT * FROM cached_categories WHERE enabled = 1 ORDER BY name COLLATE NOCASE")
    fun observeEnabledCategories(): Flow<List<CategoryCacheEntity>>

    @Query("SELECT * FROM cached_categories WHERE categoryId = :id LIMIT 1")
    suspend fun findById(id: Long): CategoryCacheEntity?

    @Upsert
    suspend fun upsertAll(categories: List<CategoryCacheEntity>)

    @Query("DELETE FROM cached_categories")
    suspend fun clear()
}
