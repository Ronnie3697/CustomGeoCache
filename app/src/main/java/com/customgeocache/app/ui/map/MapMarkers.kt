package com.customgeocache.app.ui.map

import android.util.Log
import com.customgeocache.app.data.db.entities.CacheEntity
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.Property

private const val TAG = "CGC.Markers"

/**
 * Drží MapLibre Annotation managery pro keše (SymbolManager — ikonky podle typu)
 * a aktuální pozici uživatele (CircleManager — modrá tečka).
 *
 * Při každém připojení (`attach`) registrujeme do stylu obrázky ikon přes addImage
 * a vytvoříme oba managery. Při změně mapové vrstvy (Style reload) musí volající
 * znovuvolat `attach` s novým Style.
 */
class MapMarkersHolder {
    private var symbolManager: SymbolManager? = null
    private var circleManager: CircleManager? = null
    private val symbolByGcCode = HashMap<String, Symbol>()
    private var meCircle: Circle? = null

    fun attach(mapView: MapView, map: MapLibreMap, style: Style) {
        symbolManager?.deleteAll()
        circleManager?.deleteAll()

        // Zaregistruj všechny ikony typů do stylu (idempotentní přes sdyId — pokud už
        // tam jsou z předchozího stylu, addImage je přepíše).
        for ((id, bitmap) in MapMarkerIcons.buildAll()) {
            style.addImage(id, bitmap)
        }

        symbolManager = SymbolManager(mapView, map, style).apply {
            iconAllowOverlap = true
            iconIgnorePlacement = true
        }
        circleManager = CircleManager(mapView, map, style)
        symbolByGcCode.clear()
        meCircle = null
        Log.i(TAG, "managers attached, icons registered")
    }

    fun detach() {
        symbolManager?.deleteAll()
        circleManager?.deleteAll()
        symbolManager = null
        circleManager = null
        symbolByGcCode.clear()
        meCircle = null
    }

    fun update(caches: List<CacheEntity>) {
        val mgr = symbolManager ?: run {
            Log.w(TAG, "update: SymbolManager not attached, skipping")
            return
        }
        mgr.deleteAll()
        symbolByGcCode.clear()

        var added = 0
        for (c in caches) {
            if (c.lat == 0.0 && c.lon == 0.0) continue
            val opts = SymbolOptions()
                .withLatLng(LatLng(c.lat, c.lon))
                .withIconImage(MapMarkerIcons.Id.forCacheType(c.type))
                .withIconAnchor(Property.ICON_ANCHOR_BOTTOM)   // špička pinu = souřadnice
                .withIconSize(0.55f)
            val symbol = mgr.create(opts)
            symbolByGcCode[c.gccode] = symbol
            added++
        }
        Log.i(TAG, "update: added=$added (input=${caches.size})")
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

    /** Najde gccode keše blízko klepnutého bodu (geo-based, pixel queryRendered je
     *  nestabilní s annotation pluginem). */
    fun gccodeAt(lat: Double, lon: Double, toleranceDeg: Double = 0.0008): String? {
        var bestGc: String? = null
        var bestDist = Double.MAX_VALUE
        for ((gc, sym) in symbolByGcCode) {
            val p = sym.latLng
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
