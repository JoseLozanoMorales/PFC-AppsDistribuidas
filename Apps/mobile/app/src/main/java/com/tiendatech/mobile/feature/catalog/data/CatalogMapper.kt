package com.tiendatech.mobile.feature.catalog.data

import com.tiendatech.mobile.core.database.entity.CategoryCacheEntity
import com.tiendatech.mobile.core.database.entity.ProductCacheEntity
import com.tiendatech.mobile.feature.catalog.domain.Category
import com.tiendatech.mobile.feature.catalog.domain.Product

object CatalogMapper {
    fun product(dto: ProductDto, category: Category? = null, now: Long = System.currentTimeMillis()): ProductCacheEntity? {
        val id = dto.productIdSnake ?: dto.productoId ?: dto.legacyProductId ?: dto.id ?: return null
        if (dto.habilitado == false) return null
        val categoryId = dto.categoryIdSnake ?: dto.categoriaId ?: category?.id
        val categoryName = dto.categoria ?: dto.categoryNameSnake ?: dto.nombre_categoria ?: category?.name
        return ProductCacheEntity(
            productId = id,
            name = dto.nombre ?: dto.producto ?: "Producto",
            description = dto.descripcion,
            priceText = (dto.preciounitario ?: dto.precioUnitario ?: dto.precio ?: dto.costo ?: 0.0).toString(),
            stock = dto.stock,
            enabled = true,
            categoryId = categoryId,
            categoryName = categoryName,
            imageId = dto.imagenId ?: dto.imageIdSnake ?: dto.portadaId ?: dto.coverIdSnake ?: dto.galeriaId ?: dto.galleryIdSnake,
            cachedAtEpochMillis = now
        )
    }

    fun category(dto: CategoryDto, now: Long = System.currentTimeMillis()): CategoryCacheEntity? {
        val id = dto.id ?: dto.categoryId ?: return null
        if (dto.habilitado == false || dto.nombre.isBlank()) return null
        return CategoryCacheEntity(id, dto.nombre, true, now)
    }

    fun domain(entity: ProductCacheEntity, gallery: List<Long> = emptyList()) = Product(
        id = entity.productId,
        name = entity.name,
        description = entity.description,
        price = entity.priceText.toDoubleOrNull() ?: 0.0,
        stock = entity.stock,
        categoryId = entity.categoryId,
        categoryName = entity.categoryName,
        imageId = entity.imageId,
        galleryImageIds = gallery
    )

    fun domain(entity: CategoryCacheEntity) = Category(entity.categoryId, entity.name)
}
