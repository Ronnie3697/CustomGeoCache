package com.customgeocache.app

import android.app.Application
import com.customgeocache.app.data.AppContainer

class CustomGeoCacheApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        @Volatile
        private var instance: CustomGeoCacheApp? = null
        fun get(): CustomGeoCacheApp = checkNotNull(instance) { "App not initialized yet" }
    }
}
