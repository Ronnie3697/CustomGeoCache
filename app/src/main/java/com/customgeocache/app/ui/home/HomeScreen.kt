package com.customgeocache.app.ui.home

import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.customgeocache.app.R
import com.customgeocache.app.ui.caches.CacheListScreen
import com.customgeocache.app.ui.map.MapScreen

private enum class HomeTab { MAP, CACHES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenCache: (String) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.MAP) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (tab) {
                            HomeTab.MAP -> stringResource(R.string.nav_map)
                            HomeTab.CACHES -> stringResource(R.string.nav_caches)
                        }
                    )
                },
                actions = {
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
            }
        }
    ) { padding ->
        when (tab) {
            HomeTab.MAP -> MapScreen(contentPadding = padding)
            HomeTab.CACHES -> CacheListScreen(
                contentPadding = padding,
                onOpenCache = onOpenCache
            )
        }
    }
}
