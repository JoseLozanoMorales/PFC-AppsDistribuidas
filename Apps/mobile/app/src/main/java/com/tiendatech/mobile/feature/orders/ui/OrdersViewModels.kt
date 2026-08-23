package com.tiendatech.mobile.feature.orders.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.orders.data.OrdersRepository
import com.tiendatech.mobile.feature.orders.domain.OrderDetail
import com.tiendatech.mobile.feature.orders.domain.OrderSummary
import com.tiendatech.mobile.feature.orders.domain.OrdersResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(val loading: Boolean = false, val loadingMore: Boolean = false, val orders: List<OrderSummary> = emptyList(), val page: Int = 0, val totalPages: Int = 0, val error: String? = null)

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repository: OrdersRepository) : ViewModel() {
    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()
    private var userId: Long = 0

    fun load(id: Long, unauthorized: () -> Unit) { userId = id; loadPage(0, false, unauthorized) }
    fun loadMore(unauthorized: () -> Unit) { val next = _state.value.page + 1; if (!_state.value.loadingMore && next < _state.value.totalPages) loadPage(next, true, unauthorized) }
    private fun loadPage(page: Int, append: Boolean, unauthorized: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = !append, loadingMore = append, error = null)
        _state.value = when (val result = repository.page(userId, page)) {
            is OrdersResult.Success -> OrdersUiState(orders = if (append) _state.value.orders + result.value.orders else result.value.orders, page = result.value.page, totalPages = result.value.totalPages)
            is OrdersResult.Failure -> _state.value.copy(loading = false, loadingMore = false, error = result.message)
            OrdersResult.Unauthorized -> { unauthorized(); OrdersUiState() }
        }
    }
}

data class OrderDetailUiState(val loading: Boolean = false, val detail: OrderDetail? = null, val error: String? = null)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: OrdersRepository) : ViewModel() {
    private val orderId: Long = checkNotNull(savedStateHandle["orderId"])
    private val _state = MutableStateFlow(OrderDetailUiState())
    val state: StateFlow<OrderDetailUiState> = _state.asStateFlow()
    fun load(userId: Long, unauthorized: () -> Unit) = viewModelScope.launch {
        _state.value = OrderDetailUiState(loading = true)
        _state.value = when (val result = repository.detail(userId, orderId)) {
            is OrdersResult.Success -> OrderDetailUiState(detail = result.value)
            is OrdersResult.Failure -> OrderDetailUiState(error = result.message)
            OrdersResult.Unauthorized -> { unauthorized(); OrderDetailUiState() }
        }
    }
}
