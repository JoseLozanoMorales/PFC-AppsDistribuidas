package com.tiendatech.mobile.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tiendatech.mobile.core.database.entity.ProductCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductCacheDao {
    @Query("SELECT * FROM cached_products WHERE enabled = 1 ORDER BY name COLLATE NOCASE")
    fun observeEnabledProducts(): Flow<List<ProductCacheEntity>>

    @Query("SELECT * FROM cached_products WHERE productId = :productId LIMIT 1")
    suspend fun findById(productId: Long): ProductCacheEntity?

    @Upsert
    suspend fun upsertAll(products: List<ProductCacheEntity>)

    @Query("DELETE FROM cached_products")
    suspend fun clear()
}
