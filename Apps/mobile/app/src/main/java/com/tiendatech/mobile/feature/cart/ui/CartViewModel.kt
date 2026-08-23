package com.tiendatech.mobile.feature.cart.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.cart.data.CartRepository
import com.tiendatech.mobile.feature.cart.domain.CartResult
import com.tiendatech.mobile.feature.cart.domain.ShoppingCart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartUiState(
    val loading: Boolean = false,
    val cart: ShoppingCart? = null,
    val busyProductId: Long? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CartRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()
    private var currentUserId: Long? = null

    fun load(userId: Long, onUnauthorized: () -> Unit = {}) {
        currentUserId = userId
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, message = null)
            applyLoadResult(repository.load(userId), onUnauthorized)
        }
    }

    fun add(userId: Long, productId: Long, quantity: Int, onUnauthorized: () -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busyProductId = productId, error = null, message = null)
            when (val result = repository.add(userId, productId, quantity)) {
                is CartResult.Success -> {
                    _state.value = _state.value.copy(busyProductId = null, message = "Producto añadido al carrito")
                    onSuccess()
                }
                is CartResult.Failure -> _state.value = _state.value.copy(busyProductId = null, error = result.message)
                CartResult.Unauthorized -> { _state.value = CartUiState(); onUnauthorized() }
            }
        }
    }

    fun update(productId: Long, quantity: Int, onUnauthorized: () -> Unit) {
        val cartId = _state.value.cart?.id ?: return
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyProductId = productId, error = null)
            when (val result = repository.update(cartId, productId, quantity)) {
                is CartResult.Success -> applyLoadResult(repository.load(userId), onUnauthorized)
                is CartResult.Failure -> _state.value = _state.value.copy(busyProductId = null, error = result.message)
                CartResult.Unauthorized -> { _state.value = CartUiState(); onUnauthorized() }
            }
        }
    }

    fun remove(productId: Long, onUnauthorized: () -> Unit) {
        val cartId = _state.value.cart?.id ?: return
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyProductId = productId, error = null)
            when (val result = repository.remove(cartId, productId)) {
                is CartResult.Success -> applyLoadResult(repository.load(userId), onUnauthorized)
                is CartResult.Failure -> _state.value = _state.value.copy(busyProductId = null, error = result.message)
                CartResult.Unauthorized -> { _state.value = CartUiState(); onUnauthorized() }
            }
        }
    }

    private fun applyLoadResult(result: CartResult<ShoppingCart>, onUnauthorized: () -> Unit) {
        _state.value = when (result) {
            is CartResult.Success -> CartUiState(cart = result.value)
            is CartResult.Failure -> _state.value.copy(loading = false, busyProductId = null, error = result.message)
            CartResult.Unauthorized -> { onUnauthorized(); CartUiState() }
        }
    }
}
