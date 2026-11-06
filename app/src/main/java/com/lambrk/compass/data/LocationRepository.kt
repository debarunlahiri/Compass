package com.lambrk.compass.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Where we are, and how far magnetic north is from true north here. */
data class DeclinationInfo(
    val latitude: Double,
    val longitude: Double,
    /** Degrees east of true north (positive) that magnetic north points. */
    val declination: Float,
)

/**
 * Only used for the "true north" setting: the magnetic declination depends on where you are,
 * so we need a rough (coarse) location once. No continuous tracking.
 */
class LocationRepository(private val context: Context) {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun declination(): DeclinationInfo? {
        val location = currentLocation() ?: return null
        val field = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        )
        return DeclinationInfo(location.latitude, location.longitude, field.declination)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val lm = locationManager ?: return null
        if (!hasPermission()) return null

        val providers = lm.getProviders(true)
        val lastKnown = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null) return lastKnown

        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> providers.firstOrNull() ?: return null
        }
        return withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    // Implemented explicitly: these are abstract on older Android versions.
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    }
}
