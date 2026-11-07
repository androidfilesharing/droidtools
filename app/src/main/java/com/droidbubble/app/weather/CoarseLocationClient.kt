package com.droidbubble.app.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val timeMs: Long
)

class CoarseLocationClient(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun newestFix(nowMs: Long): Pair<Long, GeoFix>? {
        val locations = listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        val newest = locations.maxByOrNull { it.time } ?: return null
        val fix = newest.toFix()
        return (nowMs - fix.timeMs) to fix
    }

    @SuppressLint("MissingPermission")
    suspend fun singleFix(timeoutMs: Long = 10_000L): GeoFix? {
        return runCatching {
            withTimeoutOrNull(timeoutMs) { request() }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun request(): GeoFix? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val provider = when {
                runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.NETWORK_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                if (cont.isActive) cont.resume(location?.toFix())
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location.toFix())
                }
            }
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
        }
    }
}

private fun Location.toFix(): GeoFix = GeoFix(latitude, longitude, time)
