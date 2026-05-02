package com.customgeocache.app.ui.map

import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.R
import com.customgeocache.app.data.api.GcSearchApi
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.state.MapCameraState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.log.Logger
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val TAG = "CGC.Map"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenCache: (String) -> Unit = {},
    onNavigateCompass: () -> Unit = {},
    onLogCache: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as CustomGeoCacheApp
    val prefs = app.container.preferences
    val repo = app.container.cacheRepository
    val activeStore = app.container.activeCacheStore
    val scope = rememberCoroutineScope()

    val apiKey by prefs.mapyApiKey.collectAsStateWithLifecycle(initialValue = null)
    val layerId by prefs.mapLayer.collectAsStateWithLifecycle(initialValue = "basic")
    val layer = remember(layerId) { MapyLayer.fromId(layerId) }
    val caches by repo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var showLayerSheet by remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var searching by remember { mutableStateOf(false) }
    var selectedCache by remember { mutableStateOf<CacheEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(caches, styleRef) {
        styleRef?.let {
            MapMarkers.update(it, caches)
            Log.i(TAG, "Markers updated: ${caches.size} caches")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Při odchodu z Map tabu uložíme aktuální polohu kamery
            mapRef?.cameraPosition?.let { cp ->
                activeStore.saveMapCamera(
                    MapCameraState(
                        lat = cp.target?.latitude ?: 49.7437,
                        lon = cp.target?.longitude ?: 15.3386,
                        zoom = cp.zoom,
                        bearing = cp.bearing,
                        tilt = cp.tilt
                    )
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (apiKey.isNullOrBlank()) {
            MissingApiKey()
        } else {
            MapLibreView(
                apiKey = apiKey!!,
                layer = layer,
                savedCamera = activeStore.mapCamera,
                onMapReady = { map, style ->
                    mapRef = map
                    styleRef = style
                    MapMarkers.ensureLayers(style)
                    MapMarkers.update(style, caches)
                    Log.i(TAG, "Map ready, markers: ${caches.size}")
                    map.addOnMapClickListener { latLng ->
                        val pixel = map.projection.toScreenLocation(latLng)
                        val features = map.queryRenderedFeatures(
                            pixel, MapMarkers.LAYER_CIRCLE, MapMarkers.LAYER_LABEL
                        )
                        val gc = features.firstNotNullOfOrNull {
                            it.getStringProperty("gccode")
                        }
                        if (gc != null) {
                            selectedCache = caches.firstOrNull { it.gccode == gc }
                        } else {
                            selectedCache = null
                        }
                        true
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = { showLayerSheet = !showLayerSheet },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.map_layers))
            }
            Spacer(Modifier.size(12.dp))
            FloatingActionButton(
                onClick = {
                    if (!locationPermission.status.isGranted) {
                        locationPermission.launchPermissionRequest()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.map_my_location))
            }
        }

        // Search-here FAB
        ExtendedFloatingActionButton(
            onClick = {
                val map = mapRef ?: return@ExtendedFloatingActionButton
                val bounds = map.projection.visibleRegion.latLngBounds
                searching = true
                scope.launch {
                    val r = repo.searchInBounds(
                        south = bounds.latitudeSouth,
                        west = bounds.longitudeWest,
                        north = bounds.latitudeNorth,
                        east = bounds.longitudeEast
                    )
                    searching = false
                    when (r) {
                        is GcSearchApi.Result.Success -> {
                            snackbar.showSnackbar("Nahráno ${r.caches.size} kešek (z ${r.total})")
                            // Pokud našlo keše, fitneme kameru na jejich bounding box,
                            // ať uživatel hned vidí, kde jsou.
                            if (r.caches.isNotEmpty() && r.caches.size <= 200) {
                                fitBoundsToCaches(map, r.caches)
                            }
                        }
                        is GcSearchApi.Result.NotAuthenticated ->
                            snackbar.showSnackbar("Nejsi přihlášený na geocaching.com.")
                        is GcSearchApi.Result.Error ->
                            snackbar.showSnackbar("Chyba: ${r.message}")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.map_search_here))
        }

        if (showLayerSheet) {
            LayerPickerCard(
                current = layer,
                onPick = { picked ->
                    showLayerSheet = false
                    scope.launch { prefs.setMapLayer(picked.id) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
            )
        }

        selectedCache?.let { cache ->
            CachePreviewCard(
                cache = cache,
                onDismiss = { selectedCache = null },
                onOpenDetail = {
                    selectedCache = null
                    onOpenCache(cache.gccode)
                },
                onNavigate = {
                    activeStore.setActive(cache)
                    selectedCache = null
                    onNavigateCompass()
                },
                onLog = {
                    activeStore.setActive(cache)
                    selectedCache = null
                    onLogCache(cache.gccode)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { data -> Snackbar(snackbarData = data) }
    }
}

private fun fitBoundsToCaches(map: MapLibreMap, caches: List<CacheEntity>) {
    if (caches.isEmpty()) return
    if (caches.size == 1) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(caches[0].lat, caches[0].lon), 14.0
        ))
        return
    }
    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
    for (c in caches) {
        if (c.lat < minLat) minLat = c.lat
        if (c.lat > maxLat) maxLat = c.lat
        if (c.lon < minLon) minLon = c.lon
        if (c.lon > maxLon) maxLon = c.lon
    }
    val bounds = LatLngBounds.Builder()
        .include(LatLng(minLat, minLon))
        .include(LatLng(maxLat, maxLon))
        .build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
}

@Composable
private fun MissingApiKey() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.map_no_api_key),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun MapLibreView(
    apiKey: String,
    layer: MapyLayer,
    savedCamera: MapCameraState?,
    onMapReady: (MapLibreMap, Style) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        Logger.setVerbosity(Logger.VERBOSE)
        MapView(context).apply { onCreate(Bundle()) }
    }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        mapView.addOnDidFailLoadingMapListener { reason ->
            Log.w(TAG, "Map load FAIL: $reason")
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                val styleJson = MapyStyles.rasterStyleJson(layer, apiKey)
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    onMapReady(map, style)
                }
                // Obnov kameru, pokud máme uloženou pozici (návrat z jiného tabu).
                // Jinak zoom 7 / střed ČR.
                val target = if (savedCamera != null) {
                    CameraPosition.Builder()
                        .target(LatLng(savedCamera.lat, savedCamera.lon))
                        .zoom(savedCamera.zoom)
                        .bearing(savedCamera.bearing)
                        .tilt(savedCamera.tilt)
                        .build()
                } else {
                    CameraPosition.Builder()
                        .target(LatLng(49.7437, 15.3386))
                        .zoom(7.0)
                        .build()
                }
                map.cameraPosition = target
            }
        }
    )
}

@Composable
private fun LayerPickerCard(
    current: MapyLayer,
    onPick: (MapyLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapyLayer.entries.forEach { l ->
                FilterChip(
                    selected = l == current,
                    onClick = { onPick(l) },
                    label = { Text(l.displayName) }
                )
            }
        }
    }
}

@Composable
private fun CachePreviewCard(
    cache: CacheEntity,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onNavigate: () -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cache.gccode, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(cache.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(cache.type) })
                AssistChip(onClick = {}, label = { Text("D ${cache.difficulty}") })
                AssistChip(onClick = {}, label = { Text("T ${cache.terrain}") })
                AssistChip(onClick = {}, label = { Text(cache.size) })
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenDetail, modifier = Modifier.weight(1f)) {
                    Text("Detail")
                }
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Naviguj")
                }
                OutlinedButton(onClick = onLog, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Log")
                }
            }
        }
    }
}
