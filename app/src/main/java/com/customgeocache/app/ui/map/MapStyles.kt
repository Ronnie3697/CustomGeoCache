package com.customgeocache.app.ui.map

/**
 * Tile provider — všechny mapové podklady, které appka umí. URLs ověřené proti c:geo
 * `tileproviders` package.
 *
 * Mapy.com vrstvy vyžadují uživatelův API klíč ze [developer.mapy.com](https://developer.mapy.com).
 * Bez klíče se uplatní fallback na OSM (viz `effective()`).
 */
enum class MapTileProvider(
    val id: String,
    val displayName: String,
    private val tileUrlTemplate: String,
    val maxZoom: Int,
    val tileSize: Int,
    val attributionHtml: String,
    val requiresApiKey: Boolean
) {
    MAPY_BASIC(
        "basic", "Mapy.com Basic",
        "https://api.mapy.cz/v1/maptiles/basic/256/{z}/{x}/{y}?apikey={API_KEY}",
        19, 256,
        "&copy; <a href='https://mapy.com'>Seznam.cz</a>", true
    ),
    MAPY_OUTDOOR(
        "outdoor", "Mapy.com Outdoor",
        "https://api.mapy.cz/v1/maptiles/outdoor/256/{z}/{x}/{y}?apikey={API_KEY}",
        19, 256,
        "&copy; <a href='https://mapy.com'>Seznam.cz</a>", true
    ),
    MAPY_AERIAL(
        "aerial", "Mapy.com Letecká",
        "https://api.mapy.cz/v1/maptiles/aerial/256/{z}/{x}/{y}?apikey={API_KEY}",
        20, 256,
        "&copy; <a href='https://mapy.com'>Seznam.cz</a>", true
    ),
    MAPY_WINTER(
        "winter", "Mapy.com Zimní",
        "https://api.mapy.cz/v1/maptiles/winter/256/{z}/{x}/{y}?apikey={API_KEY}",
        18, 256,
        "&copy; <a href='https://mapy.com'>Seznam.cz</a>", true
    ),
    OSM(
        "osm", "OpenStreetMap",
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        18, 256,
        "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors",
        false
    ),
    OSM_DE(
        "osmde", "OSM Deutsch",
        "https://tile.openstreetmap.de/{z}/{x}/{y}.png",
        18, 256,
        "&copy; OSM Deutschland", false
    ),
    OPEN_TOPO(
        "topo", "OpenTopoMap",
        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
        17, 256,
        "&copy; OSM, SRTM | Tiles &copy; OpenTopoMap (CC-BY-SA)", false
    ),
    CYCLOSM(
        "cyclosm", "CyclOSM",
        "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png",
        18, 256,
        "&copy; CyclOSM, OSM contributors", false
    );

    fun resolvedTileUrl(apiKey: String?): String =
        tileUrlTemplate.replace("{API_KEY}", apiKey.orEmpty())

    companion object {
        fun fromId(id: String): MapTileProvider = entries.firstOrNull { it.id == id } ?: OSM
    }
}

object MapStyles {
    fun rasterStyleJson(provider: MapTileProvider, apiKey: String?): String {
        val tile = provider.resolvedTileUrl(apiKey)
        return """
            {
              "version": 8,
              "name": "${provider.displayName}",
              "sources": {
                "tiles": {
                  "type": "raster",
                  "tiles": ["$tile"],
                  "tileSize": ${provider.tileSize},
                  "minzoom": 0,
                  "maxzoom": ${provider.maxZoom},
                  "attribution": "${provider.attributionHtml}"
                }
              },
              "layers": [
                { "id": "background", "type": "background", "paint": { "background-color": "#e8e8e8" } },
                { "id": "tiles", "type": "raster", "source": "tiles", "minzoom": 0, "maxzoom": ${provider.maxZoom} }
              ]
            }
        """.trimIndent()
    }
}

/** Backward compat alias — starší kód v MapMarkerIcons / MapScreen volá MapyLayer.fromId. */
typealias MapyLayer = MapTileProvider
