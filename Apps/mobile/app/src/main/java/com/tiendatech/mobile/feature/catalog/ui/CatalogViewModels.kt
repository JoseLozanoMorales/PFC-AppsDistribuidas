package com.tiendatech.mobile.feature.catalog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.catalog.data.CatalogRepository
import com.tiendatech.mobile.feature.catalog.domain.CatalogResult
import com.tiendatech.mobile.feature.catalog.domain.Category
import com.tiendatech.mobile.feature.catalog.domain.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val selectedCategoryId: Long? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val status = MutableStateFlow(CatalogStatus())
    private var catalogRequest: Job? = null
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeCatalog(), query, selectedCategoryId, status) { snapshot, search, categoryId, currentStatus ->
                val filtered = snapshot.products.filter { product ->
                    (categoryId == null || product.categoryId == categoryId) &&
                        (search.isBlank() || product.name.contains(search.trim(), ignoreCase = true))
                }
                CatalogUiState(
                    loading = currentStatus.loading && snapshot.products.isEmpty(),
                    refreshing = currentStatus.refreshing,
                    products = filtered,
                    categories = snapshot.categories,
                    query = search,
                    selectedCategoryId = categoryId,
                    loadingMore = currentStatus.loadingMore,
                    canLoadMore = currentStatus.canLoadMore,
                    message = currentStatus.message
                )
            }.collect { _state.value = it }
        }
        refresh(initial = true)
    }

    fun search(value: String) { query.value = value }

    fun selectCategory(category: Category?) {
        selectedCategoryId.value = category?.id
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        catalogRequest?.cancel()
        catalogRequest = viewModelScope.launch {
            status.value = CatalogStatus(loading = initial, refreshing = !initial)
            status.value = when (val result = repository.refresh(selectedCategoryId.value)) {
                is CatalogResult.Success -> CatalogStatus(canLoadMore = result.value, nextPage = 1)
                is CatalogResult.Failure -> CatalogStatus(message = result.message)
            }
        }
    }

    fun loadNextPage() {
        val current = status.value
        if (current.loading || current.refreshing || current.loadingMore || !current.canLoadMore) return
        catalogRequest = viewModelScope.launch {
            status.value = current.copy(loadingMore = true, message = null)
            status.value = when (val result = repository.loadNextPage(current.nextPage, selectedCategoryId.value)) {
                is CatalogResult.Success -> CatalogStatus(
                    canLoadMore = result.value,
                    nextPage = current.nextPage + 1
                )
                is CatalogResult.Failure -> current.copy(loadingMore = false, message = result.message)
            }
        }
    }

    private data class CatalogStatus(
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = true,
        val nextPage: Int = 0,
        val message: String? = null
    )
}

data class ProductDetailUiState(
    val loading: Boolean = true,
    val product: Product? = null,
    val error: String? = null
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository
) : ViewModel() {
    private val productId: Long = checkNotNull(savedStateHandle["productId"])
    private val _state = MutableStateFlow(ProductDetailUiState())
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        val cached = repository.cachedProduct(productId)
        _state.value = ProductDetailUiState(loading = cached == null, product = cached)
        _state.value = when (val result = repository.product(productId)) {
            is CatalogResult.Success -> ProductDetailUiState(loading = false, product = result.value)
            is CatalogResult.Failure -> ProductDetailUiState(loading = false, product = cached, error = result.message)
        }
    }
}
