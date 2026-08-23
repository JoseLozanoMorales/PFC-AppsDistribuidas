package com.tiendatech.mobile.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.auth.data.AuthRepository
import com.tiendatech.mobile.feature.auth.domain.AuthResult
import com.tiendatech.mobile.feature.auth.domain.AuthUser
import com.tiendatech.mobile.feature.auth.domain.AuthValidator
import com.tiendatech.mobile.feature.auth.domain.RegistrationData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthActionState(
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

enum class RegistrationStage { FORM, OTP, DONE }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuthActionState())
    val state: StateFlow<AuthActionState> = _state.asStateFlow()

    private val _registrationStage = MutableStateFlow(RegistrationStage.FORM)
    val registrationStage: StateFlow<RegistrationStage> = _registrationStage.asStateFlow()
    private var registrationData: RegistrationData? = null
    private var transactionId: String? = null

    fun login(username: String, password: String, onSuccess: (AuthUser) -> Unit) {
        AuthValidator.login(username, password)?.let { _state.value = AuthActionState(error = it); return }
        viewModelScope.launch {
            _state.value = AuthActionState(loading = true)
            when (val result = repository.login(username, password)) {
                is AuthResult.Success -> {
                    _state.value = AuthActionState()
                    onSuccess(result.value)
                }
                is AuthResult.Failure -> _state.value = AuthActionState(error = result.message)
            }
        }
    }

    fun requestOtp(data: RegistrationData, repeatedPassword: String) {
        AuthValidator.registration(data, repeatedPassword)?.let {
            _state.value = AuthActionState(error = it)
            return
        }
        viewModelScope.launch {
            _state.value = AuthActionState(loading = true)
            when (val result = repository.sendOtp(data.email, transactionId)) {
                is AuthResult.Success -> {
                    registrationData = data
                    transactionId = result.value
                    _registrationStage.value = RegistrationStage.OTP
                    _state.value = AuthActionState(message = "Código enviado a ${data.email}")
                }
                is AuthResult.Failure -> _state.value = AuthActionState(error = result.message)
            }
        }
    }

    fun verifyAndRegister(code: String) {
        AuthValidator.otp(code)?.let { _state.value = AuthActionState(error = it); return }
        val data = registrationData ?: return
        val txId = transactionId ?: return
        viewModelScope.launch {
            _state.value = AuthActionState(loading = true)
            when (val result = repository.verifyAndRegister(data, code, txId)) {
                is AuthResult.Success -> {
                    _registrationStage.value = RegistrationStage.DONE
                    _state.value = AuthActionState(message = "Cuenta creada correctamente")
                }
                is AuthResult.Failure -> _state.value = AuthActionState(error = result.message)
            }
        }
    }

    fun editRegistration() {
        _registrationStage.value = RegistrationStage.FORM
        _state.value = AuthActionState()
    }

    fun recover(email: String) {
        AuthValidator.email(email)?.let { _state.value = AuthActionState(error = it); return }
        viewModelScope.launch {
            _state.value = AuthActionState(loading = true)
            _state.value = when (val result = repository.recoverPassword(email)) {
                is AuthResult.Success -> AuthActionState(message = "Si el correo está registrado, recibirás instrucciones en unos minutos")
                is AuthResult.Failure -> AuthActionState(error = result.message)
            }
        }
    }

}
