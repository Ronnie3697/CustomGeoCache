package com.customgeocache.app.ui.map

/**
 * Mapy.com tile zdroj. Mapy.com (developer.mapy.com) v současnosti vystavuje
 * raster XYZ tiles na endpointu:
 *
 *     https://api.mapy.cz/v1/maptiles/{layer}/256/{z}/{x}/{y}?apikey=KEY
 *
 * Vector style.json neposkytují, takže místo `MapLibre.setStyle(url)` sestavujeme
 * inline MapLibre style JSON s jedinou raster vrstvou. Pan/zoom/rotace jdou pořád
 * přes GPU — UX zůstává moderní, jen tiles jsou bitmapové.
 *
 * Vrstvy:
 *   basic    — výchozí, plná mapa
 *   outdoor  — turistická + výškopis
 *   aerial   — letecký pohled
 *   winter   — zimní mapa s vleky a sjezdovkami
 */
enum class MapyLayer(val id: String, val displayName: String, val maxZoom: Int) {
    BASIC("basic", "Basic", 19),
    OUTDOOR("outdoor", "Outdoor", 19),
    AERIAL("aerial", "Letecká", 20),
    WINTER("winter", "Zimní", 18);

    companion object {
        fun fromId(id: String): MapyLayer = entries.firstOrNull { it.id == id } ?: BASIC
    }
}

object MapyStyles {

    private const val ATTRIBUTION =
        "&copy; <a href=\"https://mapy.com\">Seznam.cz, a.s.</a>, " +
        "<a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"

    /** XYZ tile URL šablona. MapLibre {z}/{x}/{y} placeholdery musí zůstat nezměněné. */
    fun tileUrl(layer: MapyLayer, apiKey: String): String =
        "https://api.mapy.cz/v1/maptiles/${layer.id}/256/{z}/{x}/{y}?apikey=$apiKey"

    /**
     * Sestaví MapLibre style JSON s jedinou raster vrstvou.
     * Vrácený řetězec se předá do `Style.Builder().fromJson(...)`.
     */
    fun rasterStyleJson(layer: MapyLayer, apiKey: String): String {
        val tile = tileUrl(layer, apiKey).replace("\"", "\\\"")
        return """
            {
              "version": 8,
              "name": "Mapy.com ${layer.displayName}",
              "sources": {
                "mapy": {
                  "type": "raster",
                  "tiles": ["$tile"],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": ${layer.maxZoom},
                  "attribution": "$ATTRIBUTION"
                }
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": { "background-color": "#e8e8e8" }
                },
                {
                  "id": "mapy-tiles",
                  "type": "raster",
                  "source": "mapy",
                  "minzoom": 0,
                  "maxzoom": ${layer.maxZoom}
                }
              ]
            }
        """.trimIndent()
    }
}
