package com.example.local

import com.example.data.CachedWeatherRecord
import com.example.data.WeatherCacheRepository
import com.example.model.AirportStation
import com.example.model.DecodedWeather

class RoomWeatherCacheRepository(
  private val dao: CachedWeatherDao,
) : WeatherCacheRepository {

  override suspend fun getCachedWeather(icao: String): CachedWeatherRecord? {
    val clean = icao.trim().uppercase()
    if (clean.length != 4) return null
    return dao.getByIcao(clean)?.toRecord()
  }

  override suspend fun getAllCachedWeather(): Map<String, CachedWeatherRecord> {
    return dao.getAll().associate { it.icao.uppercase() to it.toRecord() }
  }

  override suspend fun saveWeather(
    station: AirportStation,
    weather: DecodedWeather,
    cachedAtMillis: Long,
  ) {
    val clean = station.icao.trim().uppercase()
    if (clean.length != 4 || weather.rawMetar.isBlank()) return
    if (weather.observationTimeUtc == "--:--Z" ||
      weather.rawMetar.contains("нет данных", ignoreCase = true)
    ) {
      return
    }
    dao.upsert(CachedWeatherEntity.from(station, weather, cachedAtMillis))
  }
}
