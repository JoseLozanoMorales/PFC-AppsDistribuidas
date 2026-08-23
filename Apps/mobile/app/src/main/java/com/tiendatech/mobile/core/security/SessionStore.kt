package com.tiendatech.mobile.core.security

interface SessionStore : SessionTokenProvider {
    fun saveToken(token: String)
    fun clear()
}
