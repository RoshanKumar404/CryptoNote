package com.example.cryptnote.di

import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.api.SyncApi
import com.example.cryptnote.data.remote.interceptor.AuthInterceptor
import com.example.cryptnote.data.remote.interceptor.GzipRequestInterceptor
import com.example.cryptnote.data.remote.interceptor.TokenAuthenticator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Paste your cloud backend URL here once deployed.
     * Examples:
     * - Fly.io: "https://cryptonote-backend.fly.dev"
     * - Render: "https://cryptonote-backend.onrender.com"
     *
     * When blank, it automatically defaults to emulator loopback or adb reverse port forwarding for local testing.
     */
    const val BACKEND_BASE_URL: String = "https://cryptonote-backend.fly.dev"

    const val RENDER_BASE_URL: String = BACKEND_BASE_URL

    fun getBaseUrl(): String {
        val targetUrl = BACKEND_BASE_URL.ifBlank { RENDER_BASE_URL }
        if (targetUrl.isNotBlank()) {
            return if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/"
        }

        // Only fallback to local loopback in Debug builds
        if (com.example.cryptnote.BuildConfig.DEBUG) {
            val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic")
                    || android.os.Build.FINGERPRINT.startsWith("unknown")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == android.os.Build.PRODUCT

            return if (isEmulator) {
                "http://10.0.2.2:3000/"
            } else {
                "http://127.0.0.1:3000/"
            }
        }

        return "https://cryptonote-backend.fly.dev/"
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Never log sensitive bodies, tokens, or passwords in release builds
            level = if (com.example.cryptnote.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            // Configured for production endpoints (bypassed on local emulator / loopback)
            .add("api.cryptonote.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()
    }

    /**
     * Unauthenticated OkHttpClient used exclusively for auth endpoints (/auth/login, /auth/register, /auth/refresh)
     * to prevent recursive authentication loops.
     */
    @Provides
    @Singleton
    @Named("AuthOkHttpClient")
    fun provideAuthOkHttpClient(
        logging: HttpLoggingInterceptor,
        certificatePinner: CertificatePinner
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .certificatePinner(certificatePinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Authenticated OkHttpClient with JWT Bearer injection, GZIP compression, and 401 token refresh.
     */
    @Provides
    @Singleton
    fun provideAuthenticatedOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        gzipInterceptor: GzipRequestInterceptor,
        logging: HttpLoggingInterceptor,
        certificatePinner: CertificatePinner
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(gzipInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .certificatePinner(certificatePinner)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("AuthRetrofit")
    fun provideAuthRetrofit(
        @Named("AuthOkHttpClient") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthenticatedRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(
        @Named("AuthRetrofit") retrofit: Retrofit
    ): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSyncApi(
        retrofit: Retrofit
    ): SyncApi {
        return retrofit.create(SyncApi::class.java)
    }

    @Provides
    @Singleton
    fun provideKeysApi(
        retrofit: Retrofit
    ): com.example.cryptnote.data.remote.api.KeysApi {
        return retrofit.create(com.example.cryptnote.data.remote.api.KeysApi::class.java)
    }
}
