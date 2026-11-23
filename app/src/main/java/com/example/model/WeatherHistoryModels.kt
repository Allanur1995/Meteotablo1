package com.example.model

data class WeatherHistoryEntry(
  val icao: String,
  val observationTimeUtc: String,
  val observedAtMillis: Long,
  val windSpeedKt: Int,
  val gustKt: Int? = null,
  val windDirectionDeg: Int? = null,
  val visibilityMeters: Int? = null,
  val isCavok: Boolean = false,
  val ceilingFeet: Int? = null,
  val temperatureC: Int? = null,
  val dewPointC: Int? = null,
  val qnhHpa: Int? = null,
  val qnhInHg: Double? = null,
  val flightCategoryCode: String = "---",
  val rawMetar: String,
  val rawTaf: String = "",
  val sourceLabel: String = "",
)

data class QnhHistoryPoint(
  val observationTimeUtc: String,
  val observedAtMillis: Long,
  val qnhHpa: Int,
  val qnhInHg: Double,
  val rawMetar: String = "",
)

data class QnhTrendSummary(
  val icao: String,
  val points: List<QnhHistoryPoint>,
  val minHpa: Int?,
  val maxHpa: Int?,
  val latestHpa: Int?,
  val delta24hHpa: Int?,
)
