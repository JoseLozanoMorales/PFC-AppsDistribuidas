package com.tiendatech.mobile.core.security

import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSessionStore @Inject constructor(
    private val storage: KeyValueStorage,
    private val cipher: TokenCipher
) : SessionStore {

    override fun getToken(): String? {
        val encrypted = storage.read(TOKEN_KEY) ?: return null
        return try {
            cipher.decrypt(encrypted).takeIf(String::isNotBlank)
        } catch (_: GeneralSecurityException) {
            storage.remove(TOKEN_KEY)
            null
        } catch (_: IllegalArgumentException) {
            storage.remove(TOKEN_KEY)
            null
        }
    }

    override fun saveToken(token: String) {
        require(token.isNotBlank()) { "El token de sesión no puede estar vacío" }
        storage.write(TOKEN_KEY, cipher.encrypt(token))
    }

    override fun clear() {
        storage.remove(TOKEN_KEY)
    }

    private companion object {
        const val TOKEN_KEY = "encrypted_access_token"
    }
}
