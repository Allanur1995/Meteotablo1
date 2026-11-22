package com.example.model

enum class WindTrendDirection(
  val arrow: String,
  val label: String,
) {
  INCREASING("↑", "Усиление"),
  DECREASING("↓", "Ослабление"),
  STEADY("→", "Стабильно"),
  INSUFFICIENT("•", "Мало данных"),
}

data class WindHistoryEntry(
  val observationTimeUtc: String,
  val speedKt: Int,
  val gustKt: Int? = null,
  val directionDeg: Int? = null,
  val rawMetar: String = "",
  val recordedAtMillis: Long = System.currentTimeMillis(),
)

data class WindTrendSummary(
  val icao: String,
  val direction: WindTrendDirection,
  val deltaKt: Int,
  val currentSpeedKt: Int,
  val previousSpeedKt: Int?,
  val minSpeedKt: Int,
  val maxSpeedKt: Int,
  val entries: List<WindHistoryEntry>,
)
