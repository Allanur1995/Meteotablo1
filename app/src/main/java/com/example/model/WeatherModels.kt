package com.example.model

data class RunwayPair(
  val primaryDesignator: String,
  val primaryHeadingDeg: Int,
  val reciprocalDesignator: String,
  val reciprocalHeadingDeg: Int,
)

data class AirportStation(
  val icao: String,
  val name: String,
  val city: String,
  val latitude: Double,
  val longitude: Double,
  val elevationMeters: Int,
  val utcOffsetHours: Int,
  val timezoneLabel: String,
  val runways: List<RunwayPair>,
)

data class WindData(
  val directionDeg: Int?,
  val isVariable: Boolean,
  val isCalm: Boolean,
  val variableFromDeg: Int? = null,
  val variableToDeg: Int? = null,
  val speedKt: Int,
  val gustKt: Int? = null,
  val speedMps: Double,
  val gustMps: Double? = null,
)

data class RunwayCalculation(
  val designator: String,
  val headingDeg: Int,
  val crosswindMps: Double,
  val headwindMps: Double,
  val crosswindKt: Double,
  val headwindKt: Double,
  val frictionCoeff: Double?,
  val rvrMeters: Int?,
)

data class CloudLayer(
  val cover: String,
  val baseFeet: Int,
  val baseMeters: Int,
  val rawToken: String,
)

data class DecodedWeather(
  val stationIcao: String,
  val observationTimeUtc: String,
  val localObsTime: String,
  val receivedTimeLocal: String,
  val wind: WindData,
  val visibilityMeters: Int?,
  val isCavok: Boolean,
  val rvrPrimaryMeters: Int?,
  val rvrReciprocalMeters: Int?,
  val weatherPhenomena: String,
  val clouds: List<CloudLayer>,
  val ceilingMeters: Int?,
  val ceilingFeet: Int? = null,
  val ceilingCode: String?,
  val temperatureC: Int?,
  val dewPointC: Int?,
  val qnhHpa: Int?,
  val qnhMmHg: Int?,
  val qnhInHg: Double?,
  val qfeHpa: Int?,
  val qfeMmHg: Int?,
  val qfeInHg: Double?,
  val frictionCoeff: Double?,
  val specialConditions: String,
  val rawMetar: String,
  val rawTaf: String,
  val sourceLabel: String,
  val sunriseLocal: String,
  val sunsetLocal: String,
)

enum class WindSpeedUnit(val label: String) {
  KT("kt"),
  MPS("м/с"),
  KPH("км/ч"),
}

enum class PressureUnit(val label: String) {
  HPA("гПа"),
  INHG("дм"),
}

enum class UnitSystem(
  val displayName: String,
  val shortTag: String,
  val windUnit: WindSpeedUnit,
  val pressureUnit: PressureUnit,
) {
  METRIC(
    displayName = "Метрическая (hPa, м/с)",
    shortTag = "METRIC · hPa / м/с",
    windUnit = WindSpeedUnit.MPS,
    pressureUnit = PressureUnit.HPA,
  ),
  IMPERIAL(
    displayName = "Имперская (inHg, kt)",
    shortTag = "IMPERIAL · inHg / kt",
    windUnit = WindSpeedUnit.KT,
    pressureUnit = PressureUnit.INHG,
  ),
}

data class LogEntry(
  val timestamp: String,
  val level: String,
  val message: String,
)

enum class TafChangeType(
  val code: String,
  val label: String,
) {
  BASE("BASE", "Основной прогноз"),
  FM("FM", "С времени (FM)"),
  BECMG("BECMG", "Переход (BECMG)"),
  TEMPO("TEMPO", "Временно (TEMPO)"),
  PROB("PROB", "Вероятность (PROB)"),
}

data class TafForecastPeriod(
  val type: TafChangeType,
  val badgeLabel: String,
  val validityLabel: String,
  val windSummary: String,
  val visibilitySummary: String,
  val cloudsSummary: String,
  val phenomenaSummary: String,
  val isHazardous: Boolean,
  val rawSegment: String,
  val windSpeedKt: Int? = null,
  val gustKt: Int? = null,
  val visibilityMeters: Int? = null,
  val isWindAlert: Boolean = false,
  val isVisibilityAlert: Boolean = false,
)

data class DecodedTaf(
  val stationIcao: String,
  val issuedUtc: String,
  val validityWindow: String,
  val isCancelled: Boolean,
  val isNil: Boolean,
  val periods: List<TafForecastPeriod>,
  val rawTaf: String,
)

