package com.example.model

enum class WeatherAlertSeverity(val label: String) {
  INFO("Инфо"),
  WARNING("Внимание"),
  CRITICAL("Критично"),
}

data class WeatherAlertConfig(
  val enabled: Boolean = true,
  val windChangeThresholdKt: Int = 8,
  val visibilityThresholdMeters: Int = 3000,
  val ceilingThresholdMeters: Int = 300,
  val checkIntervalMinutes: Int = 15,
  val tableAlertsEnabled: Boolean = true,
  val tableWindAlertKt: Int = 20,
  val tableVisibilityAlertMeters: Int = 3000,
  val vfrAlertsEnabled: Boolean = true,
  val vfrMinVisibilityMeters: Int = 5000,
  val vfrMinCeilingFeet: Int = 1500,
  val vfrMaxWindKt: Int = 25,
  val vfrMonitorAllFavorites: Boolean = true,
  val vfrMonitoredIcaos: List<String> = emptyList(),
) {
  fun resolvedVfrMonitoredIcaos(favoriteIcaos: List<String>): List<String> {
    val source = if (vfrMonitorAllFavorites) favoriteIcaos else vfrMonitoredIcaos
    return source
      .map { it.trim().uppercase() }
      .filter { it.length == 4 && it.all(Char::isLetter) }
      .distinct()
  }
}

data class FavoriteWeatherSnapshot(
  val icao: String,
  val observationTimeUtc: String,
  val windSpeedKt: Int,
  val gustKt: Int?,
  val windDirectionDeg: Int?,
  val visibilityMeters: Int?,
  val ceilingMeters: Int?,
  val weatherPhenomena: String,
  val qnhHpa: Int?,
  val rawMetar: String,
  val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class WeatherChangeAlert(
  val id: String,
  val icao: String,
  val airportName: String,
  val severity: WeatherAlertSeverity,
  val title: String,
  val summary: String,
  val reasons: List<String>,
  val observationTimeUtc: String,
  val rawMetar: String,
  val timestampLocal: String,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val isRead: Boolean = false,
)
