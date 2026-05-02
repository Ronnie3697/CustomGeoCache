package com.customgeocache.app.data.network

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("User-Agent", "CustomGeoCache/0.1 (Android)")
            .header("Accept-Language", "cs-CZ,cs;q=0.9,en;q=0.8")
            .build()
        return chain.proceed(req)
    }
}
