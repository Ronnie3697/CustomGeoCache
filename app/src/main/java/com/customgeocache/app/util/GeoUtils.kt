package com.customgeocache.app.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geo helpers — Haversine distance & initial bearing.
 * Vrací vzdálenost v metrech a bearing ve stupních (0..360 od severu).
 */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Haversine vzdálenost mezi dvěma body v metrech. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /** Initial bearing z bodu 1 na bod 2 ve stupních (0=N, 90=E, 180=S, 270=W). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360) % 360
    }

    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        meters < 10_000 -> String.format("%.2f km", meters / 1000)
        else -> String.format("%.1f km", meters / 1000)
    }
}
