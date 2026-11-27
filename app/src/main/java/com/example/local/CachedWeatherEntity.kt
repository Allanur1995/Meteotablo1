package com.example.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.CachedWeatherRecord
import com.example.data.RunwayPairSerializer
import com.example.model.AirportStation
import com.example.model.DecodedWeather

@Entity(tableName = "cached_weather")
data class CachedWeatherEntity(
  @PrimaryKey
  @ColumnInfo(name = "icao")
  val icao: String,
  @ColumnInfo(name = "station_name")
  val stationName: String,
  @ColumnInfo(name = "station_city")
  val stationCity: String,
  @ColumnInfo(name = "latitude")
  val latitude: Double,
  @ColumnInfo(name = "longitude")
  val longitude: Double,
  @ColumnInfo(name = "elevation_meters")
  val elevationMeters: Int,
  @ColumnInfo(name = "utc_offset_hours")
  val utcOffsetHours: Int,
  @ColumnInfo(name = "timezone_label")
  val timezoneLabel: String,
  @ColumnInfo(name = "runways_serialized")
  val runwaysSerialized: String,
  @ColumnInfo(name = "raw_metar")
  val rawMetar: String,
  @ColumnInfo(name = "raw_taf")
  val rawTaf: String,
  @ColumnInfo(name = "source_label")
  val sourceLabel: String,
  @ColumnInfo(name = "observation_time_utc")
  val observationTimeUtc: String,
  @ColumnInfo(name = "cached_at_millis")
  val cachedAtMillis: Long,
) {
  fun toRecord(): CachedWeatherRecord {
    val cleanIcao = icao.trim().uppercase()
    return CachedWeatherRecord(
      station = AirportStation(
        icao = cleanIcao,
        name = stationName,
        city = stationCity,
        latitude = latitude,
        longitude = longitude,
        elevationMeters = elevationMeters,
        utcOffsetHours = utcOffsetHours,
        timezoneLabel = timezoneLabel,
        runways = RunwayPairSerializer.deserialize(runwaysSerialized, cleanIcao),
      ),
      rawMetar = rawMetar,
      rawTaf = rawTaf,
      sourceLabel = sourceLabel,
      observationTimeUtc = observationTimeUtc,
      cachedAtMillis = cachedAtMillis,
    )
  }

  companion object {
    fun from(
      station: AirportStation,
      weather: DecodedWeather,
      cachedAtMillis: Long,
    ): CachedWeatherEntity {
      val cleanIcao = station.icao.trim().uppercase()
      return CachedWeatherEntity(
        icao = cleanIcao,
        stationName = station.name,
        stationCity = station.city,
        latitude = station.latitude,
        longitude = station.longitude,
        elevationMeters = station.elevationMeters,
        utcOffsetHours = station.utcOffsetHours,
        timezoneLabel = station.timezoneLabel,
        runwaysSerialized = RunwayPairSerializer.serialize(station.runways),
        rawMetar = weather.rawMetar,
        rawTaf = weather.rawTaf,
        sourceLabel = weather.sourceLabel,
        observationTimeUtc = weather.observationTimeUtc,
        cachedAtMillis = cachedAtMillis,
      )
    }
  }
}
