package com.example.data

import com.example.domain.AviationWeatherParser
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class AviationWeatherRepository {

  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .build()

  private val customServerClient = CustomMeteoServerClient()

  data class FetchResult(
    val weather: DecodedWeather,
    val resolvedStation: AirportStation,
    val logMessages: List<String>,
  )

  fun createEmptyWeather(
    station: AirportStation,
    sourceLabel: String = "ожидание данных сервера",
  ): DecodedWeather {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val sunTimes = com.example.domain.SolarCalculator.calculateSunriseSunset(
      latitude = station.latitude,
      longitude = station.longitude,
      utcOffsetHours = station.utcOffsetHours,
      calendar = cal,
    )
    return DecodedWeather(
      stationIcao = station.icao,
      observationTimeUtc = "--:--Z",
      localObsTime = "--:--",
      receivedTimeLocal = AviationWeatherParser.currentLocalTimeString(station.utcOffsetHours),
      wind = com.example.model.WindData(
        directionDeg = null,
        isVariable = false,
        isCalm = true,
        speedKt = 0,
        speedMps = 0.0,
      ),
      visibilityMeters = null,
      isCavok = false,
      rvrPrimaryMeters = null,
      rvrReciprocalMeters = null,
      weatherPhenomena = "Нет данных",
      clouds = emptyList(),
      ceilingMeters = null,
      ceilingCode = null,
      temperatureC = null,
      dewPointC = null,
      qnhHpa = null,
      qnhMmHg = null,
      qnhInHg = null,
      qfeHpa = null,
      qfeMmHg = null,
      qfeInHg = null,
      frictionCoeff = null,
      specialConditions = "нет",
      rawMetar = "METAR ${station.icao} — нет данных",
      rawTaf = "TAF ${station.icao} — нет данных",
      sourceLabel = sourceLabel,
      sunriseLocal = sunTimes.sunriseLocal,
      sunsetLocal = sunTimes.sunsetLocal,
    )
  }

  suspend fun fetchLiveWeather(
    station: AirportStation,
    serverConfig: ServerConfig = ServerConfig(),
  ): Result<FetchResult> = withContext(Dispatchers.IO) {
    val logs = mutableListOf<String>()
    val icao = station.icao.trim().uppercase()
    var resolvedStation = station

    var customError: String? = null
    if (serverConfig.useCustomServer && serverConfig.url.isNotBlank()) {
      val outcome = customServerClient.fetchMetarAndTaf(icao, serverConfig)
      logs.addAll(outcome.logs)
      val customMetar = outcome.rawMetar
      if (!customMetar.isNullOrBlank()) {
        val finalTaf = outcome.rawTaf ?: "TAF $icao — нет данных"
        val decoded = AviationWeatherParser.parse(
          rawMetarInput = customMetar,
          rawTafInput = finalTaf,
          station = resolvedStation,
          sourceLabel = "сервер ${outcome.sourceHost}",
        )
        logs.add("Успешно декодирован $icao с ${outcome.sourceHost} (${decoded.observationTimeUtc})")
        return@withContext Result.success(FetchResult(decoded, resolvedStation, logs))
      } else {
        customError = outcome.errorMessage
        if (!serverConfig.fallbackToNoaa) {
          return@withContext Result.failure(
            IllegalStateException(customError ?: "Нет данных для $icao на сервере ${outcome.sourceHost}"),
          )
        }
        logs.add("Переход к резервному источнику aviationweather.gov…")
      }
    }

    try {
      val metarUrl = "https://aviationweather.gov/api/data/metar?ids=$icao&format=json"
      val tafUrl = "https://aviationweather.gov/api/data/taf?ids=$icao&format=json"
      logs.add("GET $metarUrl")

      val metarReq = Request.Builder()
        .url(metarUrl)
        .header("User-Agent", "MeteotabloAndroid/1.0")
        .build()

      var rawMetar: String? = null
      var rawTaf: String? = null

      client.newCall(metarReq).execute().use { resp ->
        logs.add("METAR HTTP ${resp.code}")
        if (resp.isSuccessful) {
          val body = resp.body?.string().orEmpty().trim()
          if (body.startsWith("[")) {
            val arr = JSONArray(body)
            if (arr.length() > 0) {
              val obj = arr.getJSONObject(0)
              rawMetar = obj.optString("rawOb").takeIf { it.isNotBlank() }
              val lat = obj.optDouble("lat", Double.NaN)
              val lon = obj.optDouble("lon", Double.NaN)
              val elev = obj.optInt("elev", Int.MIN_VALUE)
              val apiName = obj.optString("name")
              if (!lat.isNaN() && !lon.isNaN() && AirportCatalog.presets.none { it.icao == icao }) {
                resolvedStation = resolvedStation.copy(
                  latitude = lat,
                  longitude = lon,
                  elevationMeters = if (elev != Int.MIN_VALUE) elev else resolvedStation.elevationMeters,
                  name = apiName.ifBlank { resolvedStation.name },
                )
              }
            }
          }
        }
      }

      // Fallback to NOAA TGFTP raw station file if JSON returned empty
      if (rawMetar.isNullOrBlank()) {
        val noaaUrl = "https://tgftp.nws.noaa.gov/data/observations/metar/stations/$icao.TXT"
        logs.add("Fallback GET $noaaUrl")
        val noaaReq = Request.Builder()
          .url(noaaUrl)
          .header("User-Agent", "MeteotabloAndroid/1.0")
          .build()
        client.newCall(noaaReq).execute().use { resp ->
          logs.add("TGFTP HTTP ${resp.code}")
          if (resp.isSuccessful) {
            val lines = resp.body?.string().orEmpty()
              .lines()
              .map { it.trim() }
              .filter { it.isNotBlank() }
            rawMetar = lines.lastOrNull { it.contains(icao) } ?: lines.lastOrNull()
          }
        }
      }

      // Fetch TAF
      val tafReq = Request.Builder()
        .url(tafUrl)
        .header("User-Agent", "MeteotabloAndroid/1.0")
        .build()
      runCatching {
        client.newCall(tafReq).execute().use { resp ->
          logs.add("TAF HTTP ${resp.code}")
          if (resp.isSuccessful) {
            val body = resp.body?.string().orEmpty().trim()
            if (body.startsWith("[")) {
              val arr = JSONArray(body)
              if (arr.length() > 0) {
                rawTaf = arr.getJSONObject(0).optString("rawTAF").takeIf { it.isNotBlank() }
              }
            }
          }
        }
      }

      val finalMetar = rawMetar
      if (finalMetar.isNullOrBlank()) {
        val msg = customError ?: "Нет актуального METAR для $icao на сервере aviationweather.gov"
        return@withContext Result.failure(IllegalStateException(msg))
      }

      val finalTaf = rawTaf ?: "TAF $icao — нет данных"
      val decoded = AviationWeatherParser.parse(
        rawMetarInput = finalMetar,
        rawTafInput = finalTaf,
        station = resolvedStation,
        sourceLabel = "интернет (aviationweather.gov)",
      )
      logs.add("Успешно декодирован $icao (${decoded.observationTimeUtc})")
      Result.success(FetchResult(decoded, resolvedStation, logs))
    } catch (e: Exception) {
      val msg = customError ?: (e.message ?: e::class.simpleName ?: "Ошибка сети")
      logs.add("Ошибка сети: $msg")
      Result.failure(IllegalStateException(msg, e))
    }
  }
}
