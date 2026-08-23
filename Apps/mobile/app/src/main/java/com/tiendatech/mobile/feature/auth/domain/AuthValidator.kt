package com.tiendatech.mobile.feature.auth.domain

object AuthValidator {
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    private val tenDigits = Regex("^\\d{10}$")
    private val sixDigits = Regex("^\\d{6}$")

    fun login(username: String, password: String): String? = when {
        username.isBlank() -> "Ingresa tu usuario"
        password.isBlank() -> "Ingresa tu contraseña"
        else -> null
    }

    fun registration(data: RegistrationData, repeatedPassword: String): String? = when {
        data.name.isBlank() || data.username.isBlank() -> "Completa tu nombre y usuario"
        !emailPattern.matches(data.email) -> "Ingresa un correo válido"
        data.password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
        data.password != repeatedPassword -> "Las contraseñas no coinciden"
        !tenDigits.matches(data.document) -> "La cédula debe tener 10 dígitos"
        !tenDigits.matches(data.phone) -> "El teléfono debe tener 10 dígitos"
        else -> null
    }

    fun otp(code: String): String? = if (sixDigits.matches(code)) null else "Ingresa el código de 6 dígitos"

    fun email(email: String): String? = if (emailPattern.matches(email.trim())) null else "Ingresa un correo válido"
}
