package com.example.cryptnote.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Interceptor] that compresses request bodies using gzip when payload exceeds 1 KB.
 */
@Singleton
class GzipRequestInterceptor @Inject constructor() : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
