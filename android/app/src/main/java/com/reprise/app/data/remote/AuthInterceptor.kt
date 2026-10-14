package com.reprise.app.data.remote

import com.reprise.app.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the stored JWT to every outgoing request.
 *
 * Doing this in an OkHttp interceptor (rather than in each repository method)
 * means there is exactly one place that knows about the `Authorization` header.
 * If the user is not signed in, the request is sent unchanged, and the server
 * replies 401 for the protected endpoints.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.getToken()

        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request()
                .newBuilder()
                .header(HEADER_NAME, "$HEADER_PREFIX$token")
                .build()
        }

        return chain.proceed(request)
    }

    private companion object {
        const val HEADER_NAME = "Authorization"
        const val HEADER_PREFIX = "Bearer "
    }
}
