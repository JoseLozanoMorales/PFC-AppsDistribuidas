package com.tiendatech.mobile.core.security

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesKeyValueStorage @Inject constructor(
    @ApplicationContext context: Context
) : KeyValueStorage {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    override fun remove(key: String) {
        preferences.edit { remove(key) }
    }

    private companion object {
        const val FILE_NAME = "auth_prefs"
    }
}
