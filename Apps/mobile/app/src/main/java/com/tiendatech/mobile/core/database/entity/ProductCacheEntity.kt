package com.tiendatech.mobile.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_products",
    indices = [Index("categoryId"), Index("name")]
)
data class ProductCacheEntity(
    @PrimaryKey val productId: Long,
    val name: String,
    val description: String?,
    val priceText: String,
    val stock: Int?,
    val enabled: Boolean,
    val categoryId: Long?,
    val categoryName: String?,
    val imageId: Long?,
    val cachedAtEpochMillis: Long
)
