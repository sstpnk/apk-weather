package tech.stpnk.weather.data.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Геолокация через платформенный LocationManager (без Play Services). */
class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun currentCoords(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val last = runCatching {
            manager.getProviders(true)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val viaApi = runCatching { withTimeoutOrNull(10_000L) { requestCurrent(manager) } }.getOrNull()
            viaApi ?: last?.let { it.latitude to it.longitude }
        } else {
            last?.let { it.latitude to it.longitude }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(manager: LocationManager): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            var done = false
            val executor = ContextCompat.getMainExecutor(context)
            for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                if (provider !in manager.allProviders) continue
                runCatching {
                    manager.getCurrentLocation(provider, signal, executor) { location: Location? ->
                        if (!done && location != null) {
                            done = true
                            cont.resume(location.latitude to location.longitude)
                        }
                    }
                }
            }
        }
}
