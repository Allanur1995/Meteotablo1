package com.example.model

enum class WeatherAlertSeverity(val label: String) {
  INFO("Инфо"),
  WARNING("Внимание"),
  CRITICAL("Критично"),
}

enum class AircraftTypePreset(
  val id: String,
  val displayName: String,
  val defaultMaxCrosswindKt: Int,
) {
  CESSNA_172("CESSNA_172", "Cessna 172", 15),
  AN_2("AN_2", "Ан-2", 16),
  PIPER_PA28("PIPER_PA28", "Piper PA-28", 17),
  BEECH_KING_AIR("BEECH_KING_AIR", "King Air 350", 25),
  ATR_72("ATR_72", "ATR 72 / Ан-24", 35),
  B737("B737", "B737", 35),
  B738("B738", "B738", 36),
  B773("B773", "B773", 38),
  A320_B737("A320_B737", "A320 / B737", 38),
  B777_WIDE("B777_WIDE", "B777 / A330", 40),
  CUSTOM("CUSTOM", "Свой тип ВС", 20);

  companion object {
    fun fromId(id: String?): AircraftTypePreset =
      entries.firstOrNull { it.id == id } ?: ATR_72
  }
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
  val crosswindWarningEnabled: Boolean = true,
  val aircraftTypeId: String = AircraftTypePreset.ATR_72.id,
  val customAircraftName: String = "Мой самолёт",
  val maxCrosswindKt: Int = AircraftTypePreset.ATR_72.defaultMaxCrosswindKt,
) {
  fun resolvedVfrMonitoredIcaos(favoriteIcaos: List<String>): List<String> {
    val source = if (vfrMonitorAllFavorites) favoriteIcaos else vfrMonitoredIcaos
    return source
      .map { it.trim().uppercase() }
      .filter { it.length == 4 && it.all(Char::isLetter) }
      .distinct()
  }

  fun resolvedAircraftName(): String {
    return if (aircraftTypeId == AircraftTypePreset.CUSTOM.id) {
      customAircraftName.trim().ifBlank { AircraftTypePreset.CUSTOM.displayName }
    } else {
      AircraftTypePreset.fromId(aircraftTypeId).displayName
    }
  }

  fun isCrosswindExceeded(crosswindKt: Double, gustCrosswindKt: Double? = null): Boolean {
    if (!crosswindWarningEnabled) return false
    val limit = maxCrosswindKt.toDouble() + 0.05
    return crosswindKt > limit || (gustCrosswindKt != null && gustCrosswindKt > limit)
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
