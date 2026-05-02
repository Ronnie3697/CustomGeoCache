package com.customgeocache.app.ui.map

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.log.Logger
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val TAG = "CGC.Map"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val context = LocalContext.current
    val app = context.applicationContext as CustomGeoCacheApp
    val prefs = app.container.preferences
    val scope = rememberCoroutineScope()

    val apiKey by prefs.mapyApiKey.collectAsStateWithLifecycle(initialValue = null)
    val layerId by prefs.mapLayer.collectAsStateWithLifecycle(initialValue = "basic")
    val layer = remember(layerId) { MapyLayer.fromId(layerId) }

    var showLayerSheet by remember { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    // Diagnostic events shown in the status overlay (top of screen)
    val statusLog = remember { mutableStateListOf<String>() }
    fun logStatus(msg: String) {
        Log.i(TAG, msg)
        if (statusLog.size > 8) statusLog.removeAt(0)
        statusLog.add(msg)
    }

    LaunchedEffect(apiKey) {
        logStatus(
            if (apiKey.isNullOrBlank()) "API key: MISSING"
            else "API key: ${apiKey!!.take(4)}…${apiKey!!.takeLast(4)} (${apiKey!!.length} chars)"
        )
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
                onStatus = ::logStatus
            )
        }

        // Diagnostic status overlay (top-left)
        StatusOverlay(
            messages = statusLog,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
        )

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

        if (showLayerSheet) {
            LayerPickerCard(
                current = layer,
                onPick = { picked ->
                    showLayerSheet = false
                    scope.launch { prefs.setMapLayer(picked.id) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun StatusOverlay(messages: List<String>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) return
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC000000)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            messages.forEach { msg ->
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
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
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        Logger.setVerbosity(Logger.VERBOSE)
        onStatus("MapView init")
        MapView(context).apply { onCreate(Bundle()) }
    }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        mapView.addOnDidFailLoadingMapListener { reason ->
            onStatus("Map load FAIL: $reason")
        }
        mapView.addOnDidFinishLoadingStyleListener {
            onStatus("Style loaded ✓")
        }
        mapView.addOnSourceChangedListener { sourceId ->
            onStatus("Source changed: $sourceId")
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
                onStatus("Map ready, applying style…")
                applyStyleAndCamera(map, layer, apiKey, onStatus)
            }
        }
    )
}

private fun applyStyleAndCamera(
    map: MapLibreMap,
    layer: MapyLayer,
    apiKey: String,
    onStatus: (String) -> Unit
) {
    val styleJson = MapyStyles.rasterStyleJson(layer, apiKey)
    onStatus("Style JSON ${styleJson.length} bytes, layer=${layer.id}")
    map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
        onStatus("setStyle CB: sources=${style.sources.size} layers=${style.layers.size}")
    }
    if (map.cameraPosition.zoom < 1.0) {
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(49.7437, 15.3386))
            .zoom(7.0)
            .build()
    }
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
