package com.tiendatech.mobile.feature.auth.domain

data class AuthUser(
    val id: Int,
    val username: String,
    val name: String,
    val email: String,
    val roleId: Int
)

data class RegistrationData(
    val name: String,
    val username: String,
    val email: String,
    val password: String,
    val document: String,
    val phone: String
)

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val message: String) : AuthResult<Nothing>
}
