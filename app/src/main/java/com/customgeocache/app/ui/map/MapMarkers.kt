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
 * Per keš se vytvářejí 1–3 symboly:
 *  - hlavní pin (vždy)
 *  - smajlík (pokud `cache.isFound`) v levém horním rohu nad pinem
 *  - disketka (pokud má cached detail = je offline) v pravém horním rohu nad pinem
 *
 * Při změně mapové vrstvy (Style reload) musí volající znovuvolat `attach`.
 */
class MapMarkersHolder {
    private var symbolManager: SymbolManager? = null
    private var circleManager: CircleManager? = null

    private val pinByGcCode = HashMap<String, Symbol>()
    private val foundByGcCode = HashMap<String, Symbol>()
    private val offlineByGcCode = HashMap<String, Symbol>()
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
        clearAllCollections()
        Log.i(TAG, "managers attached, icons registered")
    }

    fun detach() {
        symbolManager?.deleteAll()
        circleManager?.deleteAll()
        symbolManager = null
        circleManager = null
        clearAllCollections()
    }

    private fun clearAllCollections() {
        pinByGcCode.clear()
        foundByGcCode.clear()
        offlineByGcCode.clear()
        meCircle = null
    }

    fun update(caches: List<CacheEntity>) {
        val mgr = symbolManager ?: run {
            Log.w(TAG, "update: SymbolManager not attached, skipping")
            return
        }
        mgr.deleteAll()
        clearAllCollections()

        var added = 0
        for (c in caches) {
            if (c.lat == 0.0 && c.lon == 0.0) continue
            val isOffline = !c.description.isNullOrBlank()  // detail page už byl stažen
            val ll = LatLng(c.lat, c.lon)

            // Hlavní pin (špička sedí na souřadnici)
            val pinSym = mgr.create(
                SymbolOptions()
                    .withLatLng(ll)
                    .withIconImage(MapMarkerIcons.Id.forCacheType(c.type))
                    .withIconAnchor(Property.ICON_ANCHOR_BOTTOM)
                    .withIconSize(0.55f)
            )
            pinByGcCode[c.gccode] = pinSym

            // Smajlík vlevo nahoře (offset měřený v pixelech bitmap iconu, bere se přes iconAnchor)
            if (c.isFound) {
                val foundSym = mgr.create(
                    SymbolOptions()
                        .withLatLng(ll)
                        .withIconImage(MapMarkerIcons.Id.DECOR_FOUND)
                        .withIconAnchor(Property.ICON_ANCHOR_BOTTOM_RIGHT)
                        .withIconSize(0.55f)
                        .withIconOffset(arrayOf(-26f, -52f))
                )
                foundByGcCode[c.gccode] = foundSym
            }

            if (isOffline) {
                val offlineSym = mgr.create(
                    SymbolOptions()
                        .withLatLng(ll)
                        .withIconImage(MapMarkerIcons.Id.DECOR_OFFLINE)
                        .withIconAnchor(Property.ICON_ANCHOR_BOTTOM_LEFT)
                        .withIconSize(0.55f)
                        .withIconOffset(arrayOf(26f, -52f))
                )
                offlineByGcCode[c.gccode] = offlineSym
            }
            added++
        }
        Log.i(TAG, "update: added=$added (input=${caches.size}, found=${foundByGcCode.size}, offline=${offlineByGcCode.size})")
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
        for ((gc, sym) in pinByGcCode) {
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
