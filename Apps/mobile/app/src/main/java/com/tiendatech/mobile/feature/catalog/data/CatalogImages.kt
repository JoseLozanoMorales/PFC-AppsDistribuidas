package com.tiendatech.mobile.feature.catalog.data

import com.tiendatech.mobile.BuildConfig

object CatalogImages {
    fun url(imageId: Long?): String? = imageId?.let { "${BuildConfig.API_BASE_URL}api/galeria_v2/img/$it" }
}
