package com.customgeocache.app.ui.caches

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customgeocache.app.CustomGeoCacheApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheDetailScreen(gccode: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CustomGeoCacheApp
    val cache by app.container.cacheRepository.observeByGcCode(gccode)
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cache?.name ?: gccode) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val c = cache
            if (c == null) {
                Text(text = "Tato keš ($gccode) ještě není stažená. Detail z geocaching.com je TODO v iter 2.",
                    style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row {
                MetaBlock(label = "D", value = c.difficulty.toString())
                Spacer(Modifier.size(16.dp))
                MetaBlock(label = "T", value = c.terrain.toString())
                Spacer(Modifier.size(16.dp))
                MetaBlock(label = "Velikost", value = c.size)
                Spacer(Modifier.size(16.dp))
                MetaBlock(label = "Typ", value = c.type)
            }
            Spacer(Modifier.size(16.dp))
            if (!c.description.isNullOrBlank()) {
                Text("Popis", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(c.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(16.dp))
            }
            if (!c.hint.isNullOrBlank()) {
                Text("Hint", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(c.hint, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MetaBlock(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
