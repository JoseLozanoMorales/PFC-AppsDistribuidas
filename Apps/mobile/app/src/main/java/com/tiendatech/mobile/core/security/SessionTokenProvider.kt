package com.tiendatech.mobile.core.security

interface SessionTokenProvider {
    fun getToken(): String?
}
