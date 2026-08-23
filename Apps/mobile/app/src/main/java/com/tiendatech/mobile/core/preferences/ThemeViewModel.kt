package com.tiendatech.mobile.core.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(private val repository: UserPreferencesRepository) : ViewModel() {
    val mode: StateFlow<ThemeMode> = repository.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    fun setMode(mode: ThemeMode) { viewModelScope.launch { repository.setThemeMode(mode) } }
}
