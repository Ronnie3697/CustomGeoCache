package com.customgeocache.app.ui.map

import com.customgeocache.app.data.db.entities.CacheEntity
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Helper na markery kešek na MapLibre mapě. Používáme GeoJSON source + Circle layer
 * (jednoduché barevné kruhy s gccode jako label nad nimi).
 *
 * IDs jsou stable, ať můžeme update znovu volat bez recreate.
 */
object MapMarkers {

    const val SOURCE_ID = "caches-src"
    const val LAYER_CIRCLE = "caches-circle"
    const val LAYER_LABEL = "caches-label"

    fun ensureLayers(style: Style) {
        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[]}"))
        }
        if (style.getLayer(LAYER_CIRCLE) == null) {
            val circle = CircleLayer(LAYER_CIRCLE, SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(13f),
                PropertyFactory.circleColor(
                    Expression.match(
                        Expression.get("typeShort"),
                        Expression.literal("#6E6E6E"),
                        Expression.stop("T", "#2E7D32"),
                        Expression.stop("M", "#FF8F00"),
                        Expression.stop("?", "#5E35B1"),
                        Expression.stop("E", "#5D4037"),
                        Expression.stop("Ev", "#D32F2F"),
                        Expression.stop("V", "#6D4C41"),
                        Expression.stop("W", "#1976D2")
                    )
                ),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleOpacity(0.95f)
            )
            style.addLayer(circle)
        }
        if (style.getLayer(LAYER_LABEL) == null) {
            val label = SymbolLayer(LAYER_LABEL, SOURCE_ID).withProperties(
                PropertyFactory.textField(Expression.get("typeShort")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(0.5f)
            )
            style.addLayer(label)
        }
    }

    fun update(style: Style, caches: List<CacheEntity>) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = JSONArray()
        for (c in caches) {
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().apply {
                            put(c.lon); put(c.lat)
                        })
                    })
                    put("properties", JSONObject().apply {
                        put("gccode", c.gccode)
                        put("name", c.name)
                        put("typeShort", typeShort(c.type))
                    })
                }
            )
        }
        val fc = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }
        source.setGeoJson(fc.toString())
    }

    private fun typeShort(type: String): String = when {
        type.startsWith("Trad") -> "T"
        type.startsWith("Multi") -> "M"
        type.startsWith("Mystery") -> "?"
        type.startsWith("Earth") -> "E"
        type.contains("Event") -> "Ev"
        type.startsWith("Virtual") -> "V"
        type.startsWith("Wherigo") -> "W"
        else -> "•"
    }

    fun bboxOfVisible(centerLat: Double, centerLon: Double, radiusKm: Double): DoubleArray {
        // Hrubý bbox ze středu + poloměr v km. Vrací [south, west, north, east]
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * Math.cos(Math.toRadians(centerLat)))
        return doubleArrayOf(centerLat - dLat, centerLon - dLon, centerLat + dLat, centerLon + dLon)
    }

    /** Bounding box pole projektovaných hranic mapy. */
    fun bboxOfBounds(southWest: LatLng, northEast: LatLng): DoubleArray =
        doubleArrayOf(southWest.latitude, southWest.longitude, northEast.latitude, northEast.longitude)
}
