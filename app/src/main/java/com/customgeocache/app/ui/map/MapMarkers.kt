package com.customgeocache.app.ui.map

import android.util.Log
import com.customgeocache.app.data.db.entities.CacheEntity
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.geojson.Point

private const val TAG = "CGC.Markers"

/**
 * Markery kešek přes oficiální MapLibre Annotations Plugin (org.maplibre.gl:android-plugin-annotation-v9).
 *
 * Plugin je maintained, používá ho i c:geo. Pod kapotou má GeoJSON source + CircleLayer,
 * stejně jako moje předchozí ruční implementace, ale layer setup mu zaručeně projde —
 * ruční verze bývala citlivá na timing / expression validitu.
 */
class MapMarkersHolder {
    private var manager: CircleManager? = null
    private val annotationByGcCode = HashMap<String, Circle>()
    private var meAnnotation: Circle? = null

    fun attach(mapView: MapView, map: MapLibreMap, style: Style) {
        manager?.deleteAll()
        manager = CircleManager(mapView, map, style).also {
            Log.i(TAG, "CircleManager attached")
        }
        annotationByGcCode.clear()
        meAnnotation = null
    }

    fun detach() {
        manager?.deleteAll()
        manager = null
        annotationByGcCode.clear()
        meAnnotation = null
    }

    fun update(caches: List<CacheEntity>) {
        val mgr = manager ?: run {
            Log.w(TAG, "update: manager not attached, skipping")
            return
        }
        // Smaž všechno staré (kešky + me) a postav znovu — list má max ~200, je to rychlé
        mgr.deleteAll()
        annotationByGcCode.clear()
        // me se znovu přidá v update (ne tady — to dělá setUserLocation)
        meAnnotation = null

        var added = 0
        for (c in caches) {
            if (c.lat == 0.0 && c.lon == 0.0) continue
            val opts = CircleOptions()
                .withLatLng(org.maplibre.android.geometry.LatLng(c.lat, c.lon))
                .withCircleRadius(8f)
                .withCircleColor("#1B5E20")
                .withCircleStrokeColor("#FFFFFF")
                .withCircleStrokeWidth(2f)
                .withCircleOpacity(1f)
            val circle = mgr.create(opts)
            annotationByGcCode[c.gccode] = circle
            added++
        }
        Log.i(TAG, "update: added=$added (input=${caches.size})")
    }

    /** Modrá tečka aktuální polohy uživatele. */
    fun setUserLocation(lat: Double?, lon: Double?) {
        val mgr = manager ?: return
        meAnnotation?.let { mgr.delete(it) }
        meAnnotation = null
        if (lat != null && lon != null) {
            val opts = CircleOptions()
                .withLatLng(org.maplibre.android.geometry.LatLng(lat, lon))
                .withCircleRadius(7f)
                .withCircleColor("#1976D2")
                .withCircleStrokeColor("#FFFFFF")
                .withCircleStrokeWidth(3f)
                .withCircleOpacity(1f)
            meAnnotation = mgr.create(opts)
        }
    }

    /** Najde gccode keše blízko klepnutého bodu. */
    fun gccodeAt(lat: Double, lon: Double, toleranceDeg: Double = 0.0005): String? {
        var bestGc: String? = null
        var bestDist = Double.MAX_VALUE
        for ((gc, circle) in annotationByGcCode) {
            val p = circle.latLng
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
