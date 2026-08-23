package com.tiendatech.mobile.feature.scanner.data

import com.tiendatech.mobile.feature.scanner.domain.BarcodeLookupResult
import com.tiendatech.mobile.feature.scanner.domain.ProductLookupByBarcode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {
    @Provides
    @Singleton
    fun provideProductLookupByBarcode(): ProductLookupByBarcode = ProductLookupByBarcode {
        BarcodeLookupResult.BackendUnavailable
    }
}
