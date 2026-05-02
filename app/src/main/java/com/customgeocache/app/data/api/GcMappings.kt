package com.customgeocache.app.data.api

/**
 * Číselné kódy geocaching.com → lidský text. Mapy odpovídají c:geo (CacheType / CacheSize).
 */
object GcMappings {

    fun typeName(id: Int?): String = when (id) {
        2 -> "Traditional"
        3 -> "Multi-Cache"
        4 -> "Virtual"
        5 -> "Letterbox Hybrid"
        6 -> "Event"
        8 -> "Mystery"
        9 -> "APE"
        11 -> "Webcam"
        12 -> "Locationless"
        13 -> "CITO"
        137 -> "Earthcache"
        453 -> "Mega-Event"
        605 -> "GPS Adventures Exhibit"
        1858 -> "Wherigo"
        3653 -> "Lost & Found"
        3773 -> "HQ Block Party"
        3774 -> "Lost and Found Event"
        7005 -> "Giga-Event"
        else -> "Unknown"
    }

    fun sizeName(id: Int?): String = when (id) {
        1 -> "Not chosen"
        2 -> "Micro"
        3 -> "Regular"
        4 -> "Large"
        5 -> "Virtual"
        6 -> "Other"
        8 -> "Small"
        else -> "Unknown"
    }

    /** Krátká barevně rozlišovací značka pro marker (3 znaky). */
    fun typeShort(id: Int?): String = when (id) {
        2 -> "T"
        3 -> "M"
        8 -> "?"
        137 -> "E"
        4 -> "V"
        6, 453, 7005, 3774 -> "Ev"
        1858 -> "W"
        else -> "•"
    }

    fun cacheStatusLabel(id: Int?): String? = when (id) {
        1 -> "Disabled"
        2 -> "Archived"
        else -> null
    }
}
