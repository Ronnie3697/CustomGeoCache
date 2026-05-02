package com.customgeocache.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.customgeocache.app.ui.nav.AppNavHost
import com.customgeocache.app.ui.nav.Routes
import com.customgeocache.app.ui.theme.CustomGeoCacheTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = (application as CustomGeoCacheApp).container.preferences
        // Jednorázový blokující snapshot — vyhne se UI flickeru mezi setup ↔ main.
        val startDestination = if (prefs.firstRunDoneBlocking()) Routes.home() else Routes.SETUP

        setContent {
            CustomGeoCacheTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(startDestination = startDestination)
                }
            }
        }
    }
}
