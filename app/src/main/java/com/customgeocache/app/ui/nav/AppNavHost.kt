package com.customgeocache.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.customgeocache.app.ui.caches.CacheDetailScreen
import com.customgeocache.app.ui.home.HomeScreen
import com.customgeocache.app.ui.settings.SettingsScreen
import com.customgeocache.app.ui.setup.SetupWizardScreen

object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CACHE_DETAIL = "cache/{gccode}"
    fun cacheDetail(gccode: String) = "cache/$gccode"
}

@Composable
fun AppNavHost(startDestination: String) {
    val nav = rememberNavController()
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
                    onOpenCache = { gccode -> nav.navigate(Routes.cacheDetail(gccode)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.CACHE_DETAIL) { backStack ->
                val gccode = backStack.arguments?.getString("gccode").orEmpty()
                CacheDetailScreen(gccode = gccode, onBack = { nav.popBackStack() })
            }
        }
    }
}
