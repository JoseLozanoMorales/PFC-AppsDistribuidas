package com.tiendatech.mobile.core.security

interface TokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}
