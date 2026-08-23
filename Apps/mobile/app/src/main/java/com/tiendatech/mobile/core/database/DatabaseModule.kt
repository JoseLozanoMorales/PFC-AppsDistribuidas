package com.tiendatech.mobile.core.database

import android.content.Context
import androidx.room.Room
import com.tiendatech.mobile.core.database.dao.CacheMetadataDao
import com.tiendatech.mobile.core.database.dao.CategoryCacheDao
import com.tiendatech.mobile.core.database.dao.ProductCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TiendaTechDatabase =
        Room.databaseBuilder(
            context,
            TiendaTechDatabase::class.java,
            DATABASE_NAME
        ).build()

    @Provides
    fun provideProductCacheDao(database: TiendaTechDatabase): ProductCacheDao =
        database.productCacheDao()

    @Provides
    fun provideCategoryCacheDao(database: TiendaTechDatabase): CategoryCacheDao =
        database.categoryCacheDao()

    @Provides
    fun provideCacheMetadataDao(database: TiendaTechDatabase): CacheMetadataDao =
        database.cacheMetadataDao()

    private const val DATABASE_NAME = "tiendatech_cache.db"
}
