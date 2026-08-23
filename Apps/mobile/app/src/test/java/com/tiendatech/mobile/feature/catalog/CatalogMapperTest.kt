package com.tiendatech.mobile.feature.catalog

import com.tiendatech.mobile.feature.catalog.data.CatalogMapper
import com.tiendatech.mobile.feature.catalog.data.CategoryDto
import com.tiendatech.mobile.feature.catalog.data.ProductDto
import com.tiendatech.mobile.feature.catalog.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMapperTest {
    @Test fun `summary snake case fields are mapped`() {
        val entity = CatalogMapper.product(
            ProductDto(productIdSnake = 8, nombre = "Procesador", preciounitario = 750000.0, stock = 3, galleryIdSnake = 11),
            now = 100
        )
        requireNotNull(entity)
        assertEquals(8L, entity.productId)
        assertEquals("750000.0", entity.priceText)
        assertEquals(11L, entity.imageId)
    }

    @Test fun `legacy aliases and supplied category are mapped`() {
        val entity = CatalogMapper.product(
            ProductDto(legacyProductId = 9, producto = "Memoria", precio = 120000.0),
            category = Category(4, "RAM"), now = 100
        )
        requireNotNull(entity)
        assertEquals(4L, entity.categoryId)
        assertEquals("RAM", entity.categoryName)
    }

    @Test fun `disabled product is excluded`() {
        assertNull(CatalogMapper.product(ProductDto(productIdSnake = 1, habilitado = false)))
    }

    @Test fun `category supports backend id categoria`() {
        val category = CatalogMapper.category(CategoryDto(categoryId = 6, nombre = "Monitores"), now = 100)
        requireNotNull(category)
        assertEquals(6L, category.categoryId)
        assertEquals("Monitores", category.name)
    }
}
