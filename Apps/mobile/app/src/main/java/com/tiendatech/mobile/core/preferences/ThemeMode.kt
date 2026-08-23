package com.tiendatech.mobile.core.preferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStoredValue(value: String?): ThemeMode = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: SYSTEM
    }
}
