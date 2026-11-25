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
      ceilingFeet = null,
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
    val mode = serverConfig.resolvedMode()
    logs.add("Режим источника: ${mode.displayName}")

    when (mode) {
      DataSourceMode.IP_ONLY -> {
        val (ipRes, ipErr) = fetchFromCustomIpServer(icao, serverConfig, station, logs)
        if (ipRes != null) {
          Result.success(ipRes)
        } else {
          Result.failure(IllegalStateException(ipErr ?: "Нет данных для $icao на IP-сервере"))
        }
      }

      DataSourceMode.INTERNET_ONLY -> {
        val (netRes, netErr) = fetchFromInternetSites(icao, serverConfig, station, logs)
        if (netRes != null) {
          Result.success(netRes)
        } else {
          Result.failure(IllegalStateException(netErr ?: "Нет данных для $icao на интернет-сайтах"))
        }
      }

      
DataSourceMode.AUTO_IP_FIRST -> {
  val ipAirports = setOf(
    "UTAA", "UTAM", "UTAK", "UTAT",
    "UTAV", "UTAN", "UTAE"
  )

  if (icao !in ipAirports) {
    logs.add("$icao: интернет-источник")
    val (netRes, netErr) =
      fetchFromInternetSites(icao, serverConfig, station, logs)

    if (netRes != null) {
      return@withContext Result.success(netRes)
    }

    return@withContext Result.failure(
      IllegalStateException(
        netErr ?: "Нет актуального METAR для $icao в интернете"
      )
    )
  }

  logs.add("$icao: приоритетный источник — IP-сервер")
  val (ipRes, ipErr) =
    fetchFromCustomIpServer(icao, serverConfig, station, logs)

  if (ipRes != null) {
    return@withContext Result.success(ipRes)
  }

  logs.add("IP-сервер недоступен -> переход на интернет")
  val (netRes, netErr) =
    fetchFromInternetSites(icao, serverConfig, station, logs)

  if (netRes != null) {
    return@withContext Result.success(netRes)
  }

  val msg = listOfNotNull(ipErr, netErr)
    .joinToString(" | ")
    .ifBlank { "Нет актуального METAR для $icao" }

  Result.failure(IllegalStateException(msg))
}

      DataSourceMode.AUTO_INTERNET_FIRST -> {
        val (netRes, netErr) = fetchFromInternetSites(icao, serverConfig, station, logs)
        if (netRes != null) return@withContext Result.success(netRes)

        logs.add("Интернет-сайты недоступны/пусты -> переход на IP-сервер…")
        val (ipRes, ipErr) = fetchFromCustomIpServer(icao, serverConfig, station, logs)
        if (ipRes != null) return@withContext Result.success(ipRes)

        val msg = listOfNotNull(netErr, ipErr).joinToString(" | ").i
          "Нет актуального METAR для $icao"
        }
        Result.failure(IllegalStateException(msg))
      }
    }
  }

  private suspend fun fetchFromCustomIpServer(
    icao: String,
    serverConfig: ServerConfig,
    station: AirportStation,
    logs: MutableList<String>,
  ): Pair<FetchResult?, String?> {
    if (serverConfig.url.isBlank()) {
      return null to "URL IP-сервера не задан"
    }
    val outcome = customServerClient.fetchMetarAndTaf(icao, serverConfig)
    logs.addAll(outcome.logs)
    val customMetar = outcome.rawMetar
    if (!customMetar.isNullOrBlank()) {
      val finalTaf = outcome.rawTaf ?: "TAF $icao — нет данных"
      val decoded = AviationWeatherParser.parse(
        rawMetarInput = customMetar,
        rawTafInput = finalTaf,
        station = station,
        sourceLabel = "сервер ${outcome.sourceHost}",
      )
      logs.add("Успешно декодирован $icao с ${outcome.sourceHost} (${decoded.observationTimeUtc})")
      return FetchResult(decoded, station, logs) to null
    }
    return null to (outcome.errorMessage ?: "Нет данных для $icao на сервере ${outcome.sourceHost}")
  }

  private fun fetchFromInternetSites(
    icao: String,
    serverConfig: ServerConfig,
    station: AirportStation,
    logs: MutableList<String>,
  ): Pair<FetchResult?, String?> {
    var resolvedStation = station
    var rawMetar: String? = null
    var rawTaf: String? = null
    var metarSourceSite = "интернет"

    try {
      // 1. aviationweather.gov JSON API
      if (serverConfig.useAviationWeatherGov) {
        val metarUrl = "https://aviationweather.gov/api/data/metar?ids=$icao&format=json"
        logs.add("GET $metarUrl")
        runCatching {
          val metarReq = Request.Builder()
            .url(metarUrl)
            .header("User-Agent", "Meteotablo/1.0")
            .build()
          client.newCall(metarReq).execute().use { resp ->
            logs.add("aviationweather.gov METAR HTTP ${resp.code}")
            if (resp.isSuccessful) {
              val body = resp.body?.string().orEmpty().trim()
              if (body.startsWith("[")) {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                  val obj = arr.getJSONObject(0)
                  rawMetar = obj.optString("rawOb").takeIf { it.isNotBlank() }
                  if (!rawMetar.isNullOrBlank()) {
                    metarSourceSite = "интернет (aviationweather.gov)"
                  }
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
        }.onFailure { err ->
          logs.add("aviationweather.gov ошибка: ${err.message ?: err::class.simpleName}")
        }
      }

      // 2. NOAA TGFTP raw station text file
      if (rawMetar.isNullOrBlank() && serverConfig.useNoaaTgftp) {
        val noaaUrl = "https://tgftp.nws.noaa.gov/data/observations/metar/stations/$icao.TXT"
        logs.add("GET $noaaUrl")
        runCatching {
          val noaaReq = Request.Builder()
            .url(noaaUrl)
            .header("User-Agent", "Meteotablo/1.0")
            .build()
          client.newCall(noaaReq).execute().use { resp ->
            logs.add("tgftp.nws.noaa.gov METAR HTTP ${resp.code}")
            if (resp.isSuccessful) {
              val lines = resp.body?.string().orEmpty()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
              val found = lines.lastOrNull { it.contains(icao) } ?: lines.lastOrNull()
              if (!found.isNullOrBlank()) {
                rawMetar = found
                metarSourceSite = "интернет (noaa.gov)"
              }
            }
          }
        }.onFailure { err ->
          logs.add("tgftp.nws.noaa.gov ошибка: ${err.message ?: err::class.simpleName}")
        }
      }

      // 3. VATSIM global METAR mirror
      if (rawMetar.isNullOrBlank() && serverConfig.useVatsimMetar) {
        val vatsimUrl = "https://metar.vatsim.net/metar.php?id=$icao"
        logs.add("GET $vatsimUrl")
        runCatching {
          val vatsimReq = Request.Builder()
            .url(vatsimUrl)
            .header("User-Agent", "Meteotablo/1.0")
            .build()
          client.newCall(vatsimReq).execute().use { resp ->
            logs.add("metar.vatsim.net HTTP ${resp.code}")
            if (resp.isSuccessful) {
              val text = resp.body?.string().orEmpty()
                .lines()
                .map { it.trim() }
                .firstOrNull { it.contains(icao, ignoreCase = true) }
              if (!text.isNullOrBlank()) {
                rawMetar = text
                metarSourceSite = "интернет (vatsim.net)"
              }
            }
          }
        }.onFailure { err ->
          logs.add("metar.vatsim.net ошибка: ${err.message ?: err::class.simpleName}")
        }
      }

      // TAF lookup from internet sites
      if (serverConfig.useAviationWeatherGov) {
        val tafUrl = "https://aviationweather.gov/api/data/taf?ids=$icao&format=json"
        runCatching {
          val tafReq = Request.Builder()
            .url(tafUrl)
            .header("User-Agent", "Meteotablo/1.0")
            .build()
          client.newCall(tafReq).execute().use { resp ->
            logs.add("aviationweather.gov TAF HTTP ${resp.code}")
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
      }
      if (rawTaf.isNullOrBlank() && serverConfig.useNoaaTgftp) {
        val noaaTafUrl = "https://tgftp.nws.noaa.gov/data/forecasts/taf/stations/$icao.TXT"
        runCatching {
          val tafReq = Request.Builder()
            .url(noaaTafUrl)
            .header("User-Agent", "Meteotablo/1.0")
            .build()
          client.newCall(tafReq).execute().use { resp ->
            if (resp.isSuccessful) {
              val lines = resp.body?.string().orEmpty()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.matches(Regex("\\d{4}/\\d{2}/\\d{2}.*")) }
              if (lines.isNotEmpty()) {
                rawTaf = lines.joinToString(" ")
              }
            }
          }
        }
      }

      val finalMetar = rawMetar
      if (finalMetar.isNullOrBlank()) {
        return null to "Нет актуального METAR для $icao на интернет-сайтах"
      }

      val finalTaf = rawTaf ?: "TAF $icao — нет данных"
      val decoded = AviationWeatherParser.parse(
        rawMetarInput = finalMetar,
        rawTafInput = finalTaf,
        station = resolvedStation,
        sourceLabel = metarSourceSite,
      )
      logs.add("Успешно декодирован $icao с $metarSourceSite (${decoded.observationTimeUtc})")
      return FetchResult(decoded, resolvedStation, logs) to null
    } catch (e: Exception) {
      val msg = e.message ?: e::class.simpleName ?: "Ошибка интернет-источников"
      logs.add("Ошибка интернет-источников: $msg")
      return null to msg
    }
  }

  data class HistoryFetchResult(
    val entries: List<com.example.model.WeatherHistoryEntry>,
    val logMessages: List<String>,
  )

  suspend fun fetch24hHistory(
    station: AirportStation,
    serverConfig: ServerConfig = ServerConfig(),
  ): Result<HistoryFetchResult> = withContext(Dispatchers.IO) {
    val logs = mutableListOf<String>()
    val icao = station.icao.trim().uppercase()
    val mode = serverConfig.resolvedMode()
    logs.add("Загрузка 24ч истории ($icao) · режим ${mode.displayName}")

    val entries = when (mode) {
      DataSourceMode.IP_ONLY -> fetch24hFromCustomIpServer(icao, serverConfig, station, logs)
      DataSourceMode.INTERNET_ONLY -> fetch24hFromInternet(icao, serverConfig, station, logs)
      DataSourceMode.AUTO_IP_FIRST -> {
        val ipList = fetch24hFromCustomIpServer(icao, serverConfig, station, logs)
        ipList.ifEmpty { fetch24hFromInternet(icao, serverConfig, station, logs) }
      }
      DataSourceMode.AUTO_INTERNET_FIRST -> {
        val netList = fetch24hFromInternet(icao, serverConfig, station, logs)
        netList.ifEmpty { fetch24hFromCustomIpServer(icao, serverConfig, station, logs) }
      }
    }

    if (entries.isEmpty()) {
      Result.failure(IllegalStateException("Архив 24ч для $icao пока пуст или сервер не ответил"))
    } else {
      val merged = com.example.domain.WeatherHistoryAnalyzer.mergeHistory(emptyList(), entries)
      Result.success(HistoryFetchResult(merged, logs))
    }
  }

  private suspend fun fetch24hFromCustomIpServer(
    icao: String,
    serverConfig: ServerConfig,
    station: AirportStation,
    logs: MutableList<String>,
  ): List<com.example.model.WeatherHistoryEntry> {
    if (serverConfig.url.isBlank()) return emptyList()
    val outcome = customServerClient.fetchHistory24h(icao, serverConfig)
    logs.addAll(outcome.logs)
    return outcome.observations.mapNotNull { obs ->
      com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
        rawMetar = obs.rawMetar,
        rawTaf = obs.rawTaf,
        station = station,
        sourceLabel = "сервер ${outcome.sourceHost}",
        explicitTimestampMillis = obs.obsTimeMillis,
      )
    }
  }

  private fun fetch24hFromInternet(
    icao: String,
    serverConfig: ServerConfig,
    station: AirportStation,
    logs: MutableList<String>,
  ): List<com.example.model.WeatherHistoryEntry> {
    if (!serverConfig.useAviationWeatherGov) return emptyList()

    val tafList = mutableListOf<Pair<Long, String>>()
    runCatching {
      val tafUrl = "https://aviationweather.gov/api/data/taf?ids=$icao&format=json&hours=24"
      logs.add("GET $tafUrl")
      val req = Request.Builder().url(tafUrl).header("User-Agent", "Meteotablo/1.0").build()
      client.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          val body = resp.body?.string().orEmpty().trim()
          if (body.startsWith("[")) {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              val rawTaf = obj.optString("rawTAF").takeIf { it.isNotBlank() } ?: continue
              val issueSec = obj.optLong("issueTime", 0L)
              val issueMs = if (issueSec > 0L) issueSec * 1000L else System.currentTimeMillis()
              tafList.add(issueMs to rawTaf)
            }
          }
        }
      }
    }

    val metarEntries = mutableListOf<com.example.model.WeatherHistoryEntry>()
    runCatching {
      val metarUrl = "https://aviationweather.gov/api/data/metar?ids=$icao&format=json&hours=24"
      logs.add("GET $metarUrl")
      val req = Request.Builder().url(metarUrl).header("User-Agent", "Meteotablo/1.0").build()
      client.newCall(req).execute().use { resp ->
        logs.add("aviationweather.gov 24h METAR HTTP ${resp.code}")
        if (resp.isSuccessful) {
          val body = resp.body?.string().orEmpty().trim()
          if (body.startsWith("[")) {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              val rawOb = obj.optString("rawOb").takeIf { it.isNotBlank() } ?: continue
              val obsSec = obj.optLong("obsTime", 0L)
              val obsMs = if (obsSec > 0L) obsSec * 1000L else null
              val matchedTaf = if (obsMs != null && tafList.isNotEmpty()) {
                tafList.filter { it.first <= obsMs + 3600_000L }
                  .maxByOrNull { it.first }?.second ?: tafList.firstOrNull()?.second
              } else {
                tafList.firstOrNull()?.second
              }
              com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
                rawMetar = rawOb,
                rawTaf = matchedTaf,
                station = station,
                sourceLabel = "aviationweather.gov (24ч)",
                explicitTimestampMillis = obsMs,
              )?.let(metarEntries::add)
            }
          }
        }
      }
    }.onFailure { err ->
      logs.add("aviationweather.gov 24ч ошибка: ${err.message ?: err::class.simpleName}")
    }

    return metarEntries
  }
}
