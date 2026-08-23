package com.tiendatech.mobile.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_categories")
data class CategoryCacheEntity(
    @PrimaryKey val categoryId: Long,
    val name: String,
    val enabled: Boolean = true,
    val cachedAtEpochMillis: Long
)
