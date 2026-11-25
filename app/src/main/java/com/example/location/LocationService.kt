package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.AirportCatalog
import com.example.domain.AirportMapProjection
import com.example.model.AirportStation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DetectedLocationResult(
  val latitude: Double,
  val longitude: Double,
  val cityName: String? = null,
  val providerLabel: String = "GPS",
)

data class NearestAirportResult(
  val airport: AirportStation,
  val distanceKm: Double,
  val location: DetectedLocationResult,
)

interface LocationService {
  suspend fun detectCurrentLocation(): Result<DetectedLocationResult>

  suspend fun detectNearestAirport(
    stations: List<AirportStation> = AirportCatalog.allStations,
  ): Result<NearestAirportResult> {
    return detectCurrentLocation().mapCatching { loc ->
      val (airport, distKm) = LocationServiceHelper.findNearestAirport(
        latitude = loc.latitude,
        longitude = loc.longitude,
        stations = stations,
      ) ?: throw IllegalStateException("Список аэропортов пуст")
      NearestAirportResult(
        airport = airport,
        distanceKm = distKm,
        location = loc,
      )
    }
  }
}

object LocationServiceHelper {
  fun findNearestAirport(
    latitude: Double,
    longitude: Double,
    stations: List<AirportStation> = AirportCatalog.allStations,
  ): Pair<AirportStation, Double>? {
    val best = stations.minByOrNull { station ->
      AirportMapProjection.haversineKm(latitude, longitude, station.latitude, station.longitude)
    } ?: return null
    val distKm = AirportMapProjection.haversineKm(latitude, longitude, best.latitude, best.longitude).toDouble()
    return best to distKm
  }
}

object NoOpLocationService : LocationService {
  override suspend fun detectCurrentLocation(): Result<DetectedLocationResult> {
    return Result.failure(IllegalStateException("Сервис геолокации недоступен"))
  }
}

class PlayServicesLocationService(
  private val context: Context,
) : LocationService {

  private val fusedClient by lazy {
    LocationServices.getFusedLocationProviderClient(context)
  }

  fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  @SuppressLint("MissingPermission")
  override suspend fun detectCurrentLocation(): Result<DetectedLocationResult> {
    if (!hasLocationPermission()) {
      return Result.failure(SecurityException("Нет разрешения на доступ к местоположению"))
    }
    return runCatching {
      val tokenSource = CancellationTokenSource()
      val currentLoc = runCatching {
        fusedClient
          .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
          .awaitTask(tokenSource)
      }.getOrNull()

      val fusedLast = if (currentLoc == null) {
        runCatching { fusedClient.lastLocation.awaitTask(null) }.getOrNull()
      } else {
        null
      }

      val location = currentLoc ?: fusedLast ?: getSystemLastKnownLocation()
        ?: throw IllegalStateException("Не удалось получить GPS/сетевые координаты")

      val city = resolveCityName(location.latitude, location.longitude)
      val provider = when (location.provider?.lowercase(Locale.US)) {
        "gps" -> "GPS"
        "network" -> "Сеть"
        "fused" -> "Play Services"
        else -> "GPS/Сеть"
      }
      DetectedLocationResult(
        latitude = location.latitude,
        longitude = location.longitude,
        cityName = city,
        providerLabel = provider,
      )
    }
  }

  @SuppressLint("MissingPermission")
  private fun getSystemLastKnownLocation(): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
      .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
    return candidates.maxByOrNull { it.time }
  }

  private suspend fun resolveCityName(latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.forLanguageTag("ru-RU"))
    return withContext(Dispatchers.IO) {
      runCatching {
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(
              latitude,
              longitude,
              1,
              object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                  if (cont.isActive) cont.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                  if (cont.isActive) cont.resume(emptyList())
                }
              },
            )
          }
        } else {
          @Suppress("DEPRECATION")
          geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }
        val first = addresses.firstOrNull()
        first?.locality
          ?: first?.subAdminArea
          ?: first?.adminArea
      }.getOrNull()
    }
  }

  private suspend fun <T> Task<T>.awaitTask(cancellationTokenSource: CancellationTokenSource?): T? {
    return suspendCancellableCoroutine { cont ->
      addOnSuccessListener { value ->
        if (cont.isActive) cont.resume(value)
      }
      addOnFailureListener { err ->
        if (cont.isActive) cont.resumeWithException(err)
      }
      addOnCanceledListener {
        if (cont.isActive) cont.resume(null)
      }
      if (cancellationTokenSource != null) {
        cont.invokeOnCancellation {
          cancellationTokenSource.cancel()
        }
      }
    }
  }
}
