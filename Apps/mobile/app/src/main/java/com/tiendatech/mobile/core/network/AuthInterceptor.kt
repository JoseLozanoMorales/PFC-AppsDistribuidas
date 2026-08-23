package com.tiendatech.mobile.core.network

import com.tiendatech.mobile.core.security.SessionTokenProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenProvider: SessionTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .apply {
                tokenProvider.getToken()
                    ?.takeIf(String::isNotBlank)
                    ?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        return chain.proceed(request)
    }
}
