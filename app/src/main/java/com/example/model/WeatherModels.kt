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

data class LogEntry(
  val timestamp: String,
  val level: String,
  val message: String,
)
