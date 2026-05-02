package com.customgeocache.app.ui.compass

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow polohy uživatele přes Fused Location Provider (Google Play Services).
 * Vyžaduje, aby volající už měl ACCESS_FINE_LOCATION permission.
 */
object LocationFlow {

    @SuppressLint("MissingPermission")
    fun observe(context: Context, intervalMs: Long = 1500): Flow<Location> = callbackFlow {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            close()
            return@callbackFlow
        }

        val provider = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        provider.requestLocationUpdates(request, callback, null)
        provider.lastLocation.addOnSuccessListener { it?.let { trySend(it) } }

        awaitClose { provider.removeLocationUpdates(callback) }
    }
}
