package com.example.data

import com.example.domain.AviationWeatherParser
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.network.AviationWeatherNetworkManager
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AviationWeatherRepository(
  private val publicNetworkManager: AviationWeatherNetworkManager = AviationWeatherNetworkManager(),
  private val customServerClient: CustomMeteoServerClient = CustomMeteoServerClient(),
) {

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
      utcOffsetHours = 0,
      calendar = cal,
      appendZuluSuffix = true,
    )
    return DecodedWeather(
      stationIcao = station.icao,
      observationTimeUtc = "--:--Z",
      localObsTime = "--:--",
      receivedTimeLocal = AviationWeatherParser.currentUtcTimeString(),
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
    val hasIpTaf = ipRes.weather.rawTaf.isNotBlank() &&
      !ipRes.weather.rawTaf.contains("нет данных", ignoreCase = true)
    if (!hasIpTaf && (serverConfig.useAviationWeatherGov || serverConfig.useNoaaTgftp)) {
      val fallbackTaf = runCatching {
        publicNetworkManager.fetchLatestTafOnly(
          icao = icao,
          useAviationWeatherGov = serverConfig.useAviationWeatherGov,
          useNoaaTgftp = serverConfig.useNoaaTgftp,
          logs = logs,
        )
      }.getOrNull()
      if (!fallbackTaf.isNullOrBlank()) {
        val cleanFallbackTaf = fallbackTaf.trim().replace(Regex("\\s+"), " ")
          .replace(Regex("^TAF\\s+TAF\\b", RegexOption.IGNORE_CASE), "TAF")
        logs.add("IP вернул METAR без TAF -> TAF $icao дополнен из интернета")
        return@withContext Result.success(
          ipRes.copy(
            weather = ipRes.weather.copy(
              rawTaf = cleanFallbackTaf,
              sourceLabel = "${ipRes.weather.sourceLabel} + TAF инет",
            ),
            logMessages = logs,
          ),
        )
      }
    }
    return@withContext Result.success(ipRes)
  }

  logs.add("IP-сервер (${ipErr ?: "недоступен"}) -> переход на интернет")
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

        val msg = listOfNotNull(netErr, ipErr).joinToString(" | ").ifBlank {
          "Нет актуального METAR для $icao"
        }
        Result.failure(IllegalStateException(msg))
      }
    }
  }

  suspend fun fetchWeatherByCoordinates(
    latitude: Double,
    longitude: Double,
    locationLabel: String? = null,
    serverConfig: ServerConfig = ServerConfig(),
  ): Result<FetchResult> = withContext(Dispatchers.IO) {
    val logs = mutableListOf<String>()
    val clampedLat = latitude.coerceIn(-89.9, 89.9)
    val clampedLon = longitude.coerceIn(-180.0, 180.0)
    val cleanLabel = locationLabel?.trim()?.takeIf { it.isNotBlank() }
    logs.add(
      "Поиск погоды по координатам: ${
        String.format(java.util.Locale.US, "%.4f, %.4f", clampedLat, clampedLon)
      }${cleanLabel?.let { " ($it)" } ?: ""}",
    )

    val nearestCatalog = AirportCatalog.allStations.minByOrNull { st ->
      com.example.domain.AirportMapProjection.haversineKm(clampedLat, clampedLon, st.latitude, st.longitude)
    } ?: AirportCatalog.presets.first()
    val nearestDistKm = com.example.domain.AirportMapProjection.haversineKm(
      clampedLat,
      clampedLon,
      nearestCatalog.latitude,
      nearestCatalog.longitude,
    )

    val ipAirports = setOf("UTAA", "UTAM", "UTAK", "UTAT", "UTAV", "UTAN", "UTAE")
    if (nearestCatalog.icao in ipAirports &&
      nearestDistKm <= 120.0 &&
      serverConfig.resolvedMode() != DataSourceMode.INTERNET_ONLY
    ) {
      logs.add("Ближайший аэродром ТМ ${nearestCatalog.icao} (${nearestDistKm} км) -> запрос через IP/авто")
      val live = fetchLiveWeather(nearestCatalog, serverConfig)
      if (live.isSuccess) {
        val res = live.getOrThrow()
        val displayStation = if (nearestDistKm > 15.0 && cleanLabel != null) {
          res.resolvedStation.copy(
            city = "$cleanLabel (~${nearestDistKm} км до ${res.resolvedStation.icao})",
          )
        } else {
          res.resolvedStation
        }
        return@withContext Result.success(
          res.copy(
            resolvedStation = displayStation,
            logMessages = logs + res.logMessages,
          ),
        )
      }
    }

    // Query public Retrofit API (aviationweather.gov bounding box) for nearest reporting station to exact coordinates
    val awcCandidate = publicNetworkManager.fetchNearestByCoordinates(
      latitude = clampedLat,
      longitude = clampedLon,
      useAviationWeatherGov = serverConfig.useAviationWeatherGov,
      logs = logs,
    )
    if (awcCandidate != null) {
      val known = AirportCatalog.findKnown(awcCandidate.icao)
      val approxUtcOffset = kotlin.math.round(clampedLon / 15.0).toInt().coerceIn(-12, 14)
      val cityLabel = cleanLabel?.let { "$it (бл. ${awcCandidate.icao} · ${awcCandidate.distanceKm.toInt()} км)" }
        ?: known?.city
        ?: String.format(java.util.Locale.US, "%.2f°, %.2f°", clampedLat, clampedLon)
      val matchedStation = known?.copy(city = cityLabel) ?: AirportStation(
        icao = awcCandidate.icao,
        name = awcCandidate.name ?: "${awcCandidate.icao} Airfield",
        city = cityLabel,
        latitude = awcCandidate.stationLat ?: clampedLat,
        longitude = awcCandidate.stationLon ?: clampedLon,
        elevationMeters = awcCandidate.elevationMeters ?: 50,
        utcOffsetHours = approxUtcOffset,
        timezoneLabel = if (approxUtcOffset >= 0) "UTC+$approxUtcOffset" else "UTC$approxUtcOffset",
        runways = listOf(com.example.model.RunwayPair("36", 360, "18", 180)),
      )
      val decoded = AviationWeatherParser.parse(
        rawMetarInput = awcCandidate.rawMetar,
        rawTafInput = awcCandidate.rawTaf ?: "TAF ${matchedStation.icao} — нет данных",
        station = matchedStation,
        sourceLabel = "GPS/координаты (AWC ${matchedStation.icao})",
      )
      return@withContext Result.success(FetchResult(decoded, matchedStation, logs))
    }

    // Fallback to nearest catalog airport
    logs.add("Нет станций в радиусе AWC -> ближайший каталог ${nearestCatalog.icao} ($nearestDistKm км)")
    val fallbackRes = fetchLiveWeather(nearestCatalog, serverConfig)
    if (fallbackRes.isSuccess) {
      val res = fallbackRes.getOrThrow()
      val updatedStation = if (cleanLabel != null) {
        res.resolvedStation.copy(city = "$cleanLabel (бл. ${res.resolvedStation.icao})")
      } else {
        res.resolvedStation
      }
      return@withContext Result.success(res.copy(resolvedStation = updatedStation, logMessages = logs + res.logMessages))
    }

    // Last resort: still switch to coordinate-aware nearest station placeholder so user sees coordinates & sun times
    val approxOffset = kotlin.math.round(clampedLon / 15.0).toInt().coerceIn(-12, 14)
    val fallbackCoordsStation = nearestCatalog.copy(
      city = cleanLabel ?: String.format(java.util.Locale.US, "%.3f°, %.3f°", clampedLat, clampedLon),
      latitude = clampedLat,
      longitude = clampedLon,
      utcOffsetHours = approxOffset,
      timezoneLabel = if (approxOffset >= 0) "UTC+$approxOffset" else "UTC$approxOffset",
    )
    Result.failure(
      IllegalStateException(
        "Не удалось загрузить METAR для ${fallbackCoordsStation.city} (ближайший ${nearestCatalog.icao}, $nearestDistKm км)",
      ),
    )
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

  private suspend fun fetchFromInternetSites(
    icao: String,
    serverConfig: ServerConfig,
    station: AirportStation,
    logs: MutableList<String>,
  ): Pair<FetchResult?, String?> {
    return try {
      val response = publicNetworkManager.fetchLatestWeather(
        icao = icao,
        useAviationWeatherGov = serverConfig.useAviationWeatherGov,
        useNoaaTgftp = serverConfig.useNoaaTgftp,
        useVatsimMetar = serverConfig.useVatsimMetar,
        logs = logs,
      ) ?: return null to "Нет актуального METAR для $icao на интернет-сайтах"

      var resolvedStation = station
      if (response.resolvedLat != null &&
        response.resolvedLon != null &&
        AirportCatalog.presets.none { it.icao == icao }
      ) {
        resolvedStation = resolvedStation.copy(
          latitude = response.resolvedLat,
          longitude = response.resolvedLon,
          elevationMeters = response.resolvedElevMeters ?: resolvedStation.elevationMeters,
          name = response.resolvedName ?: resolvedStation.name,
        )
      }

      val finalTaf = response.rawTaf ?: "TAF $icao — нет данных"
      val decoded = AviationWeatherParser.parse(
        rawMetarInput = response.rawMetar,
        rawTafInput = finalTaf,
        station = resolvedStation,
        sourceLabel = response.sourceLabel,
      )
      logs.add("Успешно декодирован $icao с ${response.sourceLabel} (${decoded.observationTimeUtc})")
      FetchResult(decoded, resolvedStation, logs) to null
    } catch (e: Exception) {
      val msg = e.message ?: e::class.simpleName ?: "Ошибка интернет-источников"
      logs.add("Ошибка интернет-источников: $msg")
      null to msg
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
        val ipAirports = setOf("UTAA", "UTAM", "UTAK", "UTAT", "UTAV", "UTAN", "UTAE")
        if (icao in ipAirports) {
          val ipList = fetch24hFromCustomIpServer(icao, serverConfig, station, logs)
          ipList.ifEmpty { fetch24hFromInternet(icao, serverConfig, station, logs) }
        } else {
          fetch24hFromInternet(icao, serverConfig, station, logs)
        }
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

  private suspend fun fetch24hFromInternet(
    icao: String,
    serverConfig: ServerConfig,
    station: AirportStation,
    logs: MutableList<String>,
  ): List<com.example.model.WeatherHistoryEntry> {
    if (!serverConfig.useAviationWeatherGov) return emptyList()
    val histData = publicNetworkManager.fetch24hHistory(
      icao = icao,
      useAviationWeatherGov = true,
      logs = logs,
    )
    val tafList = histData.tafs.mapNotNull { dto ->
      val raw = dto.rawTaf?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val issueMs = (dto.issueTime ?: 0L).let { if (it > 0L) it * 1000L else System.currentTimeMillis() }
      issueMs to raw
    }
    return histData.metars.mapNotNull { dto ->
      val rawOb = dto.rawOb?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val obsMs = dto.obsTime?.takeIf { it > 0L }?.let { it * 1000L }
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
      )
    }
  }
}
