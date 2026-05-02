package com.customgeocache.app.ui.caches

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.R
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.ui.compass.LocationFlow
import com.customgeocache.app.util.GeoUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CacheListScreen(
    contentPadding: PaddingValues,
    onOpenCache: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CustomGeoCacheApp
    val activeStore = app.container.activeCacheStore
    val caches by app.container.cacheRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Reference bod pro výpočet vzdálenosti — preferujeme aktuální GPS, fallback na poslední pozici mapy.
    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val location by produceState<Location?>(initialValue = null, locationPermission.status.isGranted) {
        if (!locationPermission.status.isGranted) {
            value = null
            return@produceState
        }
        LocationFlow.observe(context).collect { value = it }
    }

    val refLat: Double?
    val refLon: Double?
    if (location != null) {
        refLat = location!!.latitude
        refLon = location!!.longitude
    } else {
        refLat = activeStore.mapCamera?.lat
        refLon = activeStore.mapCamera?.lon
    }

    val sorted = remember(caches, refLat, refLon) {
        if (refLat == null || refLon == null) caches
        else caches.sortedBy { GeoUtils.distanceMeters(refLat, refLon, it.lat, it.lon) }
    }

    if (sorted.isEmpty()) {
        EmptyState(contentPadding)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sorted, key = { it.gccode }) { cache ->
                val distance = if (refLat != null && refLon != null) {
                    GeoUtils.distanceMeters(refLat, refLon, cache.lat, cache.lon)
                } else null
                CacheCard(
                    cache = cache,
                    distanceText = distance?.let(GeoUtils::formatDistance),
                    onClick = { onOpenCache(cache.gccode) }
                )
            }
        }
    }
}

@Composable
private fun CacheCard(
    cache: CacheEntity,
    distanceText: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cache.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Text(
                        text = cache.gccode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (distanceText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetaText("D ${cache.difficulty}")
                MetaText("T ${cache.terrain}")
                MetaText(cache.size)
                MetaText(cache.type)
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyState(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.caches_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
