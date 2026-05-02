package com.customgeocache.app.ui.compass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.util.GeoUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val context = LocalContext.current
    val app = context.applicationContext as CustomGeoCacheApp

    val activeCache by app.container.activeCacheStore.active.collectAsState()
    val permission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    if (activeCache == null) {
        EmptyState(contentPadding)
        return
    }

    if (!permission.status.isGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Pro kompas potřebujeme přístup k poloze.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(12.dp))
            androidx.compose.material3.Button(onClick = { permission.launchPermissionRequest() }) {
                Text("Povolit polohu")
            }
        }
        return
    }

    val target = activeCache!!
    val azimuth by rememberDeviceAzimuth()

    val location by produceState<android.location.Location?>(initialValue = null, target.gccode) {
        LocationFlow.observe(context).collect { value = it }
    }

    val bearing = remember(location, target) {
        location?.let {
            GeoUtils.bearingDegrees(it.latitude, it.longitude, target.lat, target.lon).toFloat()
        } ?: 0f
    }
    val distanceM = remember(location, target) {
        location?.let { GeoUtils.distanceMeters(it.latitude, it.longitude, target.lat, target.lon) }
    }

    // Šipka ukazuje k cíli relativně k orientaci telefonu = bearing - azimuth
    val arrowAngle by animateFloatAsState(
        targetValue = (bearing - azimuth + 360f) % 360f,
        animationSpec = tween(300),
        label = "arrowAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header s GC kódem a názvem
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(target.gccode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(target.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${target.type}  •  D ${target.difficulty}  •  T ${target.terrain}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.size(32.dp))

        // Kompas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Pozadí — N/S/E/W
            CompassDial(azimuth = azimuth, modifier = Modifier.fillMaxSize())
            // Šipka k cíli
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(arrowAngle),
                contentAlignment = Alignment.Center
            ) {
                ArrowToTarget()
            }
        }

        Spacer(Modifier.size(24.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Vzdálenost", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = distanceM?.let { GeoUtils.formatDistance(it) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Směr", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (location != null) "${bearing.toInt()}°" else "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                "Žádná aktivní keš.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Klepni na keš na mapě a stiskni „Naviguj“.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompassDial(azimuth: Float, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier.rotate(-azimuth),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 - 8.dp.toPx()
            // Vnější kruh
            drawCircle(
                color = outline,
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
            // tick marks each 30°
            for (deg in 0 until 360 step 30) {
                val rad = Math.toRadians(deg.toDouble() - 90)
                val x1 = (center.x + radius * Math.cos(rad)).toFloat()
                val y1 = (center.y + radius * Math.sin(rad)).toFloat()
                val x2 = (center.x + (radius - 16.dp.toPx()) * Math.cos(rad)).toFloat()
                val y2 = (center.y + (radius - 16.dp.toPx()) * Math.sin(rad)).toFloat()
                drawLine(
                    color = outline,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        // N / E / S / W písmenka
        Box(modifier = Modifier.fillMaxSize()) {
            Text("N", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("S", color = onSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium)
            Text("E", color = onSurface,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                style = MaterialTheme.typography.titleMedium)
            Text("W", color = onSurface,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ArrowToTarget() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = center.x
        val cy = center.y
        val length = size.minDimension * 0.30f
        val width = size.minDimension * 0.10f
        val path = Path().apply {
            moveTo(cx, cy - length)
            lineTo(cx - width, cy + length * 0.4f)
            lineTo(cx, cy + length * 0.15f)
            lineTo(cx + width, cy + length * 0.4f)
            close()
        }
        drawPath(path = path, color = color)
        drawCircle(color = Color(0xFF555555), radius = 6f, center = Offset(cx, cy))
    }
}
