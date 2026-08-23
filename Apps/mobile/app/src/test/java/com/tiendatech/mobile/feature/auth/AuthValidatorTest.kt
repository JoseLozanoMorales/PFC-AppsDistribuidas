package com.tiendatech.mobile.feature.auth

import com.tiendatech.mobile.feature.auth.domain.AuthValidator
import com.tiendatech.mobile.feature.auth.domain.RegistrationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {
    private val valid = RegistrationData("Cliente Prueba", "cliente", "cliente@correo.com", "segura123", "1234567890", "0987654321")

    @Test fun `valid registration is accepted`() = assertNull(AuthValidator.registration(valid, valid.password))

    @Test fun `different passwords are rejected`() =
        assertEquals("Las contraseñas no coinciden", AuthValidator.registration(valid, "otra1234"))

    @Test fun `invalid document is rejected`() =
        assertEquals("La cédula debe tener 10 dígitos", AuthValidator.registration(valid.copy(document = "123"), valid.password))

    @Test fun `otp must contain six digits`() {
        assertEquals("Ingresa el código de 6 dígitos", AuthValidator.otp("12A456"))
        assertNull(AuthValidator.otp("123456"))
    }
}
