package com.tiendatech.mobile.feature.scanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.scanner.domain.BarcodeLookupResult
import com.tiendatech.mobile.feature.scanner.domain.BarcodePolicy
import com.tiendatech.mobile.feature.scanner.domain.ProductLookupByBarcode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val code: String = "",
    val validationError: String? = null,
    val lookingUp: Boolean = false,
    val message: String? = null,
    val productId: Long? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val analysisPaused: Boolean = false
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val lookup: ProductLookupByBarcode
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = mutableState.asStateFlow()

    fun codeChanged(value: String) {
        mutableState.update { it.copy(code = value, validationError = null, message = null, productId = null, categoryId = null, categoryName = null) }
    }

    fun detected(value: String) {
        if (mutableState.value.analysisPaused) return
        mutableState.update { it.copy(code = BarcodePolicy.normalize(value), analysisPaused = true) }
        search()
    }

    fun search() {
        val code = BarcodePolicy.normalize(mutableState.value.code)
        val error = BarcodePolicy.validationMessage(code)
        if (error != null) {
            mutableState.update { it.copy(validationError = error) }
            return
        }
        mutableState.update { it.copy(code = code, validationError = null, lookingUp = true, message = null, productId = null, categoryId = null, categoryName = null, analysisPaused = true) }
        viewModelScope.launch {
            when (val result = lookup.find(code)) {
                is BarcodeLookupResult.Found -> mutableState.update {
                    it.copy(lookingUp = false, productId = result.productId, message = "Producto encontrado: ${result.productName}")
                }
                BarcodeLookupResult.NotFound -> mutableState.update { it.copy(lookingUp = false, message = "No se encontró un producto para este código") }
                BarcodeLookupResult.BackendUnavailable -> mutableState.update { it.copy(lookingUp = false, message = "Código leído correctamente. El catálogo todavía no permite buscar productos por código de barras.") }
            }
        }
    }

    fun retry() {
        mutableState.value = ScannerUiState()
    }
}
