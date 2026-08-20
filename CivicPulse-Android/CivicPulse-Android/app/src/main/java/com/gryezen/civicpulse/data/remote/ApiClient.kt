package com.gryezen.civicpulse.data.remote

import android.content.Context
import com.gryezen.civicpulse.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit needs a base URL at construction time, but ours is user-editable
 * (Account > Server settings) because the Render / Supabase backend URL is
 * still being finalized on another branch. Rather than rebuild Retrofit on
 * every change, a single interceptor rewrites the scheme/host/port of every
 * outgoing request to whatever [currentBaseUrl] currently points to.
 */
class ApiClient(context: Context) {

    private val cookieJar = PersistentCookieJar(context)

    // Starts pointed at the compile-time default so requests made before the
    // DataStore-backed preference (Account > Server settings) finishes its
    // first emission still hit a real server, not the placeholder host.
    @Volatile
    private var currentBaseUrl = BuildConfig.DEFAULT_BASE_URL.toHttpUrl()

    fun setBaseUrl(url: String) {
        currentBaseUrl = url.toHttpUrl()
    }

    fun clearSession() = cookieJar.clear()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val rewriteHostInterceptor = Interceptor { chain ->
        val original = chain.request()
        val target = currentBaseUrl
        val newUrl = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        chain.proceed(original.newBuilder().url(newUrl).build())
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(rewriteHostInterceptor)
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()
        .create(ApiService::class.java)

    companion object {
        // Never actually dialed — request host/scheme get rewritten by the
        // interceptor above before the call leaves the device.
        private val PLACEHOLDER_BASE_URL = "https://civicpulse.invalid/".toHttpUrl()
    }
}
