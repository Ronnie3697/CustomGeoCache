package com.customgeocache.app.ui.map

/**
 * Mapy.com tile / style URL builder. Aktuální (2025+) endpoint je `api.mapy.cz/v1/maptiles/...`.
 *
 * Podporované vrstvy:
 *   basic    — vector, výchozí
 *   outdoor  — vector, s výškopisem a turistikou
 *   aerial   — raster (letecký pohled)
 *   winter   — raster (zimní mapa)
 */
enum class MapyLayer(val id: String, val isVector: Boolean, val displayName: String) {
    BASIC("basic", true, "Basic"),
    OUTDOOR("outdoor", true, "Outdoor"),
    AERIAL("aerial", false, "Letecká"),
    WINTER("winter", false, "Zimní");

    companion object {
        fun fromId(id: String): MapyLayer = entries.firstOrNull { it.id == id } ?: BASIC
    }
}

object MapyStyles {
    /** URL k MapLibre style.json pro vector vrstvy. Zahrnuje API klíč. */
    fun styleUrl(layer: MapyLayer, apiKey: String): String =
        "https://api.mapy.cz/v1/maptiles/${layer.id}/style.json?apikey=$apiKey"

    /** URL šablona pro raster XYZ tiles. */
    fun rasterTileUrl(layer: MapyLayer, apiKey: String): String =
        "https://api.mapy.cz/v1/maptiles/${layer.id}/256/{z}/{x}/{y}?apikey=$apiKey"
}
