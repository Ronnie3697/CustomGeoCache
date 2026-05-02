package com.customgeocache.app.data.parser

import com.customgeocache.app.data.db.entities.CacheEntity
import org.jsoup.Jsoup
import java.util.regex.Pattern

/**
 * HTML parser detail stránky `https://www.geocaching.com/geocache/GCxxxxx?decrypt=y`.
 * Selektory portovány z c:geo `GCConstants.java` / `GCParser.java`.
 *
 * Výsledek je doplněk k CacheEntity (description / hint / attributes / lat/lon),
 * základní fields (name, type, D/T, size) by měly už dorazit ze search endpointu.
 */
object GcDetailParser {

    /** Extrahuje URL obrázků z popisu keše. Filtruje odkazy mimo geocaching.com a typické UI ikony. */
    fun extractImages(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val descEls = doc.select(
            "span#ctl00_ContentBody_LongDescription img, " +
            "span#ctl00_ContentBody_ShortDescription img"
        )
        return descEls.mapNotNull { el ->
            val src = el.absUrl("src").ifBlank { el.attr("src") }
            src.takeIf {
                it.isNotBlank() &&
                !it.contains("/images/wpttypes/") &&        // ikony typů kešek
                !it.contains("/images/icons/attributes/") && // ikony attributů
                !it.contains("blank-")
            }
        }.distinct()
    }

    fun parse(gccode: String, html: String, baseFromSearch: CacheEntity? = null): CacheEntity? {
        val doc = Jsoup.parse(html)

        // Premium only check — pokud je tam zámek, nemůžeme číst víc než basic info
        val isPremiumOnly = html.contains("class=\"illustration lock-icon\"")

        val name = baseFromSearch?.name?.takeIf { it.isNotBlank() }
            ?: doc.select("span#ctl00_ContentBody_CacheName").text().ifBlank { gccode }

        val descHtml = doc.select("span#ctl00_ContentBody_LongDescription").html()
        val shortDescHtml = doc.select("span#ctl00_ContentBody_ShortDescription").html()
        val description = (shortDescHtml + (if (descHtml.isNotBlank()) "<br/><br/>$descHtml" else ""))
            .ifBlank { null }

        // Hint je v ROT13 v <div class="hint">
        val hint = doc.select("div#div_hint, div.hint").text().trim().ifBlank { null }

        // Attributes — img alt v sekci attributy
        val attrs = doc.select("div.CacheDetailNavigationWidget img.attribute, div.WidgetBody img[alt]")
            .mapNotNull { it.attr("alt").takeIf { a -> a.isNotBlank() && !a.startsWith("blank-") } }
            .distinct()
            .joinToString(",")
            .ifBlank { null }

        // Lat/Lon — `<span id="uxLatLon">N 50° 04.123 E 014° 25.456</span>`
        val latLonText = doc.select("span#uxLatLon").text().trim()
        val coords = parseLatLon(latLonText)

        val owner = doc.select("div#ctl00_ContentBody_mcd1 a").text().ifBlank { baseFromSearch?.owner }

        // Difficulty / Terrain
        val difficulty = parseStarRating(html, PATTERN_DIFFICULTY) ?: baseFromSearch?.difficulty ?: 0f
        val terrain = parseStarRating(html, PATTERN_TERRAIN) ?: baseFromSearch?.terrain ?: 0f

        // Size — img alt
        val size = doc.select("p.Size span").text().trim()
            .ifBlank { baseFromSearch?.size ?: "Unknown" }

        // Type
        val type = doc.select("ul.CacheDetailNavigation li.cache-type").text().trim()
            .ifBlank { baseFromSearch?.type ?: "Unknown" }

        return CacheEntity(
            gccode = gccode,
            name = name,
            type = type,
            lat = coords?.first ?: baseFromSearch?.lat ?: 0.0,
            lon = coords?.second ?: baseFromSearch?.lon ?: 0.0,
            difficulty = difficulty,
            terrain = terrain,
            size = size,
            owner = owner,
            isFound = baseFromSearch?.isFound == true,
            isFavorite = baseFromSearch?.isFavorite == true,
            description = description,
            hint = hint,
            attributes = attrs,
            lastUpdatedMillis = System.currentTimeMillis()
        ).also {
            if (isPremiumOnly) {
                // I bez detailu vrátíme aspoň záhlaví
            }
        }
    }

    private val PATTERN_DIFFICULTY: Pattern = Pattern.compile(
        "<span id=\"ctl00_ContentBody_uxLegendScale\"[^>]*>.*?stars/(\\d+(?:_\\d+)?)\\.gif",
        Pattern.DOTALL
    )
    private val PATTERN_TERRAIN: Pattern = Pattern.compile(
        "<span id=\"ctl00_ContentBody_Localize12\"[^>]*>.*?stars/(\\d+(?:_\\d+)?)\\.gif",
        Pattern.DOTALL
    )

    private fun parseStarRating(html: String, pattern: Pattern): Float? {
        val m = pattern.matcher(html)
        if (!m.find()) return null
        val raw = m.group(1) ?: return null
        return raw.replace("_", ".").toFloatOrNull()
    }

    /** "N 50° 04.123 E 014° 25.456" → 50.06872, 14.42427 */
    private fun parseLatLon(text: String): Pair<Double, Double>? {
        if (text.isBlank()) return null
        val regex = Regex(
            "([NS])\\s*(\\d+)°\\s*([\\d.]+)\\s*([EW])\\s*(\\d+)°\\s*([\\d.]+)"
        )
        val m = regex.find(text) ?: return null
        val (latH, latD, latM, lonH, lonD, lonM) = m.destructured
        val lat = (latD.toDouble() + latM.toDouble() / 60).let { if (latH == "S") -it else it }
        val lon = (lonD.toDouble() + lonM.toDouble() / 60).let { if (lonH == "W") -it else it }
        return lat to lon
    }
}
