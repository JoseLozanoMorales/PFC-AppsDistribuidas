package com.tiendatech.mobile.core.security

interface KeyValueStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}
