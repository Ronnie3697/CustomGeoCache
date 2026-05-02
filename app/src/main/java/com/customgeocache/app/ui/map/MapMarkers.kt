package com.customgeocache.app.ui.map

import android.util.Log
import com.customgeocache.app.data.db.entities.CacheEntity
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.SymbolManager

private const val TAG = "CGC.Markers"

/**
 * Markery kešek přes oficiální MapLibre Annotations Plugin.
 *
 * **Performance:** Per-cache `mgr.create(SymbolOptions)` rebuilduje GeoJSON source
 * pro každý symbol zvlášť — při 200+ keších v hustých oblastech (Brno apod.) to
 * trvá několik sekund. Místo toho buildujeme jeden velký FeatureCollection s pin
 * + found-decoration + offline-decoration features a SymbolManager.create(FC) je
 * vloží naráz, jeden source rebuild.
 */
class MapMarkersHolder {
    private var symbolManager: SymbolManager? = null
    private var circleManager: CircleManager? = null

    /** gccode → souřadnice keše. Slouží pro tap-to-find lookup, ne pro samotný render. */
    private val locByGcCode = HashMap<String, LatLng>()
    private var meCircle: Circle? = null

    fun attach(mapView: MapView, map: MapLibreMap, style: Style) {
        symbolManager?.deleteAll()
        circleManager?.deleteAll()

        for ((id, bitmap) in MapMarkerIcons.buildAll()) {
            style.addImage(id, bitmap)
        }

        symbolManager = SymbolManager(mapView, map, style).apply {
            iconAllowOverlap = true
            iconIgnorePlacement = true
        }
        circleManager = CircleManager(mapView, map, style)
        locByGcCode.clear()
        meCircle = null
        Log.i(TAG, "managers attached, icons registered")
    }

    fun detach() {
        symbolManager?.deleteAll()
        circleManager?.deleteAll()
        symbolManager = null
        circleManager = null
        locByGcCode.clear()
        meCircle = null
    }

    fun update(caches: List<CacheEntity>) {
        val mgr = symbolManager ?: run {
            Log.w(TAG, "update: SymbolManager not attached, skipping")
            return
        }
        val t0 = System.currentTimeMillis()
        mgr.deleteAll()
        locByGcCode.clear()

        val features = JSONArray()
        var pins = 0
        var founds = 0
        var offlines = 0
        for (c in caches) {
            if (c.lat == 0.0 && c.lon == 0.0) continue
            val isOffline = !c.description.isNullOrBlank()

            // Hlavní pin
            features.put(buildFeature(
                lat = c.lat, lon = c.lon,
                iconId = MapMarkerIcons.Id.forCacheType(c.type),
                anchor = "bottom",
                offset = null,
                size = 0.55f,
                gccode = c.gccode
            ))
            locByGcCode[c.gccode] = LatLng(c.lat, c.lon)
            pins++

            if (c.isFound) {
                features.put(buildFeature(
                    lat = c.lat, lon = c.lon,
                    iconId = MapMarkerIcons.Id.DECOR_FOUND,
                    anchor = "bottom-right",
                    offset = doubleArrayOf(-26.0, -52.0),
                    size = 0.55f,
                    gccode = null
                ))
                founds++
            }
            if (isOffline) {
                features.put(buildFeature(
                    lat = c.lat, lon = c.lon,
                    iconId = MapMarkerIcons.Id.DECOR_OFFLINE,
                    anchor = "bottom-left",
                    offset = doubleArrayOf(26.0, -52.0),
                    size = 0.55f,
                    gccode = null
                ))
                offlines++
            }
        }

        val fc = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }
        // BATCH create — jeden source rebuild místo 200+
        mgr.create(fc.toString())

        val ms = System.currentTimeMillis() - t0
        Log.i(TAG, "update: pins=$pins found=$founds offline=$offlines features=${features.length()} took ${ms}ms")
    }

    private fun buildFeature(
        lat: Double, lon: Double,
        iconId: String, anchor: String,
        offset: DoubleArray?, size: Float,
        gccode: String?
    ): JSONObject {
        return JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(lon); put(lat)
                })
            })
            put("properties", JSONObject().apply {
                put("icon-image", iconId)
                put("icon-anchor", anchor)
                put("icon-size", size.toDouble())
                if (offset != null) {
                    put("icon-offset", JSONArray().apply {
                        put(offset[0]); put(offset[1])
                    })
                }
                if (gccode != null) put("gccode", gccode)
            })
        }
    }

    fun setUserLocation(lat: Double?, lon: Double?) {
        val mgr = circleManager ?: return
        meCircle?.let { mgr.delete(it) }
        meCircle = null
        if (lat != null && lon != null) {
            val opts = CircleOptions()
                .withLatLng(LatLng(lat, lon))
                .withCircleRadius(7f)
                .withCircleColor("#1976D2")
                .withCircleStrokeColor("#FFFFFF")
                .withCircleStrokeWidth(3f)
                .withCircleOpacity(1f)
            meCircle = mgr.create(opts)
        }
    }

    fun gccodeAt(lat: Double, lon: Double, toleranceDeg: Double = 0.0008): String? {
        var bestGc: String? = null
        var bestDist = Double.MAX_VALUE
        for ((gc, p) in locByGcCode) {
            val dLat = lat - p.latitude
            val dLon = lon - p.longitude
            val d = dLat * dLat + dLon * dLon
            if (d < bestDist && d < toleranceDeg * toleranceDeg) {
                bestDist = d
                bestGc = gc
            }
        }
        return bestGc
    }
}
