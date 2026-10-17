package com.reprise.app.data.remote

import com.reprise.app.BuildConfig
import com.reprise.app.data.local.TokenStore
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds the single Retrofit client used by the whole app.
 *
 * The base URL comes from `BuildConfig.API_BASE_URL`, which is defined per build
 * type in `app/build.gradle.kts` (emulator -> `http://10.0.2.2:4000/`).
 */
object ApiClient {

    /**
     * @param tokenStore provides the JWT that [AuthInterceptor] attaches.
     */
    fun create(tokenStore: TokenStore): RepRiseApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BASIC logs only the request line and the response status — never
            // headers or bodies — so passwords and JWTs cannot end up in logcat.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(loggingInterceptor)
            // Timeouts keep a dead server from leaving the UI spinning forever.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(httpClient)
            .addConverterFactory(repRiseJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RepRiseApi::class.java)
    }
}
