package com.tiendatech.mobile.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tiendatech.mobile.core.database.dao.CacheMetadataDao
import com.tiendatech.mobile.core.database.dao.CategoryCacheDao
import com.tiendatech.mobile.core.database.dao.ProductCacheDao
import com.tiendatech.mobile.core.database.entity.CacheMetadataEntity
import com.tiendatech.mobile.core.database.entity.CategoryCacheEntity
import com.tiendatech.mobile.core.database.entity.ProductCacheEntity

@Database(
    entities = [
        ProductCacheEntity::class,
        CategoryCacheEntity::class,
        CacheMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TiendaTechDatabase : RoomDatabase() {
    abstract fun productCacheDao(): ProductCacheDao
    abstract fun categoryCacheDao(): CategoryCacheDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
}
