package com.customgeocache.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.R
import com.customgeocache.app.ui.caches.CacheListScreen
import com.customgeocache.app.ui.compass.CompassScreen
import com.customgeocache.app.ui.map.MapScreen

enum class HomeTab { MAP, CACHES, COMPASS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenCache: (String) -> Unit,
    onOpenLog: (String) -> Unit
) {
    val context = LocalContext.current
    val activeStore = (context.applicationContext as CustomGeoCacheApp).container.activeCacheStore

    var tab by rememberSaveable { mutableStateOf(HomeTab.MAP) }
    val requestedTab by activeStore.requestedTab.collectAsStateWithLifecycle(initialValue = null)
    val activeCache by activeStore.active.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(requestedTab) {
        requestedTab?.let { name ->
            runCatching { HomeTab.valueOf(name) }.getOrNull()?.let { tab = it }
            activeStore.consumeRequestedTab()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (tab) {
                            HomeTab.MAP -> stringResource(R.string.nav_map)
                            HomeTab.CACHES -> stringResource(R.string.nav_caches)
                            HomeTab.COMPASS -> "Kompas"
                        }
                    )
                },
                actions = {
                    // Když uživatel na Compass tabu má aktivní keš, ukážeme zkratky
                    // na Detail + Log přímo v top app bar (Compass screen sama by jinak
                    // toolbar neměla — používá HomeScreen scaffold).
                    if (tab == HomeTab.COMPASS && activeCache != null) {
                        IconButton(onClick = { onOpenCache(activeCache!!.gccode) }) {
                            Icon(Icons.Default.Info, contentDescription = "Detail")
                        }
                        IconButton(onClick = { onOpenLog(activeCache!!.gccode) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Logovat")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HomeTab.MAP,
                    onClick = { tab = HomeTab.MAP },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_map)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.CACHES,
                    onClick = { tab = HomeTab.CACHES },
                    icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_caches)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.COMPASS,
                    onClick = { tab = HomeTab.COMPASS },
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    label = { Text("Kompas") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            HomeTab.MAP -> MapScreen(
                contentPadding = padding,
                onOpenCache = onOpenCache,
                onNavigateCompass = { tab = HomeTab.COMPASS },
                onLogCache = onOpenLog
            )
            HomeTab.CACHES -> CacheListScreen(
                contentPadding = padding,
                onOpenCache = onOpenCache
            )
            HomeTab.COMPASS -> CompassScreen(contentPadding = padding)
        }
    }
}
