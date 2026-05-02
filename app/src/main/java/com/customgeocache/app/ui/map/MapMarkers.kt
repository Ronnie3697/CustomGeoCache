package com.customgeocache.app.ui.map

import android.util.Log
import com.customgeocache.app.data.db.entities.CacheEntity
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val TAG = "CGC.Markers"

/**
 * Helper na markery kešek na MapLibre mapě. Používáme GeoJSON source + jednoduchý
 * Circle layer + Symbol layer (text uvnitř kruhu).
 *
 * Po několika iteracích jsem se rozhodl pro **konstantní barvu kruhu** místo
 * Expression.match podle typu — některé verze MapLibre tichy ignorují špatně
 * vytvořené expression a layer pak nemaluje nic. Barvu podle typu řešíme přes
 * `typeShort` text uvnitř kruhu (T/M/?/E/Ev/V/W).
 */
object MapMarkers {

    const val SOURCE_ID = "caches-src"
    const val LAYER_CIRCLE = "caches-circle"
    const val LAYER_LABEL = "caches-label"

    fun ensureLayers(style: Style) {
        val existing = style.getSource(SOURCE_ID)
        if (existing == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[]}"))
            Log.i(TAG, "ensureLayers: source $SOURCE_ID created")
        } else if (existing !is GeoJsonSource) {
            style.removeSource(SOURCE_ID)
            style.addSource(GeoJsonSource(SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[]}"))
            Log.w(TAG, "ensureLayers: replaced non-GeoJson source")
        }

        if (style.getLayer(LAYER_CIRCLE) == null) {
            val circle = CircleLayer(LAYER_CIRCLE, SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleColor("#1B5E20"),         // konstantní zelená
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleOpacity(1.0f),
                PropertyFactory.visibility(Property.VISIBLE)
            )
            // addLayer dá vrstvu na vrchol stacku — nad raster tiles. Explicitní pojistka.
            style.addLayer(circle)
            Log.i(TAG, "ensureLayers: circle layer added; total layers=${style.layers.size}")
        }
        if (style.getLayer(LAYER_LABEL) == null) {
            val label = SymbolLayer(LAYER_LABEL, SOURCE_ID).withProperties(
                PropertyFactory.textField(Expression.get("typeShort")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(0.6f),
                PropertyFactory.visibility(Property.VISIBLE)
            )
            style.addLayer(label)
            Log.i(TAG, "ensureLayers: label layer added")
        }
    }

    fun update(style: Style, caches: List<CacheEntity>) {
        ensureLayers(style)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (source == null) {
            Log.w(TAG, "update: source still null after ensureLayers — style not loaded?")
            return
        }
        val features = JSONArray()
        var skipped = 0
        for (c in caches) {
            // Zahodíme zjevně nesmyslné lokace (premium-only keše bez souřadnic).
            if (c.lat == 0.0 && c.lon == 0.0) { skipped++; continue }
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
        Log.i(TAG, "update: features=${features.length()} (skipped=$skipped) layers=${style.layers.size} circleVisible=${style.getLayer(LAYER_CIRCLE)?.visibility?.value}")
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
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * Math.cos(Math.toRadians(centerLat)))
        return doubleArrayOf(centerLat - dLat, centerLon - dLon, centerLat + dLat, centerLon + dLon)
    }

    fun bboxOfBounds(southWest: LatLng, northEast: LatLng): DoubleArray =
        doubleArrayOf(southWest.latitude, southWest.longitude, northEast.latitude, northEast.longitude)
}
