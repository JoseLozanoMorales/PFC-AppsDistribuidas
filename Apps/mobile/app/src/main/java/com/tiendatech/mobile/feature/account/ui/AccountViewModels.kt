package com.tiendatech.mobile.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.account.data.AccountRepository
import com.tiendatech.mobile.feature.account.domain.AccountData
import com.tiendatech.mobile.feature.account.domain.AccountResult
import com.tiendatech.mobile.feature.account.domain.OrderConfirmation
import com.tiendatech.mobile.feature.cart.data.CartRepository
import com.tiendatech.mobile.feature.cart.domain.CartResult
import com.tiendatech.mobile.feature.cart.domain.ShoppingCart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountUiState(val loading: Boolean = false, val data: AccountData? = null, val busy: Boolean = false, val message: String? = null, val error: String? = null)

@HiltViewModel
class AccountViewModel @Inject constructor(private val repository: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()
    private var userId: Long = 0

    fun load(id: Long, unauthorized: () -> Unit) { userId = id; viewModelScope.launch { _state.value = _state.value.copy(loading = true); handleLoad(repository.load(id), unauthorized) } }
    fun saveAddress(id: Long?, street: String, reference: String?, cityId: Long, unauthorized: () -> Unit) = mutate(unauthorized) { repository.saveAddress(userId, id, street, reference, cityId) }
    fun deleteAddress(id: Long, unauthorized: () -> Unit) = mutate(unauthorized) { repository.deleteAddress(userId, id) }
    fun createPayment(card: String, expiration: String, typeId: Long, unauthorized: () -> Unit, clearSensitive: () -> Unit) = mutate(unauthorized, clearSensitive) { repository.createPayment(card, expiration, typeId) }
    fun updatePayment(id: Long, card: String, expiration: String, typeId: Long, enabled: Boolean, unauthorized: () -> Unit, clearSensitive: () -> Unit) = mutate(unauthorized, clearSensitive) { repository.updatePayment(id, card, expiration, typeId, enabled) }
    fun setPaymentEnabled(id: Long, enabled: Boolean, unauthorized: () -> Unit) = mutate(unauthorized) { repository.setPaymentEnabled(id, enabled) }
    fun changePassword(current: String, new: String, repeated: String, unauthorized: () -> Unit, clearSensitive: () -> Unit) = mutate(unauthorized, clearSensitive) { repository.changePassword(current, new, repeated) }

    private fun mutate(unauthorized: () -> Unit, success: () -> Unit = {}, action: suspend () -> AccountResult<Unit>) = viewModelScope.launch {
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        when (val result = action()) {
            is AccountResult.Success -> { success(); handleLoad(repository.load(userId), unauthorized, "Cambios guardados") }
            is AccountResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.message)
            is AccountResult.Ambiguous -> _state.value = _state.value.copy(busy = false, error = result.message)
            AccountResult.Unauthorized -> unauthorized()
        }
    }

    private fun handleLoad(result: AccountResult<AccountData>, unauthorized: () -> Unit, message: String? = null) {
        _state.value = when (result) {
            is AccountResult.Success -> AccountUiState(data = result.value, message = message)
            is AccountResult.Failure -> _state.value.copy(loading = false, busy = false, error = result.message)
            is AccountResult.Ambiguous -> _state.value.copy(loading = false, busy = false, error = result.message)
            AccountResult.Unauthorized -> { unauthorized(); AccountUiState() }
        }
    }
}

data class CheckoutUiState(
    val loading: Boolean = false, val busy: Boolean = false, val account: AccountData? = null,
    val cart: ShoppingCart? = null, val selectedAddressId: Long? = null, val selectedPaymentId: Long? = null,
    val confirmation: OrderConfirmation? = null, val error: String? = null, val ambiguous: Boolean = false
)

@HiltViewModel
class CheckoutViewModel @Inject constructor(private val accounts: AccountRepository, private val carts: CartRepository) : ViewModel() {
    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()
    private var userId: Long = 0
    private var idempotencyKey: String? = null

    fun load(id: Long, unauthorized: () -> Unit) { userId = id; viewModelScope.launch {
        _state.value = CheckoutUiState(loading = true)
        val account = async { accounts.load(id) }; val cart = async { carts.load(id) }
        val accountResult = account.await(); val cartResult = cart.await()
        if (accountResult is AccountResult.Unauthorized || cartResult is CartResult.Unauthorized) { unauthorized(); return@launch }
        _state.value = CheckoutUiState(
            account = (accountResult as? AccountResult.Success)?.value,
            cart = (cartResult as? CartResult.Success)?.value,
            selectedAddressId = (accountResult as? AccountResult.Success)?.value?.addresses?.firstOrNull { it.enabled }?.id,
            selectedPaymentId = (accountResult as? AccountResult.Success)?.value?.paymentMethods?.firstOrNull { it.enabled }?.id,
            error = (accountResult as? AccountResult.Failure)?.message ?: (cartResult as? CartResult.Failure)?.message
        )
    } }

    fun selectAddress(id: Long) { _state.value = _state.value.copy(selectedAddressId = id) }
    fun selectPayment(id: Long) { _state.value = _state.value.copy(selectedPaymentId = id) }

    fun checkout(unauthorized: () -> Unit) {
        val address = _state.value.selectedAddressId ?: return
        val payment = _state.value.selectedPaymentId ?: return
        val key = idempotencyKey ?: UUID.randomUUID().toString().also { idempotencyKey = it }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            _state.value = when (val result = accounts.checkout(userId, address, payment, key)) {
                is AccountResult.Success -> { idempotencyKey = null; _state.value.copy(busy = false, confirmation = result.value) }
                is AccountResult.Ambiguous -> _state.value.copy(busy = false, error = result.message, ambiguous = true)
                is AccountResult.Failure -> { idempotencyKey = null; _state.value.copy(busy = false, error = result.message) }
                AccountResult.Unauthorized -> { unauthorized(); CheckoutUiState() }
            }
        }
    }
}
