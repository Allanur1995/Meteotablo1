package com.example.data

import com.example.domain.AviationWeatherParser
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.RunwayPair
import java.util.concurrent.ConcurrentHashMap

data class CachedWeatherRecord(
  val station: AirportStation,
  val rawMetar: String,
  val rawTaf: String,
  val sourceLabel: String,
  val observationTimeUtc: String,
  val cachedAtMillis: Long,
) {
  fun toDecodedWeather(isOfflineFallback: Boolean): DecodedWeather {
    val label = if (isOfflineFallback) {
      "кэш (${observationTimeUtc}, оффлайн)"
    } else {
      sourceLabel
    }
    return AviationWeatherParser.parse(
      rawMetarInput = rawMetar,
      rawTafInput = rawTaf,
      station = station,
      sourceLabel = label,
    )
  }
}

interface WeatherCacheRepository {
  suspend fun getCachedWeather(icao: String): CachedWeatherRecord?
  suspend fun getAllCachedWeather(): Map<String, CachedWeatherRecord>
  suspend fun saveWeather(
    station: AirportStation,
    weather: DecodedWeather,
    cachedAtMillis: Long = System.currentTimeMillis(),
  )
}

class InMemoryWeatherCacheRepository : WeatherCacheRepository {
  private val cache = ConcurrentHashMap<String, CachedWeatherRecord>()

  override suspend fun getCachedWeather(icao: String): CachedWeatherRecord? {
    return cache[icao.trim().uppercase()]
  }

  override suspend fun getAllCachedWeather(): Map<String, CachedWeatherRecord> {
    return cache.toMap()
  }

  override suspend fun saveWeather(
    station: AirportStation,
    weather: DecodedWeather,
    cachedAtMillis: Long,
  ) {
    val cleanIcao = station.icao.trim().uppercase()
    if (cleanIcao.length != 4 || weather.rawMetar.isBlank()) return
    cache[cleanIcao] = CachedWeatherRecord(
      station = station.copy(icao = cleanIcao),
      rawMetar = weather.rawMetar,
      rawTaf = weather.rawTaf,
      sourceLabel = weather.sourceLabel,
      observationTimeUtc = weather.observationTimeUtc,
      cachedAtMillis = cachedAtMillis,
    )
  }
}

internal object RunwayPairSerializer {
  fun serialize(runways: List<RunwayPair>): String {
    return runways.joinToString(";") { pair ->
      "${pair.primaryDesignator}|${pair.primaryHeadingDeg}|${pair.reciprocalDesignator}|${pair.reciprocalHeadingDeg}"
    }
  }

  fun deserialize(raw: String, fallbackIcao: String): List<RunwayPair> {
    val parsed = raw.split(";").mapNotNull { chunk ->
      val parts = chunk.split("|")
      if (parts.size != 4) return@mapNotNull null
      val pHdg = parts[1].toIntOrNull() ?: return@mapNotNull null
      val rHdg = parts[3].toIntOrNull() ?: return@mapNotNull null
      if (parts[0].isBlank() || parts[2].isBlank()) return@mapNotNull null
      RunwayPair(parts[0], pHdg, parts[2], rHdg)
    }
    return parsed.ifEmpty { AirportCatalog.findOrDefault(fallbackIcao).runways }
  }
}
