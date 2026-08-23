package com.tiendatech.mobile.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun storedValues_areParsedIgnoringCase() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStoredValue("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStoredValue("LIGHT"))
    }

    @Test
    fun missingOrUnknownValues_fallBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("unsupported"))
    }
}
