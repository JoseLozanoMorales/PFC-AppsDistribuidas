package com.tiendatech.mobile.feature.scanner

import com.tiendatech.mobile.feature.scanner.domain.BarcodeLookupResult
import com.tiendatech.mobile.feature.scanner.domain.BarcodePolicy
import com.tiendatech.mobile.feature.scanner.domain.DemoBarcodeCatalog
import com.tiendatech.mobile.feature.scanner.domain.ProductLookupByBarcode
import com.tiendatech.mobile.feature.scanner.ui.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `normaliza espacios y acepta EAN`() {
        assertEquals("7501234567890", BarcodePolicy.normalize(" 7501234567890 "))
        assertNull(BarcodePolicy.validationMessage("7501234567890"))
    }

    @Test fun `rechaza codigo numerico demasiado corto`() {
        assertEquals("El código numérico debe tener entre 8 y 14 dígitos", BarcodePolicy.validationMessage("1234"))
    }

    @Test fun `una deteccion pausa analisis y evita duplicados`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = ScannerViewModel(ProductLookupByBarcode { calls++; BarcodeLookupResult.BackendUnavailable })
        viewModel.detected("7501234567890")
        viewModel.detected("12345678")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(viewModel.state.value.analysisPaused)
        assertEquals("7501234567890", viewModel.state.value.code)
    }

    @Test fun `lookup encontrado expone producto`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(ProductLookupByBarcode { BarcodeLookupResult.Found(42, "Producto de prueba") })
        viewModel.codeChanged("ABC-42")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(42L, viewModel.state.value.productId)
        assertEquals("Producto encontrado: Producto de prueba", viewModel.state.value.message)
    }

    @Test fun `catalogo demo contiene cinco codigos asociados a productos reales`() {
        assertEquals(5, DemoBarcodeCatalog.entries.size)
        assertEquals(5, DemoBarcodeCatalog.entries.map { it.productId }.distinct().size)
        assertTrue(DemoBarcodeCatalog.entries.all { it.productName.isNotBlank() && it.categoryName.isNotBlank() })
        assertTrue(DemoBarcodeCatalog.entries.all { BarcodePolicy.validationMessage(it.code) == null })
    }

    @Test fun `reintentar limpia lectura y reactiva camara`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(ProductLookupByBarcode { BarcodeLookupResult.NotFound })
        viewModel.detected("12345678")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.retry()
        assertFalse(viewModel.state.value.analysisPaused)
        assertEquals("", viewModel.state.value.code)
        assertNull(viewModel.state.value.message)
    }
}
