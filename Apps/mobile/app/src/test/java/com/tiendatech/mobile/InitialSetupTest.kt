package com.tiendatech.mobile

import com.tiendatech.mobile.core.designsystem.theme.Primary
import com.tiendatech.mobile.core.navigation.TiendaTechDestinations
import org.junit.Test
import org.junit.Assert.*
import androidx.compose.ui.graphics.Color

/**
 * Pruebas unitarias para validar la configuración inicial de la Fase 0.
 */
class InitialSetupTest {
    @Test
    fun brandColors_areCorrectlyDefined() {
        // Verifica que el color primario sea el definido en el manual de identidad (#5C65EE)
        assertEquals(Color(0xFF5C65EE), Primary)
    }

    @Test
    fun initialNavigationRoute_isHome() {
        // Verifica que la ruta inicial definida en la navegación sea "home"
        assertEquals("home", TiendaTechDestinations.HOME_ROUTE)
    }
}
