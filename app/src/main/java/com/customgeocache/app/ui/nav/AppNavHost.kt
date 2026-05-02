package com.customgeocache.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.ui.caches.CacheDetailScreen
import com.customgeocache.app.ui.home.HomeScreen
import com.customgeocache.app.ui.home.HomeTab
import com.customgeocache.app.ui.log.LogEntryScreen
import com.customgeocache.app.ui.settings.SettingsScreen
import com.customgeocache.app.ui.setup.SetupWizardScreen

object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CACHE_DETAIL = "cache/{gccode}"
    const val LOG_ENTRY = "log/{gccode}"

    fun cacheDetail(gccode: String) = "cache/$gccode"
    fun logEntry(gccode: String) = "log/$gccode"
}

@Composable
fun AppNavHost(startDestination: String) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val activeStore = (context.applicationContext as CustomGeoCacheApp).container.activeCacheStore

    NavHost(navController = nav, startDestination = startDestination) {

        composable(Routes.SETUP) {
            SetupWizardScreen(
                onFinish = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }

        navigation(startDestination = Routes.HOME, route = Routes.MAIN) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenCache = { gccode -> nav.navigate(Routes.cacheDetail(gccode)) },
                    onOpenLog = { gccode -> nav.navigate(Routes.logEntry(gccode)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.CACHE_DETAIL) { backStack ->
                val gccode = backStack.arguments?.getString("gccode").orEmpty()
                CacheDetailScreen(
                    gccode = gccode,
                    onBack = { nav.popBackStack() },
                    onNavigateToCompass = {
                        activeStore.requestTab(HomeTab.COMPASS.name)
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    },
                    onLog = { nav.navigate(Routes.logEntry(gccode)) }
                )
            }
            composable(Routes.LOG_ENTRY) { backStack ->
                val gccode = backStack.arguments?.getString("gccode").orEmpty()
                LogEntryScreen(
                    gccode = gccode,
                    onBack = { nav.popBackStack() },
                    onSubmitted = { nav.popBackStack() }
                )
            }
        }
    }
}
