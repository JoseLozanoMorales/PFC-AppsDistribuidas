package com.tiendatech.mobile.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureSessionStoreTest {

    @Test
    fun saveToken_storesOnlyCipherTextAndCanRestoreToken() {
        val storage = InMemoryStorage()
        val store = SecureSessionStore(storage, PrefixCipher())

        store.saveToken("jwt-sensitive-value")

        assertFalse(storage.values.values.single().contains("jwt-sensitive-value"))
        assertEquals("jwt-sensitive-value", store.getToken())
    }

    @Test
    fun clear_removesStoredCredential() {
        val storage = InMemoryStorage()
        val store = SecureSessionStore(storage, PrefixCipher())
        store.saveToken("token")

        store.clear()

        assertNull(store.getToken())
    }

    @Test
    fun corruptCredential_isDeletedAndReturnsNull() {
        val storage = InMemoryStorage(mutableMapOf("encrypted_access_token" to "corrupt"))
        val store = SecureSessionStore(storage, PrefixCipher())

        assertNull(store.getToken())
        assertEquals(emptyMap<String, String>(), storage.values)
    }

    @Test
    fun blankToken_isRejected() {
        val store = SecureSessionStore(InMemoryStorage(), PrefixCipher())

        assertThrows(IllegalArgumentException::class.java) { store.saveToken("  ") }
    }

    private class InMemoryStorage(
        val values: MutableMap<String, String> = mutableMapOf()
    ) : KeyValueStorage {
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private class PrefixCipher : TokenCipher {
        override fun encrypt(plainText: String): String = "cipher:${plainText.reversed()}"
        override fun decrypt(cipherText: String): String {
            require(cipherText.startsWith("cipher:"))
            return cipherText.removePrefix("cipher:").reversed()
        }
    }
}
