package com.tiendatech.mobile.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiendatech.mobile.feature.auth.data.AuthRepository
import com.tiendatech.mobile.feature.auth.domain.AuthResult
import com.tiendatech.mobile.feature.auth.domain.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionUiState {
    data object Loading : SessionUiState
    data object Guest : SessionUiState
    data class Authenticated(val user: AuthUser) : SessionUiState
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init { restore() }

    fun authenticated(user: AuthUser) { _state.value = SessionUiState.Authenticated(user) }

    fun logout() {
        repository.logout()
        _state.value = SessionUiState.Guest
    }

    private fun restore() = viewModelScope.launch {
        _state.value = when (val result = repository.restoreSession()) {
            is AuthResult.Success -> result.value?.let(SessionUiState::Authenticated) ?: SessionUiState.Guest
            is AuthResult.Failure -> SessionUiState.Guest
        }
    }
}
