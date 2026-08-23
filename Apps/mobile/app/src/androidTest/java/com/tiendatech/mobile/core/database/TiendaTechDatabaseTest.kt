package com.tiendatech.mobile.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiendatech.mobile.core.database.entity.ProductCacheEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TiendaTechDatabaseTest {
    private lateinit var database: TiendaTechDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TiendaTechDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun productCache_canInsertReadAndClear() = runBlocking {
        val product = ProductCacheEntity(
            productId = 42,
            name = "Procesador",
            description = "Producto de prueba",
            priceText = "199.99",
            stock = 5,
            enabled = true,
            categoryId = 2,
            categoryName = "CPU",
            imageId = null,
            cachedAtEpochMillis = 1_000
        )

        database.productCacheDao().upsertAll(listOf(product))
        assertEquals(product, database.productCacheDao().findById(42))

        database.productCacheDao().clear()
        assertNull(database.productCacheDao().findById(42))
    }
}
